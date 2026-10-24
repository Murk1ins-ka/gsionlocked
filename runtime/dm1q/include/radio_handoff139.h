// Optional radio ownership. Included after guardian service/pidfd helpers.
#include <sys/socket.h>
struct radio_lease139 {int binder,hardware,mounts,task,restore;pid_t pid;long guest;int mobile,network,channel,keeper_task,owner_watched;pid_t keeper;};
static void radio_exec139(struct radio_lease139 *lease,const char *mode){
    if(fcntl(lease->binder,F_SETFD,0)||fcntl(lease->hardware,F_SETFD,0)||fcntl(lease->mounts,F_SETFD,0))_exit(91);
    char binder[32],hardware[32],mounts[32];snprintf(binder,sizeof(binder),"%d",lease->binder);snprintf(hardware,sizeof(hardware),"%d",lease->hardware);snprintf(mounts,sizeof(mounts),"%d",lease->mounts);
    if(lease->mobile){
        char network[32];snprintf(network,sizeof(network),"%d",lease->network);if(fcntl(lease->network,F_SETFD,0))_exit(91);
        execl("/data/local/tmp/tempgsi-radio-runner139","radio-runner139",mode,binder,hardware,mounts,network,(char*)NULL);_exit(127);
    }
    execl("/data/local/tmp/tempgsi-radio-runner139","radio-runner139",mode,binder,hardware,mounts,(char*)NULL);_exit(127);
}
static int radio_private_fd139(long pid,const char *device){
    char path[256];struct stat a,b;snprintf(path,sizeof(path),"/proc/%ld/root%s",pid,device);
    int fd=open(path,O_PATH|O_CLOEXEC);
    if(fd<0||fstat(fd,&a)||stat(device,&b)||!S_ISCHR(a.st_mode)||a.st_rdev==b.st_rdev){if(fd>=0)close(fd);return -1;}return fd;
}
static int radio_prepare139(struct radio_lease139 *lease,long guest,const char *cgroup){
    lease->guest=guest;
    if(lease->mobile){
        char path[128];snprintf(path,sizeof(path),"/proc/%ld/ns/net",guest);lease->network=open(path,O_RDONLY|O_CLOEXEC);
        if(lease->network<0||access("/data/local/tmp/tempgsi-mobile-namespace143",X_OK))return -1;
    }
    if(!named_service_is("ril-daemon","running")||access("/data/local/tmp/tempgsi-radio-runner139",X_OK))return -1;
    lease->binder=radio_private_fd139(guest,"/dev/binder");if(lease->binder<0)return -1;
    char path[300];snprintf(path,sizeof(path),"%s/cgroup.procs",cgroup);FILE *input=fopen(path,"r");if(!input)return -1;
    long candidate;while(fscanf(input,"%ld",&candidate)==1){
        // SF itself has host hwbinder. Find another member of the exact guest
        // cgroup with both the matching private primary Binder and hwbinder.
        int primary=radio_private_fd139(candidate,"/dev/binder");struct stat a,b;
        int same=primary>=0&&!fstat(primary,&a)&&!fstat(lease->binder,&b)&&a.st_rdev==b.st_rdev;
        if(!same){if(primary>=0)close(primary);continue;}
        lease->hardware=radio_private_fd139(candidate,"/dev/hwbinder");
        if(lease->hardware>=0){
            // Both source mounts must belong to the namespace we enter for
            // open_tree. SF's separate mount namespace has the same rdev but
            // cannot serve as the primary mount source here.
            close(lease->binder);lease->binder=primary;
            snprintf(path,sizeof(path),"/proc/%ld/ns/mnt",candidate);lease->mounts=open(path,O_RDONLY|O_CLOEXEC);break;
        }
        close(primary);
    }fclose(input);
    if(lease->hardware<0||lease->mounts<0)return -1;
    pid_t test=fork();if(test<0)return -1;
    if(!test){alarm(5);radio_exec139(lease,"--check-credentials");}
    int status;while(waitpid(test,&status,0)<0)if(errno!=EINTR)return -1;
    if(!WIFEXITED(status)||WEXITSTATUS(status)){printf("RADIO_MOUNT_PREFLIGHT_FAILED_HOST_RIL_UNCHANGED_EXIT=%d\n",WIFEXITED(status)?WEXITSTATUS(status):-1);return -1;}
    puts("RADIO_PRIVATE_BINDER_SOURCES_PREPARED_STOCK_RIL_UNCHANGED");return 0;
}
static int radio_start139(struct radio_lease139 *lease){
    if(!named_service_is("zygote","stopped")||!named_service_is("zygote_secondary","stopped"))return -1;
    lease->restore=1;
    if(set_service("ctl.stop","ril-daemon","stopped"))return -1;
    puts("RADIO_HOST_RIL_STOPPED_FOR_EXCLUSIVE_GUEST_REGISTRATION");
    if(lease->mobile){
        int pair[2];if(socketpair(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC,0,pair))return -1;
        lease->keeper=fork();if(lease->keeper<0){close(pair[0]);close(pair[1]);return -1;}
        if(!lease->keeper){
            close(pair[0]);char network[24],owner[24],control[24];
            snprintf(network,sizeof(network),"%d",lease->network);snprintf(owner,sizeof(owner),"%d",getppid());snprintf(control,sizeof(control),"%d",pair[1]);
            if(fcntl(lease->network,F_SETFD,0)||fcntl(pair[1],F_SETFD,0))_exit(90);
            execl("/data/local/tmp/tempgsi-mobile-namespace143","mobile143",lease->owner_watched?"--lease-owner-watched145":"--lease",network,owner,control,(char*)NULL);_exit(127);
        }
        close(pair[1]);lease->channel=pair[0];lease->keeper_task=pidfd_open_checked(lease->keeper);
        struct pollfd ready={.fd=lease->channel,.events=POLLIN};char mark=0;
        if(lease->keeper_task<0||poll(&ready,1,35000)!=1||recv(lease->channel,&mark,1,0)!=1||mark!='R'){puts("RADIO_MOBILE143_PREPARATION_FAILED_RESTORE_REQUIRED");return -1;}
        puts("RADIO_MOBILE143_REAL_MODEM_INTERFACES_READY");
    }
    lease->pid=fork();if(lease->pid<0)return -1;
    if(!lease->pid){
        // Child must not survive unexpected guardian death.
        pid_t parent=getppid();if(parent<=1||prctl(PR_SET_PDEATHSIG,SIGKILL)||getppid()!=parent)_exit(90);
        int null=open("/dev/null",O_RDONLY);if(null<0||dup2(null,0)<0)_exit(92);if(null>2)close(null);
        radio_exec139(lease,"--run-fds");
    }
    lease->task=pidfd_open_checked(lease->pid);
    if(lease->task<0){kill(lease->pid,SIGKILL);waitpid(lease->pid,NULL,0);lease->pid=-1;return -1;}
    printf("RADIO_GUEST_RIL_PID=%d_STOCK_LIBRARY_VIEW_NETWORK=%s\n",lease->pid,lease->mobile?"PRIVATE143":"HOST");
    // File presence must not enable an incomplete network experiment.
    puts("RADIO_INCOMPLETE_CELLULAR_BRIDGE142_DISABLED");
    return 0;
}
static int radio_restore139(struct radio_lease139 *lease){
    int result=0;
    if(lease->task>=0){kill_fd(lease->task);close(lease->task);lease->task=-1;}
    if(lease->pid>1){int status=0;pid_t waited;do{waited=waitpid(lease->pid,&status,0);}while(waited<0&&errno==EINTR);
        if(waited<0)result=-1;else printf("RADIO_CHILD_TERMINAL_EXIT=%d_SIGNAL=%d\n",WIFEXITED(status)?WEXITSTATUS(status):-1,WIFSIGNALED(status)?WTERMSIG(status):0);lease->pid=-1;}
    if(lease->channel>=0){
        char command='Q';send(lease->channel,&command,1,MSG_NOSIGNAL);close(lease->channel);lease->channel=-1;
    }
    if(lease->keeper>1){
        // Never kill the independent keeper during rollback: it owns the
        // namespace descriptors required to bring virtual rmnet devices home.
        struct pollfd done={.fd=lease->keeper_task,.events=POLLIN};
        int ready=lease->keeper_task>=0?poll(&done,1,35000):-1;
        int status=0;pid_t waited=ready==1?waitpid(lease->keeper,&status,0):-1;
        if(waited!=lease->keeper||!WIFEXITED(status)||WEXITSTATUS(status)){puts("RADIO_MOBILE143_RESTORE_NOT_CONFIRMED");result=-1;}
        else puts("RADIO_MOBILE143_RESTORE_CONFIRMED");
    }
    if(lease->keeper_task>=0)close(lease->keeper_task);
    if(lease->network>=0)close(lease->network);
    if(lease->binder>=0)close(lease->binder);if(lease->hardware>=0)close(lease->hardware);
    if(lease->mounts>=0)close(lease->mounts);lease->mounts=-1;
    lease->binder=lease->hardware=-1;
    if(lease->restore){int restored=set_service("ctl.start","ril-daemon","running");printf("RADIO_HOST_RIL_RESTORE_RESULT=%d\n",restored);if(restored)result=-1;lease->restore=0;}
    return result;
}
