#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
static void normalize_launcher(void){
    umask(022);
    sigset_t signals;sigemptyset(&signals);sigprocmask(SIG_SETMASK,&signals,NULL);
    signal(SIGCHLD,SIG_DFL);signal(SIGPIPE,SIG_DFL);signal(SIGHUP,SIG_DFL);
    chdir("/");
}
static int active_coloros_owner(const char *boot){
    FILE *file=fopen("/data/local/tmp/gsionlocked-coloros-owner","r");
    if(!file)return 0;
    char owner_boot[80];int pid=0;int parsed=fscanf(file,"%79s %d",owner_boot,&pid);fclose(file);
    if(parsed!=2||pid<=1||pid==getpid()||strcmp(boot,owner_boot)||kill(pid,0))return 0;
    char path[80],command[4096];snprintf(path,sizeof(path),"/proc/%d/cmdline",pid);
    int fd=open(path,O_RDONLY|O_CLOEXEC);if(fd<0)return 0;ssize_t size=read(fd,command,sizeof(command));close(fd);
    const char *script="/data/local/tmp/oneos/start-coloros-logged.sh";
    const char *helper="/data/local/tmp/tempgsi-control/coloros-launch";
    return size>0&&(memmem(command,(size_t)size,script,strlen(script))||memmem(command,(size_t)size,helper,strlen(helper)));
}
static int detach_cgroup(void){
    const char *paths[]={"/sys/fs/cgroup/cgroup.procs","/dev/cpuctl/tasks","/dev/cpuset/tasks"};
    char pid[32];int length=snprintf(pid,sizeof(pid),"%d",getpid()),moved=0;
    for(unsigned i=0;i<sizeof(paths)/sizeof(paths[0]);i++){int fd=open(paths[i],O_WRONLY|O_CLOEXEC);if(fd<0)continue;if(write(fd,pid,length)==length)moved=1;close(fd);}
    return moved;
}
static int coloros_launch(void){
    if(getuid()!=0)return 2;
    const char *stage="/data/local/tmp/oneos";
    if(access(stage,F_OK))return 3;
    int lock=open("/data/local/tmp/gsionlocked-coloros-start.lock",O_CREAT|O_RDWR|O_NOFOLLOW|O_CLOEXEC,0600);
    if(lock<0||flock(lock,LOCK_EX|LOCK_NB)){puts("A ColorOS launch is already active");return 4;}
    char boot[80];FILE *bootfile=fopen("/proc/sys/kernel/random/boot_id","r");
    if(!bootfile)return 3;
    int boot_ok=fscanf(bootfile,"%79s",boot);fclose(bootfile);if(boot_ok!=1)return 3;
    if(active_coloros_owner(boot)){puts("A ColorOS startup process is already active");return 4;}
    int ready[2];if(pipe2(ready,O_CLOEXEC))return 5;
    pid_t child=fork();if(child<0)return 5;
    if(child){close(ready[1]);char ok=0;ssize_t n=read(ready[0],&ok,1);close(ready[0]);if(n!=1||ok!='1')return 9;printf("COLOROS_START_DISPATCHED=%d\n",child);return 0;}
    close(ready[0]);if(setsid()<0||!detach_cgroup())_exit(6);
    normalize_launcher();
    int owner=open("/data/local/tmp/gsionlocked-coloros-owner",O_CREAT|O_TRUNC|O_WRONLY|O_NOFOLLOW|O_CLOEXEC,0600);
    if(owner<0)_exit(7);
    char record[128];int count=snprintf(record,sizeof(record),"%s %d\n",boot,getpid());
    if(write(owner,record,(size_t)count)!=count)_exit(7);close(owner);
    int nullfd=open("/dev/null",O_RDONLY);
    int log=open("/data/local/tmp/oneos/takeover-s23-launcher.log",O_CREAT|O_TRUNC|O_WRONLY|O_NOFOLLOW,0600);
    if(nullfd<0||log<0)_exit(7);
    dup2(nullfd,0);dup2(log,1);dup2(log,2);if(nullfd>2)close(nullfd);if(log>2)close(log);
    if(write(ready[1],"1",1)!=1)_exit(9);close(ready[1]);
    execl("/data/adb/ksu/bin/busybox","busybox","nsenter","-t","1","-m","--","/system/bin/sh","/data/local/tmp/oneos/start-coloros-logged.sh",(char*)NULL);
    perror("ColorOS nsenter");return 8;
}
int main(int argc,char **argv){
    if(argc==2&&!strcmp(argv[1],"--coloros"))return coloros_launch();
    if(getuid()!=0||argc!=5||argv[1][0]!='/')return 2;
    if(strcmp(argv[3],"--unlimited"))return 2;
    if(strlen(argv[4])!=64||strspn(argv[4],"0123456789abcdef")!=64)return 2;
    char *end;long mib=strtol(argv[2],&end,10);if(*end||mib<512||mib>2147483647L)return 2;
    if(mkdir("/data/local/tmp/tempgsi-control",0700)&&access("/data/local/tmp/tempgsi-control",F_OK))return 3;
    int lock=open("/data/local/tmp/tempgsi-control/launcher.lock",O_CREAT|O_RDWR,0600);if(lock<0||flock(lock,LOCK_EX|LOCK_NB)){puts("A launch is already active");return 4;}
    int ready[2];if(pipe2(ready,O_CLOEXEC))return 5;
    pid_t child=fork();if(child<0)return 5;if(child){close(ready[1]);char ok=0;ssize_t n=read(ready[0],&ok,1);close(ready[0]);if(n!=1||ok!='1'){puts("Launcher failed before log initialization");return 9;}printf("Phone-owned controller PID %d\n",child);return 0;}
    close(ready[0]);
    if(setsid()<0||!detach_cgroup())_exit(6);
    int nullfd=open("/dev/null",O_RDONLY);int log=open("/data/local/tmp/tempgsi-control/latest.log",O_CREAT|O_TRUNC|O_WRONLY,0600);if(nullfd<0||log<0)_exit(7);
    dup2(nullfd,0);dup2(log,1);dup2(log,2);if(nullfd>2)close(nullfd);if(log>2)close(log);
    if(write(ready[1],"1",1)!=1)_exit(9);close(ready[1]);
    setenv("CLASSPATH",argv[1],1);
    execl("/system/bin/app_process","app_process","/system/bin","dev.tempgsi.controller.SessionMain",argv[1],argv[2],argv[3],argv[4],(char*)NULL);perror("app_process");return 8;
}
