/* Borrow the existing Software Watchdog's open file description before stopping
 * the host framework. Never open a new watchdog or alter timeout/options. */
#include <dirent.h>
#include <linux/watchdog.h>
static int adopt_host_softdog(void){
    struct stat root;if(stat("/",&root))return -1;
    DIR *processes=opendir("/proc");if(!processes)return -1;struct dirent *process;int adopted=-1;
    while(adopted<0&&(process=readdir(processes))){
        char *end;long pid=strtol(process->d_name,&end,10);if(*end||pid<=1)continue;
        char path[128],name[64]={0};struct stat candidate;
        snprintf(path,sizeof(path),"/proc/%ld/root",pid);if(stat(path,&candidate)||candidate.st_dev!=root.st_dev||candidate.st_ino!=root.st_ino)continue;
        snprintf(path,sizeof(path),"/proc/%ld/comm",pid);FILE *comm=fopen(path,"r");if(!comm)continue;fgets(name,sizeof(name),comm);fclose(comm);if(strcmp(name,"system_server\n"))continue;
        int task=syscall(SYS_pidfd_open,(pid_t)pid,0);if(task<0)continue;
        snprintf(path,sizeof(path),"/proc/%ld/fd",pid);DIR *fds=opendir(path);if(!fds){close(task);continue;}struct dirent *entry;
        while((entry=readdir(fds))){
            long number=strtol(entry->d_name,&end,10);if(*end||number<0)continue;
            snprintf(path,sizeof(path),"/proc/%ld/fd/%ld",pid,number);char link[256];ssize_t n=readlink(path,link,sizeof(link)-1);if(n<0)continue;link[n]=0;
            if(strcmp(link,"/dev/watchdog")&&strcmp(link,"/dev/watchdog0"))continue;
            int fd=syscall(SYS_pidfd_getfd,task,(int)number,0);if(fd<0)continue;
            struct watchdog_info info={0};int timeout=-1;
            if(ioctl(fd,WDIOC_GETSUPPORT,&info)||ioctl(fd,WDIOC_GETTIMEOUT,&timeout)){close(fd);continue;}
            info.identity[sizeof(info.identity)-1]=0;
            if(strcmp((char*)info.identity,"Software Watchdog")||timeout!=100||!(info.options&WDIOF_KEEPALIVEPING)||!(info.options&WDIOF_MAGICCLOSE)){close(fd);continue;}
            printf("HOST_SOFTDOG_EXISTING_FD_ADOPTED_OWNER=%ld_TIMEOUT=%d\n",pid,timeout);adopted=fd;break;
        }
        closedir(fds);close(task);
    }
    closedir(processes);return adopted;
}
