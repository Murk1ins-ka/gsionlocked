#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static int detach_cgroup(void){
    const char *paths[]={"/sys/fs/cgroup/cgroup.procs","/dev/cpuctl/tasks","/dev/cpuset/tasks"};
    char pid[32];int length=snprintf(pid,sizeof(pid),"%d",getpid()),moved=0;
    for(unsigned i=0;i<sizeof(paths)/sizeof(paths[0]);i++){int fd=open(paths[i],O_WRONLY|O_CLOEXEC);if(fd<0)continue;if(write(fd,pid,length)==length)moved=1;close(fd);}
    return moved;
}
int main(int argc,char **argv){
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
