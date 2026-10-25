#define _GNU_SOURCE
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sched.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
/* Explicit root maintenance inside an existing guest. Joins the guest cgroup
 * so the guardian also terminates maintenance processes on session exit. */
int main(int argc,char **argv){
    if(getuid()!=0||argc<4||strcmp(argv[1],"--pid")||argv[3][0]!='/')return 2;
    char *end;long pid=strtol(argv[2],&end,10);if(*end||pid<=1)return 2;
    int task=syscall(SYS_pidfd_open,pid,0);if(task<0)return 3;
    char path[256];snprintf(path,sizeof(path),"/proc/%ld/root",pid);int root=open(path,O_PATH|O_DIRECTORY|O_CLOEXEC);if(root<0)return 4;
    struct stat host,guest,current;
    if(stat("/proc/1/root",&host)||stat("/",&current)||fstat(root,&guest)||(host.st_dev==guest.st_dev&&host.st_ino==guest.st_ino)){puts("REFUSE_HOST_ROOT_TARGET_NO_CHANGES");return 5;}
    int already_guest=current.st_dev==guest.st_dev&&current.st_ino==guest.st_ino;
    snprintf(path,sizeof(path),"/proc/%ld/root/dev/binder",pid);if(stat("/dev/binder",&host)||stat(path,&guest)||!S_ISCHR(guest.st_mode)||(already_guest?host.st_rdev!=guest.st_rdev:host.st_rdev==guest.st_rdev)){puts("REFUSE_PRIMARY_BINDER_SCOPE_MISMATCH");return 6;}
    snprintf(path,sizeof(path),"/proc/%ld/root/dev/hwbinder",pid);if(stat("/dev/hwbinder",&host)||stat(path,&guest)||!S_ISCHR(guest.st_mode)||(already_guest?host.st_rdev!=guest.st_rdev:host.st_rdev==guest.st_rdev)){puts("REFUSE_HARDWARE_BINDER_SCOPE_MISMATCH");return 6;}
    snprintf(path,sizeof(path),"/proc/%ld/ns/mnt",pid);int mounts=open(path,O_RDONLY|O_CLOEXEC);snprintf(path,sizeof(path),"/proc/%ld/ns/net",pid);int network=open(path,O_RDONLY|O_CLOEXEC);if(mounts<0||network<0)return 7;
    snprintf(path,sizeof(path),"/proc/%ld/cgroup",pid);FILE *groups=fopen(path,"r");if(!groups)return 8;char line[300];int membership=-1;
    unsigned selected=0;
    while(fgets(line,sizeof(line),groups)){unsigned owner;char extra;if(sscanf(line,"0::/tempgsi_session_%u%c",&owner,&extra)==2&&extra=='\n'&&owner>1){selected=owner;snprintf(path,sizeof(path),"/sys/fs/cgroup/tempgsi_session_%u/cgroup.procs",owner);membership=open(path,O_WRONLY|O_CLOEXEC);break;}}fclose(groups);if(membership<0)return 8;
    if(already_guest){
        int matches=0;groups=fopen("/proc/self/cgroup","r");if(!groups)return 8;
        while(fgets(line,sizeof(line),groups)){unsigned owner;char extra;if(sscanf(line,"0::/tempgsi_session_%u%c",&owner,&extra)==2&&extra=='\n'&&owner==selected)matches=1;}fclose(groups);
        if(!matches){puts("REFUSE_CALLER_OUTSIDE_MATCHING_GUEST_CGROUP");return 8;}
    }
    struct pollfd alive={.fd=task,.events=POLLIN};if(poll(&alive,1,0)!=0)return 9;
    snprintf(line,sizeof(line),"%d",getpid());if(write(membership,line,strlen(line))!=(ssize_t)strlen(line))return 10;close(membership);
    if(setns(network,CLONE_NEWNET)||setns(mounts,CLONE_NEWNS)||fchdir(root)||chroot(".")||chdir("/")){perror("enter prepared guest");return 11;}
    close(network);close(mounts);close(root);close(task);
    clearenv();setenv("PATH","/system/bin:/system/xbin",1);setenv("HOME","/",1);setenv("ANDROID_ROOT","/system",1);setenv("ANDROID_DATA","/data",1);setenv("ANDROID_ART_ROOT","/apex/com.android.art",1);setenv("ANDROID_RUNTIME_ROOT","/apex/com.android.runtime",1);setenv("ANDROID_I18N_ROOT","/apex/com.android.i18n",1);setenv("ANDROID_TZDATA_ROOT","/apex/com.android.tzdata",1);setenv("EXTERNAL_STORAGE","/storage/emulated/0",1);
    execv(argv[3],argv+3);perror("guest exec");return 12;
}
