#define _GNU_SOURCE
#include <sched.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <linux/rtnetlink.h>
#include <net/if.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* An independent root process owns the namespace descriptors until rollback.
 * Parent death, control-channel loss, and a 200s deadline all restore devices.
 * No routes, addresses, global forwarding, firewall or modem IDs are written.
 */
struct device {char name[IFNAMSIZ];int up,moved;};
static int acknowledge(int fd,struct nlmsghdr *request){
    struct sockaddr_nl kernel={.nl_family=AF_NETLINK};
    if(sendto(fd,request,request->nlmsg_len,0,(void*)&kernel,sizeof(kernel))!=(ssize_t)request->nlmsg_len)return -1;
    struct pollfd p={.fd=fd,.events=POLLIN};if(poll(&p,1,1500)!=1){errno=ETIMEDOUT;return -1;}
    char reply[4096];struct sockaddr_nl peer={0};socklen_t size=sizeof(peer);
    int length=recvfrom(fd,reply,sizeof(reply),0,(void*)&peer,&size);
    if(length<0)return -1;if(peer.nl_pid){errno=EPROTO;return -1;}
    for(struct nlmsghdr *n=(void*)reply;NLMSG_OK(n,length);n=NLMSG_NEXT(n,length)){
        if(n->nlmsg_seq!=request->nlmsg_seq)continue;
        if(n->nlmsg_type==NLMSG_ERROR&&n->nlmsg_len>=NLMSG_LENGTH(sizeof(struct nlmsgerr))){
            int error=((struct nlmsgerr*)NLMSG_DATA(n))->error;if(error){errno=-error;return -1;}return 0;
        }
    }errno=EPROTO;return -1;
}
static int attr(struct nlmsghdr *n,size_t capacity,int type,const void *data,size_t length){
    size_t offset=NLMSG_ALIGN(n->nlmsg_len),total=RTA_LENGTH(length);
    if(offset+RTA_ALIGN(total)>capacity){errno=E2BIG;return -1;}
    struct rtattr *a=(void*)((char*)n+offset);a->rta_type=type;a->rta_len=total;
    if(length)memcpy(RTA_DATA(a),data,length);n->nlmsg_len=offset+RTA_ALIGN(total);return 0;
}
static int transact(struct nlmsghdr *n){
    int fd=socket(AF_NETLINK,SOCK_RAW|SOCK_CLOEXEC,NETLINK_ROUTE);if(fd<0)return -1;
    struct sockaddr_nl local={.nl_family=AF_NETLINK};int result=bind(fd,(void*)&local,sizeof(local));
    if(!result)result=acknowledge(fd,n);int saved=errno;close(fd);errno=saved;return result;
}
static int move_device(const char *name,int target){
    unsigned index=if_nametoindex(name);if(!index){errno=ENODEV;return -1;}
    char buffer[256]={0};struct nlmsghdr *n=(void*)buffer;
    n->nlmsg_len=NLMSG_LENGTH(sizeof(struct ifinfomsg));n->nlmsg_type=RTM_NEWLINK;n->nlmsg_flags=NLM_F_REQUEST|NLM_F_ACK;n->nlmsg_seq=143;
    struct ifinfomsg *info=NLMSG_DATA(n);info->ifi_family=AF_UNSPEC;info->ifi_index=index;
    if(attr(n,sizeof(buffer),IFLA_NET_NS_FD,&target,sizeof(target)))return -1;
    return transact(n);
}
static int flags(const char *name,int set_up){
    int fd=socket(AF_INET,SOCK_DGRAM|SOCK_CLOEXEC,0);if(fd<0)return -1;
    struct ifreq r={0};snprintf(r.ifr_name,sizeof(r.ifr_name),"%s",name);
    int result=ioctl(fd,SIOCGIFFLAGS,&r);
    if(!result&&set_up){r.ifr_flags|=IFF_UP;result=ioctl(fd,SIOCSIFFLAGS,&r);}
    int up=r.ifr_flags&IFF_UP;close(fd);return result?-1:!!up;
}
static int allowed(const char *name){
    // The rmnet driver destroys/recreates its dynamic virtual endpoints when
    // the physical port changes namespace. Lease the port, not those transient
    // endpoint objects; the RIL recreates active data calls in its own network.
    return !strcmp(name,"rmnet_ipa0");
}
static int restore(struct device *devices,int count,int host,int guest){
    int failed=0;
    for(int i=count-1;i>=0;i--)if(devices[i].moved){
        if(setns(guest,CLONE_NEWNET)){printf("MOBILE143_RESTORE_NAMESPACE_FAILED_ERRNO=%d\n",errno);failed=1;continue;}
        if(move_device(devices[i].name,host)){
            int error=errno;
            // Moving the physical rmnet base can unregister and recreate its
            // virtual endpoints. Count this only after finding the named link
            // in the host namespace, never from ENODEV alone.
            if(error==ENODEV&&!setns(host,CLONE_NEWNET)&&if_nametoindex(devices[i].name)){
                devices[i].moved=0;
                if(devices[i].up&&flags(devices[i].name,1)<0)failed=1;
                printf("MOBILE143_BASE_RECREATED_HOST_LINK_VERIFIED_NAME=%s\n",devices[i].name);continue;
            }
            printf("MOBILE143_RESTORE_FAILED_NAME=%s_ERRNO=%d\n",devices[i].name,error);failed=1;continue;
        }
        devices[i].moved=0;
        if(setns(host,CLONE_NEWNET)||(devices[i].up&&flags(devices[i].name,1)<0)){printf("MOBILE143_RESTORE_FLAGS_FAILED_NAME=%s_ERRNO=%d\n",devices[i].name,errno);failed=1;}
    }
    if(setns(host,CLONE_NEWNET))failed=1;
    printf("MOBILE143_RESTORE_RESULT=%d\n",failed);return failed;
}
static int create_dummy(const char *name){
    char buffer[512]={0};struct nlmsghdr *n=(void*)buffer;
    n->nlmsg_len=NLMSG_LENGTH(sizeof(struct ifinfomsg));n->nlmsg_type=RTM_NEWLINK;n->nlmsg_flags=NLM_F_REQUEST|NLM_F_ACK|NLM_F_CREATE|NLM_F_EXCL;n->nlmsg_seq=144;
    const char kind[]="dummy";
    if(attr(n,sizeof(buffer),IFLA_IFNAME,name,strlen(name)+1))return -1;
    size_t start=NLMSG_ALIGN(n->nlmsg_len);if(attr(n,sizeof(buffer),IFLA_LINKINFO,NULL,0))return 31;
    if(attr(n,sizeof(buffer),IFLA_INFO_KIND,kind,sizeof(kind)))return 31;
    ((struct rtattr*)(buffer+start))->rta_len=n->nlmsg_len-start;
    return transact(n);
}
static int fixture(void){
    // All fixture devices are created inside a disposable namespace.
    if(unshare(CLONE_NEWNET))return 30;
    int source=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);if(source<0)return 30;
    const char name[]="mobile143test";if(create_dummy(name))return 32;
    if(unshare(CLONE_NEWNET))return 33;
    int target=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);if(target<0||setns(source,CLONE_NEWNET))return 33;
    if(move_device(name,target)||if_nametoindex(name))return 34;
    if(setns(target,CLONE_NEWNET)||!if_nametoindex(name)||move_device(name,source))return 35;
    if(setns(source,CLONE_NEWNET)||!if_nametoindex(name))return 36;
    close(target);close(source);puts("MOBILE143_DISPOSABLE_NAMESPACE_ROUNDTRIP_OK_NO_MODEM_CHANGES");return 0;
}
static int owner_fixture(int unexpected,int owner_watched){
    if(prctl(PR_SET_CHILD_SUBREAPER,1)||unshare(CLONE_NEWNET))return 40;
    int host=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);if(host<0||create_dummy("rmnet_ipa0")||unshare(CLONE_NEWNET))return 41;
    int guest=open("/proc/self/ns/net",O_RDONLY);if(guest<0||setns(host,CLONE_NEWNET))return 42;
    int report[2],command[2];if(pipe(report)||pipe(command))return 43;
    pid_t owner=fork();if(owner<0)return 44;
    if(!owner){
        close(report[0]);close(command[1]);int channel[2];if(socketpair(AF_UNIX,SOCK_SEQPACKET,0,channel))_exit(50);
        pid_t keeper=fork();if(keeper<0)_exit(51);
        if(!keeper){
            close(channel[0]);char a[24],b[24],c[24];snprintf(a,sizeof(a),"%d",guest);snprintf(b,sizeof(b),"%d",getppid());snprintf(c,sizeof(c),"%d",channel[1]);
            execl("/proc/self/exe","mobile143",owner_watched?"--lease-owner-watched145":"--lease",a,b,c,(char*)NULL);_exit(52);
        }
        close(channel[1]);struct pollfd ready={.fd=channel[0],.events=POLLIN};char mark=0;
        if(poll(&ready,1,8000)!=1||recv(channel[0],&mark,1,0)!=1||mark!='R'){kill(keeper,SIGKILL);_exit(53);}
        if(write(report[1],&keeper,sizeof(keeper))!=sizeof(keeper))_exit(54);
        if(read(command[0],&mark,1)!=1)_exit(55);
        mark='Q';send(channel[0],&mark,1,MSG_NOSIGNAL);ready.revents=0;
        int polled=poll(&ready,1,8000);int received=polled==1?recv(channel[0],&mark,1,0):-2;
        if(polled!=1||received!=1||mark!='D'){printf("MOBILE143_FIXTURE_DONE_POLL=%d_RECV=%d_MARK=%d_ERRNO=%d\n",polled,received,mark,errno);_exit(56);}
        int status=0;pid_t waited=waitpid(keeper,&status,0);
        if(waited!=keeper||!WIFEXITED(status)||WEXITSTATUS(status)){printf("MOBILE143_FIXTURE_KEEPER_WAIT=%d_ERRNO=%d_STATUS=%d\n",waited,errno,status);_exit(57);}
        _exit(0);
    }
    close(report[1]);close(command[0]);struct pollfd ready={.fd=report[0],.events=POLLIN};pid_t keeper=-1;
    if(poll(&ready,1,10000)!=1||read(report[0],&keeper,sizeof(keeper))!=sizeof(keeper)){kill(owner,SIGKILL);waitpid(owner,NULL,0);return 45;}
    int result=0;if(if_nametoindex("rmnet_ipa0"))result=46;
    if(unexpected)kill(owner,SIGKILL);else {char mark='Q';if(write(command[1],&mark,1)!=1)result=47;}
    int status;if(waitpid(owner,&status,0)!=owner)result=48;
    if(!unexpected&&(!WIFEXITED(status)||WEXITSTATUS(status))){printf("MOBILE143_FIXTURE_OWNER_STATUS=%d\n",status);result=49;}
    if(unexpected){
        int task=syscall(SYS_pidfd_open,keeper,0);struct pollfd done={.fd=task,.events=POLLIN};
        if(task<0||poll(&done,1,8000)!=1){kill(keeper,SIGKILL);result=58;}
        if(task>=0)close(task);
        if(waitpid(keeper,&status,0)!=keeper||!WIFEXITED(status)||WEXITSTATUS(status))result=59;
    }
    if(!if_nametoindex("rmnet_ipa0"))result=60;
    close(guest);close(host);close(report[0]);close(command[1]);
    printf("MOBILE143_%s_FIXTURE_RESULT=%d_NO_REAL_MODEM_CHANGES\n",unexpected?"OWNER_DEATH":"NORMAL_RETURN",result);return result;
}
int main(int argc,char **argv){
    setbuf(stdout,NULL);if(getuid()!=0)return 2;
    if(argc==2&&!strcmp(argv[1],"--self-test"))return fixture();
    if(argc==2&&!strcmp(argv[1],"--self-test-owner-death"))return owner_fixture(1,0);
    if(argc==2&&!strcmp(argv[1],"--self-test-return"))return owner_fixture(0,0);
    if(argc==2&&!strcmp(argv[1],"--self-test-owner-watched-death145"))return owner_fixture(1,1);
    if(argc==2&&!strcmp(argv[1],"--self-test-owner-watched-return145"))return owner_fixture(0,1);
    int owner_watched=argc==5&&!strcmp(argv[1],"--lease-owner-watched145");
    if(argc!=5||(!owner_watched&&strcmp(argv[1],"--lease")))return 2;
    char *end;long guest=strtol(argv[2],&end,10);if(*end||guest<3)return 2;
    long parent=strtol(argv[3],&end,10);if(*end||parent<=1||getppid()!=parent)return 2;
    long control=strtol(argv[4],&end,10);if(*end||control<3||control==guest)return 2;
    int host=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC),owner=syscall(SYS_pidfd_open,parent,0);
    struct stat a,b;if(host<0||owner<0||fstat(host,&a)||fstat(guest,&b)||(a.st_dev==b.st_dev&&a.st_ino==b.st_ino))return 3;
    struct device devices[65]={0};int count=0,result=0;
    struct if_nameindex *names=if_nameindex();if(!names)return 4;
    for(struct if_nameindex *p=names;p->if_index;p++)if(allowed(p->if_name)){
        if(count>=65){result=1;break;}
        snprintf(devices[count].name,IFNAMSIZ,"%s",p->if_name);devices[count].up=flags(p->if_name,0);if(devices[count].up<0){result=1;break;}count++;
    }
    if_freenameindex(names);
    if(!count)result=1;
    // The modem's base link moves last; roll back in reverse order.
    for(int i=0;i<count;i++)if(!strcmp(devices[i].name,"rmnet_ipa0")){struct device base=devices[i];memmove(&devices[i],&devices[i+1],(count-i-1)*sizeof(base));devices[count-1]=base;break;}
    for(int i=0;!result&&i<count;i++){
        if(move_device(devices[i].name,guest)){printf("MOBILE143_MOVE_FAILED_NAME=%s_ERRNO=%d\n",devices[i].name,errno);result=1;break;}
        devices[i].moved=1;printf("MOBILE143_MOVED_NAME=%s\n",devices[i].name);
    }
    char ready=result?'E':'R';if(send(control,&ready,1,MSG_NOSIGNAL)!=1)result=1;
    if(!result){
        struct pollfd observed[2]={{.fd=owner,.events=POLLIN},{.fd=control,.events=POLLIN}};
        // The long session still returns on either guardian death or channel
        // loss. Its guardian independently watches heat, guest heartbeat and
        // the physical return keys; the bounded test keeps its 200s deadline.
        int event;do{event=poll(observed,2,owner_watched?-1:200000);}while(event<0&&errno==EINTR);
        printf("MOBILE143_RETURN_EVENT=%d_OWNER_WATCHED=%d\n",event,owner_watched);
        // Drain the request before closing SOCK_SEQPACKET; otherwise the
        // unread message can reset the peer and discard our restore receipt.
        if(event>0&&(observed[1].revents&POLLIN)){char request;recv(control,&request,1,MSG_DONTWAIT);}
    }
    int restored=restore(devices,count,host,guest);
    char done=restored?'E':'D';send(control,&done,1,MSG_NOSIGNAL);
    close(control);close(owner);close(guest);close(host);return result||restored?5:0;
}
