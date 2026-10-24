/* Guardian retains both network namespace FDs across guest termination. */
struct wifi_handoff {int host,guest,moved;};
static int wifi_move_fds(int source,int target){
    pid_t child=fork();if(child<0)return -1;
    if(!child){
        if(fcntl(source,F_SETFD,0)||fcntl(target,F_SETFD,0))_exit(90);
        char from[24],to[24];snprintf(from,sizeof(from),"%d",source);snprintf(to,sizeof(to),"%d",target);
        alarm(7);execl("/data/local/tmp/tempgsi-wifi-ns-probe121","wifi-ns-probe","--move-fds",from,to,(char*)NULL);_exit(91);
    }
    int status;while(waitpid(child,&status,0)<0)if(errno!=EINTR)return -1;
    return WIFEXITED(status)&&WEXITSTATUS(status)==0?0:-1;
}
static int wifi_handoff_prepare(struct wifi_handoff *lease,long pid){
    char path[128];lease->host=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);
    snprintf(path,sizeof(path),"/proc/%ld/ns/net",pid);lease->guest=open(path,O_RDONLY|O_CLOEXEC);
    struct stat a,b;
    if(lease->host<0||lease->guest<0||fstat(lease->host,&a)||fstat(lease->guest,&b)||
       (a.st_dev==b.st_dev&&a.st_ino==b.st_ino))return -1;
    return 0;
}
static int wifi_handoff_start(struct wifi_handoff *lease,long pid){
    if(wifi_move_fds(lease->host,lease->guest))return -1;
    lease->moved=1;
    char path[256];snprintf(path,sizeof(path),"/proc/%ld/root/data/local/tmp/wifi-handoff.ready",pid);
    int ready=open(path,O_CREAT|O_EXCL|O_NOFOLLOW|O_WRONLY|O_CLOEXEC,0600);
    if(ready<0)return -1;close(ready);puts("WIFI_PHYSICAL_RADIO_LEASED_TO_GUEST");return 0;
}
static int wifi_handoff_restore(struct wifi_handoff *lease){
    int result=0;
    if(lease->moved){
        result=wifi_move_fds(lease->guest,lease->host);
        printf("WIFI_PHYSICAL_RADIO_RESTORE_RESULT=%d\n",result);
    }
    if(lease->guest>=0)close(lease->guest);if(lease->host>=0)close(lease->host);
    lease->guest=lease->host=-1;lease->moved=0;return result;
}
