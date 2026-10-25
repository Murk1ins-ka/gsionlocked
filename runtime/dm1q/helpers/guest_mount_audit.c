#define _GNU_SOURCE
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/ioctl.h>
#include <linux/dm-ioctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
static int no_userspace(long pid){
    char path[128],line[8192],state=0;unsigned long flags=0;
    snprintf(path,sizeof(path),"/proc/%ld/stat",pid);FILE *input=fopen(path,"r");if(!input)return errno==ENOENT||errno==ESRCH;
    int ok=fgets(line,sizeof(line),input)!=NULL;fclose(input);if(!ok)return 0;
    char *tail=strrchr(line,')');if(!tail||sscanf(tail+1," %c %*d %*d %*d %*d %*d %lu",&state,&flags)!=2)return 0;
    return state=='Z'||state=='X'||(flags&0x00200000UL);
}
int main(int argc,char **argv){
    if(argc!=2)return 2;struct stat device;if(stat(argv[1],&device)||!S_ISBLK(device.st_mode))return 3;
    unsigned target_major=major(device.st_rdev),target_minor=minor(device.st_rdev);
    printf("TARGET_DEVICE=%u:%u\n",target_major,target_minor);
    int control=open("/dev/device-mapper",O_RDONLY|O_CLOEXEC);if(control>=0){struct dm_ioctl info={0};info.version[0]=DM_VERSION_MAJOR;info.version[1]=0;info.version[2]=0;info.data_size=sizeof(info);info.data_start=sizeof(info);const char *name=strrchr(argv[1],'/');snprintf(info.name,sizeof(info.name),"%s",name?name+1:argv[1]);if(!ioctl(control,DM_DEV_STATUS,&info))printf("TARGET_DM_OPEN_COUNT=%d\n",info.open_count);else perror("DM_DEV_STATUS");close(control);}
    DIR *proc=opendir("/proc");if(!proc)return 4;struct dirent *entry;int inspected=0,unreadable=0,holders=0;
    while((entry=readdir(proc))){char *end;long pid=strtol(entry->d_name,&end,10);if(*end||pid<1)continue;
        char path[128],line[8192];snprintf(path,sizeof(path),"/proc/%ld/mountinfo",pid);FILE *mounts=fopen(path,"r");if(!mounts){int error=errno;if(error!=ENOENT&&error!=ESRCH&&!no_userspace(pid)){printf("LIVE_MOUNT_TABLE_UNREADABLE_PID=%ld_ERRNO=%d\n",pid,error);unreadable++;}continue;}inspected++;int found=0;
        while(fgets(line,sizeof(line),mounts)){unsigned devmajor,devminor;int id,parent;char root[256],point[256];if(sscanf(line,"%d %d %u:%u %255s %255s",&id,&parent,&devmajor,&devminor,root,point)==6&&devmajor==target_major&&devminor==target_minor&&strstr(line," - ext4 ")){found=1;break;}}
        fclose(mounts);
        const char *references[]={"cwd","root","exe"};
        for(unsigned index=0;index<3;index++){struct stat referenced;snprintf(path,sizeof(path),"/proc/%ld/%s",pid,references[index]);if(!stat(path,&referenced)&&referenced.st_dev==device.st_rdev){found=1;printf("GUEST_VOLUME_PROCESS_REFERENCE_PID=%ld_KIND=%s\n",pid,references[index]);}}
        snprintf(path,sizeof(path),"/proc/%ld/maps",pid);FILE *maps=fopen(path,"r");if(maps){while(fgets(line,sizeof(line),maps)){unsigned m,n;if(sscanf(line,"%*s %*s %*s %x:%x",&m,&n)==2&&m==target_major&&n==target_minor){found=1;printf("GUEST_VOLUME_MEMORY_MAPPING_PID=%ld\n",pid);break;}}fclose(maps);}
        snprintf(path,sizeof(path),"/proc/%ld/ns/mnt",pid);struct stat leader;int have_leader=!stat(path,&leader);
        snprintf(path,sizeof(path),"/proc/%ld/task",pid);DIR *tasks=opendir(path);struct dirent *task;
        if(tasks){while((task=readdir(tasks))){char *tail;long tid=strtol(task->d_name,&tail,10);if(*tail||tid<=0||tid==pid)continue;snprintf(path,sizeof(path),"/proc/%ld/task/%ld/ns/mnt",pid,tid);struct stat thread_ns;if(stat(path,&thread_ns)||(have_leader&&thread_ns.st_ino==leader.st_ino))continue;
            snprintf(path,sizeof(path),"/proc/%ld/task/%ld/mountinfo",pid,tid);FILE *thread_mounts=fopen(path,"r");if(!thread_mounts){unreadable++;continue;}while(fgets(line,sizeof(line),thread_mounts)){int id,parent;unsigned m,n;if(sscanf(line,"%d %d %u:%u",&id,&parent,&m,&n)==4&&m==target_major&&n==target_minor){printf("GUEST_VOLUME_THREAD_MOUNT_HOLDER_PID=%ld_TID=%ld\n",pid,tid);found=1;break;}}fclose(thread_mounts);
        }closedir(tasks);}
        snprintf(path,sizeof(path),"/proc/%ld/fd",pid);DIR *fds=opendir(path);struct dirent *fd;
        if(fds){while((fd=readdir(fds))){char fdpath[256];struct stat opened;snprintf(fdpath,sizeof(fdpath),"/proc/%ld/fd/%s",pid,fd->d_name);if(!stat(fdpath,&opened)&&(opened.st_dev==device.st_rdev||(S_ISBLK(opened.st_mode)&&opened.st_rdev==device.st_rdev))){found=1;printf("GUEST_VOLUME_OPEN_FD_PID=%ld_FD=%s\n",pid,fd->d_name);}}closedir(fds);}
        if(!found)continue;
        holders++;
        snprintf(path,sizeof(path),"/proc/%ld/comm",pid);FILE *name=fopen(path,"r");char comm[128]={0};if(name){fgets(comm,sizeof(comm),name);fclose(name);}comm[strcspn(comm,"\n")]=0;
        printf("GUEST_VOLUME_MOUNT_HOLDER_PID=%ld_COMM=%s\n",pid,comm);
        snprintf(path,sizeof(path),"/proc/%ld/cgroup",pid);FILE *group=fopen(path,"r");if(group){while(fgets(line,sizeof(line),group))fputs(line,stdout);fclose(group);}
    }closedir(proc);printf("PROCESS_MOUNT_TABLES_READ=%d_UNREADABLE=%d_HOLDERS=%d\n",inspected,unreadable,holders);return holders?10:unreadable?11:0;
}
