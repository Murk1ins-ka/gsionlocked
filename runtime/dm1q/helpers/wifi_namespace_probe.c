/* Explicit nl80211 inventory, bounded roundtrip and guardian-owned FD handoff. */
#define _GNU_SOURCE
#include <linux/netlink.h>
#include <linux/genetlink.h>
#include <linux/nl80211.h>
#include <sys/socket.h>
#include <unistd.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <poll.h>
#include <fcntl.h>
#include <sched.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <signal.h>
#include <stdlib.h>
static int detected_family,netns_capable;
static uint32_t sequence;
static int attr_ok(const struct nlattr *a,int n){return n>=(int)sizeof(*a)&&a->nla_len>=sizeof(*a)&&a->nla_len<=n;}
static const struct nlattr *next_attr(const struct nlattr *a,int *n){int size=NLA_ALIGN(a->nla_len);*n-=size;return (const void*)((const char*)a+size);}
static const void *payload(const struct nlattr *a){return (const char*)a+NLA_HDRLEN;}
static uint32_t u32(const struct nlattr *a){uint32_t value=0;if(a->nla_len==NLA_HDRLEN+4)memcpy(&value,payload(a),4);return value;}
static int request(int fd,int family,int command,int dump,int type,const void *value,size_t size){
    char buffer[256]={0};struct nlmsghdr *n=(void*)buffer;
    n->nlmsg_len=NLMSG_LENGTH(GENL_HDRLEN);n->nlmsg_type=family;n->nlmsg_flags=NLM_F_REQUEST|(dump?NLM_F_DUMP:0);n->nlmsg_seq=++sequence;
    struct genlmsghdr *g=NLMSG_DATA(n);g->cmd=command;g->version=1;
    if(type){struct nlattr *a=(void*)(buffer+NLMSG_ALIGN(n->nlmsg_len));a->nla_type=type;a->nla_len=NLA_HDRLEN+size;memcpy((char*)a+NLA_HDRLEN,value,size);n->nlmsg_len=NLMSG_ALIGN(n->nlmsg_len)+NLA_ALIGN(a->nla_len);}
    struct sockaddr_nl kernel={.nl_family=AF_NETLINK};return sendto(fd,buffer,n->nlmsg_len,0,(void*)&kernel,sizeof(kernel))==(ssize_t)n->nlmsg_len?0:-1;
}
static int receive(int fd,char *buffer,size_t capacity){
    struct pollfd p={.fd=fd,.events=POLLIN};if(poll(&p,1,3000)!=1){errno=ETIMEDOUT;return -1;}
    struct sockaddr_nl peer={0};struct iovec io={.iov_base=buffer,.iov_len=capacity};struct msghdr msg={.msg_name=&peer,.msg_namelen=sizeof(peer),.msg_iov=&io,.msg_iovlen=1};
    int n=recvmsg(fd,&msg,0);if(n<0)return -1;if(peer.nl_pid||msg.msg_flags&MSG_TRUNC){errno=EPROTO;return -1;}return n;
}
static int inventory(void){
    setbuf(stdout,NULL);int fd=socket(AF_NETLINK,SOCK_RAW|SOCK_CLOEXEC,NETLINK_GENERIC);if(fd<0){perror("generic netlink");return 3;}
    struct sockaddr_nl local={.nl_family=AF_NETLINK};if(bind(fd,(void*)&local,sizeof(local))){perror("bind");return 4;}
    const char name[]="nl80211";if(request(fd,GENL_ID_CTRL,CTRL_CMD_GETFAMILY,0,CTRL_ATTR_FAMILY_NAME,name,sizeof(name)))return 5;
    char buffer[131072];int length=receive(fd,buffer,sizeof(buffer));if(length<0){perror("family receive");return 6;}
    int family=0;
    for(struct nlmsghdr *n=(void*)buffer;NLMSG_OK(n,length);n=NLMSG_NEXT(n,length)){
        if(n->nlmsg_seq!=sequence)continue;
        if(n->nlmsg_type==NLMSG_ERROR){struct nlmsgerr *e=NLMSG_DATA(n);printf("NETLINK_FAMILY_ERROR=%d\n",e->error);return 7;}
        int left=n->nlmsg_len-NLMSG_HDRLEN-GENL_HDRLEN;
        for(const struct nlattr *a=(void*)((char*)NLMSG_DATA(n)+GENL_HDRLEN);attr_ok(a,left);a=next_attr(a,&left))
            if((a->nla_type&NLA_TYPE_MASK)==CTRL_ATTR_FAMILY_ID&&a->nla_len==NLA_HDRLEN+2){uint16_t id;memcpy(&id,payload(a),2);family=id;}
    }
    if(!family)return 8;detected_family=family;printf("NL80211_FAMILY=%d\n",family);
    if(request(fd,family,NL80211_CMD_GET_WIPHY,1,0,NULL,0))return 9;
    int found=0,done=0;
    while(!done){
        length=receive(fd,buffer,sizeof(buffer));if(length<0){perror("wiphy receive");return 10;}
        for(struct nlmsghdr *n=(void*)buffer;NLMSG_OK(n,length);n=NLMSG_NEXT(n,length)){
            if(n->nlmsg_seq!=sequence)continue;
            if(n->nlmsg_type==NLMSG_DONE){done=1;break;}
            if(n->nlmsg_type==NLMSG_ERROR){struct nlmsgerr *e=NLMSG_DATA(n);printf("NETLINK_WIPHY_ERROR=%d\n",e->error);return 11;}
            if(n->nlmsg_type!=family||n->nlmsg_len<NLMSG_LENGTH(GENL_HDRLEN))continue;
            int left=n->nlmsg_len-NLMSG_HDRLEN-GENL_HDRLEN;uint32_t id=0;char phy[128]="unknown";int netns=0,scan=0,connect=0;
            for(const struct nlattr *a=(void*)((char*)NLMSG_DATA(n)+GENL_HDRLEN);attr_ok(a,left);a=next_attr(a,&left)){
                int type=a->nla_type&NLA_TYPE_MASK;
                if(type==NL80211_ATTR_WIPHY)id=u32(a);
                if(type==NL80211_ATTR_WIPHY_NAME){size_t size=a->nla_len-NLA_HDRLEN;if(size>=sizeof(phy))size=sizeof(phy)-1;memcpy(phy,payload(a),size);phy[size]=0;}
                if(type==NL80211_ATTR_SUPPORTED_COMMANDS){int remain=a->nla_len-NLA_HDRLEN;for(const struct nlattr *c=payload(a);attr_ok(c,remain);c=next_attr(c,&remain)){uint32_t command=u32(c);if(command==NL80211_CMD_SET_WIPHY_NETNS)netns=1;if(command==NL80211_CMD_TRIGGER_SCAN)scan=1;if(command==NL80211_CMD_CONNECT)connect=1;}}
            }
            printf("WIPHY_INDEX=%u NAME=%s NETNS_TRANSFER_ADVERTISED=%d SCAN=%d CONNECT=%d\n",id,phy,netns,scan,connect);if(id==0&&netns)netns_capable=1;found++;
        }
    }
    close(fd);puts("READ_ONLY_NO_WIFI_OR_NAMESPACE_CHANGES");return found?0:12;
}
static int move_phy(int target){
    int fd=socket(AF_NETLINK,SOCK_RAW|SOCK_CLOEXEC,NETLINK_GENERIC);if(fd<0)return -1;
    struct sockaddr_nl local={.nl_family=AF_NETLINK};if(bind(fd,(void*)&local,sizeof(local))){close(fd);return -1;}
    char buffer[256]={0};struct nlmsghdr *n=(void*)buffer;n->nlmsg_len=NLMSG_LENGTH(GENL_HDRLEN);
    n->nlmsg_type=detected_family;n->nlmsg_flags=NLM_F_REQUEST|NLM_F_ACK;n->nlmsg_seq=++sequence;
    struct genlmsghdr *g=NLMSG_DATA(n);g->cmd=NL80211_CMD_SET_WIPHY_NETNS;g->version=1;
    int types[]={NL80211_ATTR_WIPHY,NL80211_ATTR_NETNS_FD};uint32_t values[]={0,(uint32_t)target};
    for(int i=0;i<2;i++){struct nlattr *a=(void*)(buffer+NLMSG_ALIGN(n->nlmsg_len));a->nla_type=types[i];a->nla_len=NLA_HDRLEN+4;memcpy((char*)a+NLA_HDRLEN,&values[i],4);n->nlmsg_len=NLMSG_ALIGN(n->nlmsg_len)+NLA_ALIGN(a->nla_len);}
    struct sockaddr_nl kernel={.nl_family=AF_NETLINK};if(sendto(fd,buffer,n->nlmsg_len,0,(void*)&kernel,sizeof(kernel))!=(ssize_t)n->nlmsg_len){close(fd);return -1;}
    int length=receive(fd,buffer,sizeof(buffer));close(fd);if(length<0)return -1;
    for(n=(void*)buffer;NLMSG_OK(n,length);n=NLMSG_NEXT(n,length))if(n->nlmsg_seq==sequence&&n->nlmsg_type==NLMSG_ERROR&&n->nlmsg_len>=NLMSG_LENGTH(sizeof(struct nlmsgerr))){struct nlmsgerr *e=NLMSG_DATA(n);if(e->error){errno=-e->error;return -1;}return 0;}
    errno=EPROTO;return -1;
}
static int interfaces_down(void){
    int fd=socket(AF_INET,SOCK_DGRAM|SOCK_CLOEXEC,0);if(fd<0)return -1;
    const char *names[]={"wlan0","p2p0","wifi-aware0"};
    for(unsigned i=0;i<3;i++){struct ifreq req={0};strcpy(req.ifr_name,names[i]);if(ioctl(fd,SIOCGIFFLAGS,&req)){if(errno==ENODEV)continue;close(fd);return -1;}if(req.ifr_flags&IFF_UP){printf("REFUSE_UP_INTERFACE=%s\n",names[i]);close(fd);return -1;}}
    close(fd);return 0;
}
static int roundtrip(void){
    if(getuid()!=0||!netns_capable||interfaces_down())return 20;
    int host=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);if(host<0)return 21;
    int ready[2],hold[2],guard_pipe[2];if(pipe2(ready,O_CLOEXEC)||pipe2(hold,O_CLOEXEC)||pipe2(guard_pipe,O_CLOEXEC))return 22;
    pid_t holder=fork();if(holder<0)return 23;
    if(!holder){
        close(ready[0]);close(hold[1]);close(guard_pipe[0]);close(guard_pipe[1]);
        if(unshare(CLONE_NEWNET))_exit(31);char mark='R';if(write(ready[1],&mark,1)!=1)_exit(32);close(ready[1]);
        while(read(hold[0],&mark,1)<0&&errno==EINTR){}close(hold[0]);_exit(0);
    }
    close(ready[1]);close(hold[0]);
    struct pollfd r={.fd=ready[0],.events=POLLIN};char mark=0;
    if(poll(&r,1,3000)!=1||read(ready[0],&mark,1)!=1||mark!='R'){close(hold[1]);kill(holder,SIGKILL);waitpid(holder,NULL,0);return 24;}close(ready[0]);
    char path[128];snprintf(path,sizeof(path),"/proc/%d/ns/net",holder);int guest=open(path,O_RDONLY|O_CLOEXEC);
    if(guest<0){close(hold[1]);waitpid(holder,NULL,0);return 25;}
    pid_t guard=fork();if(guard<0){close(hold[1]);waitpid(holder,NULL,0);return 26;}
    if(!guard){
        close(guard_pipe[1]);close(hold[1]);struct pollfd watch={.fd=guard_pipe[0],.events=POLLIN|POLLHUP};
        int status=poll(&watch,1,20000);char done=0;
        if(status>0&&read(guard_pipe[0],&done,1)==1&&done=='D')_exit(0);
        puts("WIFI_NETNS_EMERGENCY_ROLLBACK");
        if(setns(guest,CLONE_NEWNET))_exit(41);
        int restored=move_phy(host);if(restored&&errno!=ENODEV){perror("emergency PHY restore");_exit(42);}
        _exit(0);
    }
    close(guard_pipe[0]);int result=0,moved=0,entered=0;
    if(move_phy(guest)){perror("move PHY into temporary network namespace");result=27;}
    else {moved=1;puts("WIFI_PHY_TRANSFERRED_TO_TEMPORARY_NAMESPACE");}
    if(moved){
        if(setns(guest,CLONE_NEWNET)){perror("enter temporary network namespace");result=28;}
        else{entered=1;printf("TEMPORARY_NETNS_WLAN0_INDEX=%u\n",if_nametoindex("wlan0"));if(!if_nametoindex("wlan0"))result=29;else{int check=inventory();if(check)result=30;}sleep(3);}
        if(!entered&&setns(guest,CLONE_NEWNET)){perror("enter namespace for rollback");result=34;}
        else if(move_phy(host)){perror("restore PHY to original namespace");result=35;}
        else{puts("WIFI_PHY_EXPLICITLY_RESTORED_TO_ORIGINAL_NAMESPACE");moved=0;}
        if(setns(host,CLONE_NEWNET)){perror("restore caller namespace");result=36;}
    }
    if(!moved){mark='D';if(write(guard_pipe[1],&mark,1)!=1)result=37;}
    close(guard_pipe[1]);int guard_status;waitpid(guard,&guard_status,0);
    if(!WIFEXITED(guard_status)||WEXITSTATUS(guard_status))result=38;
    close(hold[1]);waitpid(holder,NULL,0);close(guest);close(host);
    if(moved&&setns(open("/proc/1/ns/net",O_RDONLY|O_CLOEXEC),CLONE_NEWNET))result=39;
    printf("HOST_NETNS_WLAN0_INDEX=%u\n",if_nametoindex("wlan0"));
    if(!if_nametoindex("wlan0"))result=40;
    printf("WIFI_NETNS_ROUNDTRIP_RESULT=%d\n",result);return result;
}
int main(int argc,char **argv){
    if(argc==4&&!strcmp(argv[1],"--move-fds")){
        if(getuid()!=0)return 2;
        char *end;long source=strtol(argv[2],&end,10);if(*end||source<3||source>65535)return 2;
        long target=strtol(argv[3],&end,10);if(*end||target<3||target>65535||source==target)return 2;
        if(setns((int)source,CLONE_NEWNET)){perror("guardian source namespace");return 45;}
        int result=inventory();if(result)return result;
        if(move_phy((int)target)){perror("guardian PHY handoff");return 46;}
        puts("GUARDIAN_WIFI_PHY_HANDOFF_OK");return 0;
    }
    if(argc!=2||(strcmp(argv[1],"--inventory")&&strcmp(argv[1],"--roundtrip-3s")))return 2;
    signal(SIGPIPE,SIG_IGN);
    int result=inventory();if(result||!strcmp(argv[1],"--inventory"))return result;
    return roundtrip();
}
