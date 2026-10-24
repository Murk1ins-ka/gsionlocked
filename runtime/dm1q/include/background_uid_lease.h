// Temporary allowances for installed guest app UIDs in the host cgroup.
// BACKGROUND_MATCH: AOSP Connectivity/bpf_progs/netd.h, bit 12.
// Host STANDBY_MATCH (bit 3) must not block an unrelated guest app sharing its UID.
// Preserve global configuration and snapshot/restore every leased UID entry.
#ifndef TEMPGSI_BACKGROUND_UID_LEASE_H
#define TEMPGSI_BACKGROUND_UID_LEASE_H
#include <linux/bpf.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <stdlib.h>
struct bg_uid_value {uint32_t iif,rule;};
struct bg_uid_lease {int fd,applied,existed;uint32_t uid;struct bg_uid_value before,leased;};
static int bg_uid_lookup(int fd,uint32_t uid,struct bg_uid_value *value){
    union bpf_attr attr={0};attr.map_fd=fd;attr.key=(uint64_t)(uintptr_t)&uid;attr.value=(uint64_t)(uintptr_t)value;
    return syscall(__NR_bpf,BPF_MAP_LOOKUP_ELEM,&attr,sizeof(attr));
}
static int bg_uid_write(int fd,uint32_t uid,const struct bg_uid_value *value,unsigned flags){
    union bpf_attr attr={0};attr.map_fd=fd;attr.key=(uint64_t)(uintptr_t)&uid;attr.value=(uint64_t)(uintptr_t)value;attr.flags=flags;
    return syscall(__NR_bpf,BPF_MAP_UPDATE_ELEM,&attr,sizeof(attr));
}
static int bg_uid_prepare_map(struct bg_uid_lease *lease,uint32_t uid){
    *lease=(struct bg_uid_lease){.fd=-1};
    if(uid<10000||uid>19999)return -1;
    lease->uid=uid;
    const char *map="/sys/fs/bpf/netd_shared/map_netd_uid_owner_map";
    union bpf_attr attr={0};attr.pathname=(uint64_t)(uintptr_t)map;
    int fd=syscall(__NR_bpf,BPF_OBJ_GET,&attr,sizeof(attr));if(fd<0)return -1;
    struct bpf_map_info info={0};memset(&attr,0,sizeof(attr));attr.info.bpf_fd=fd;attr.info.info_len=sizeof(info);attr.info.info=(uint64_t)(uintptr_t)&info;
    if(syscall(__NR_bpf,BPF_OBJ_GET_INFO_BY_FD,&attr,sizeof(attr))||info.key_size!=sizeof(uint32_t)||info.value_size!=sizeof(struct bg_uid_value)){close(fd);return -1;}
    lease->fd=fd;return 0;
}
static int bg_uid_prepare(struct bg_uid_lease *lease,pid_t guest){
    *lease=(struct bg_uid_lease){.fd=-1};
    char path[256];struct stat host,other;
    snprintf(path,sizeof(path),"/proc/%d/root",guest);
    if(stat("/",&host)||stat(path,&other)||(host.st_dev==other.st_dev&&host.st_ino==other.st_ino))return -1;
    snprintf(path,sizeof(path),"/proc/%d/root/dev/binder",guest);
    if(stat("/dev/binder",&host)||stat(path,&other)||!S_ISCHR(other.st_mode)||host.st_rdev==other.st_rdev)return -1;
    snprintf(path,sizeof(path),"/proc/%d/root/data/system/packages.list",guest);
    FILE *packages=fopen(path,"r");if(!packages)return -1;
    char line[4096],name[256];unsigned uid=0;int found=0;
    while(fgets(line,sizeof(line),packages)){
        if(sscanf(line,"%255s %u",name,&uid)==2&&!strcmp(name,"org.lineageos.jelly")){
            if(found||uid<10000||uid>19999){fclose(packages);return -1;}
            lease->uid=uid;found=1;
        }
    }fclose(packages);if(!found)return -1;
    return bg_uid_prepare_map(lease,lease->uid);
}
static int bg_uid_begin(struct bg_uid_lease *lease){
    if(lease->fd<0||lease->applied)return -1;
    memset(&lease->before,0,sizeof(lease->before));
    int result=bg_uid_lookup(lease->fd,lease->uid,&lease->before);
    if(result&&errno!=ENOENT)return -1;
    lease->existed=!result;lease->leased=lease->before;
    lease->leased.rule=(lease->leased.rule|(1U<<12)|(1U<<4))&~(1U<<3);
    if(!memcmp(&lease->leased,&lease->before,sizeof(lease->leased))){puts("GUEST_UID_ALREADY_ALLOWED_IN_HOST_BACKGROUND_AND_STANDBY_CHAINS");return 0;}
    if(bg_uid_write(lease->fd,lease->uid,&lease->leased,lease->existed?BPF_EXIST:BPF_NOEXIST))return -1;
    lease->applied=1;
    printf("GUEST_UID_BACKGROUND_AND_STANDBY_LEASE_APPLIED_UID=%u_GLOBAL_RULES_UNCHANGED\n",lease->uid);fflush(stdout);return 0;
}
static int bg_uid_restore(struct bg_uid_lease *lease){
    int result=0;
    if(lease->fd<0)return 0;
    if(lease->applied){
        struct bg_uid_value current={0};
        if(bg_uid_lookup(lease->fd,lease->uid,&current)){
            if(errno!=ENOENT)result=-1;
        }else if(memcmp(&current,&lease->leased,sizeof(current))){
            puts("BROWSER_UID_RESTORE_SKIPPED_EXTERNAL_RULE_CHANGE");result=-1;
        }else if(lease->existed){
            result=bg_uid_write(lease->fd,lease->uid,&lease->before,BPF_EXIST);
        }else{
            union bpf_attr attr={0};attr.map_fd=lease->fd;attr.key=(uint64_t)(uintptr_t)&lease->uid;
            result=syscall(__NR_bpf,BPF_MAP_DELETE_ELEM,&attr,sizeof(attr));
        }
        printf("BROWSER_UID_BACKGROUND_ALLOWANCE_RESTORE_RESULT=%d\n",result);fflush(stdout);
    }
    close(lease->fd);lease->fd=-1;lease->applied=0;return result;
}
// Package UIDs are discovered from the validated guest's private data directory.
// New installs are picked up during the lease; every changed entry is restored.
#define GUEST_UID_LEASE_LIMIT 1024
struct bg_uid_group {int directory,count;long long next;struct stat stamp;int stamped;struct bg_uid_lease entries[GUEST_UID_LEASE_LIMIT];};
static struct bg_uid_group *active_bg_group;
static int bg_group_prepare(struct bg_uid_group *group,pid_t guest){
    memset(group,0,sizeof(*group));group->directory=-1;
    if(bg_uid_prepare(&group->entries[0],guest))return -1;group->count=1;
    char path[256];snprintf(path,sizeof(path),"/proc/%d/root/data/system",guest);
    group->directory=open(path,O_RDONLY|O_DIRECTORY|O_CLOEXEC);return group->directory<0?-1:0;
}
static int bg_group_refresh(struct bg_uid_group *group){
    struct stat current;if(fstatat(group->directory,"packages.list",&current,AT_SYMLINK_NOFOLLOW)||!S_ISREG(current.st_mode))return -1;
    if(group->stamped&&current.st_ino==group->stamp.st_ino&&current.st_size==group->stamp.st_size&&current.st_mtim.tv_sec==group->stamp.st_mtim.tv_sec&&current.st_mtim.tv_nsec==group->stamp.st_mtim.tv_nsec)return 0;
    int fd=openat(group->directory,"packages.list",O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return -1;
    FILE *input=fdopen(fd,"r");if(!input){close(fd);return -1;}char line[4096],name[256];unsigned uid;int result=0;
    while(fgets(line,sizeof(line),input))if(sscanf(line,"%255s %u",name,&uid)==2&&uid>=10000&&uid<=19999){
        int found=0;for(int i=0;i<group->count;i++)if(group->entries[i].uid==uid){found=1;break;}
        if(found)continue;
        if(group->count>=GUEST_UID_LEASE_LIMIT){result=-1;break;}
        struct bg_uid_lease *lease=&group->entries[group->count];
        if(bg_uid_prepare_map(lease,uid)){result=-1;break;}group->count++;
        if(bg_uid_begin(lease)){result=-1;break;}
    }
    if(ferror(input))result=-1;fclose(input);
    if(!result){group->stamp=current;group->stamped=1;printf("GUEST_PACKAGE_UID_NETWORK_LEASES=%d_GLOBAL_RULES_UNCHANGED\n",group->count);}
    return result;
}
static int bg_group_begin(struct bg_uid_group *group){
    if(bg_uid_begin(&group->entries[0])||bg_group_refresh(group))return -1;
    active_bg_group=group;return 0;
}
static int bg_group_tick(struct bg_uid_group *group,long long now){
    if(now<group->next)return 0;group->next=now+5000;return bg_group_refresh(group);
}
static int bg_group_restore(struct bg_uid_group *group){
    active_bg_group=NULL;int result=0;
    for(int i=group->count-1;i>=0;i--)if(bg_uid_restore(&group->entries[i]))result=-1;
    if(group->directory>=0)close(group->directory);group->directory=-1;
    printf("GUEST_PACKAGE_UID_NETWORK_RESTORE_RESULT=%d_COUNT=%d\n",result,group->count);return result;
}
#endif
