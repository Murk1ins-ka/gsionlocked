// Explicitly locate the readonly SELinux mount for libselinux. No policy changes.
#include <dlfcn.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/mount.h>
#include <sys/stat.h>
int mount(const char *source,const char *target,const char *filesystem,unsigned long flags,const void *data){
    int (*original)(const char*,const char*,const char*,unsigned long,const void*)=dlsym(RTLD_NEXT,"mount");
    int result=original(source,target,filesystem,flags,data);int error=errno;
    if(result<0&&target&&(!strncmp(target,"/data/",6)||!strncmp(target,"/data_mirror",12))){
        int (*logprint)(int,const char*,const char*,...)=dlsym(RTLD_DEFAULT,"__android_log_print");
        if(logprint){struct stat point,parent,user_zero;int point_result=lstat(target,&point),parent_result=stat("/data",&parent),zero_result=lstat("/data/user/0",&user_zero);
            logprint(6,"TempGsiMount","FAILED source=%s target=%s fs=%s errno=%d target_stat=%d parent_stat=%d user0_stat=%d user0_mode=%o",source?source:"NULL",target,filesystem?filesystem:"NULL",error,point_result,parent_result,zero_result,zero_result?0:(unsigned)user_zero.st_mode);}
    }
    errno=error;return result;
}
int socket(int domain,int type,int protocol){
    int (*original)(int,int,int)=dlsym(RTLD_NEXT,"socket");int result=original(domain,type,protocol);int error=errno;
    if(!strcmp(getprogname(),"netd")&&result<0)fprintf(stderr,"PRIVATE_NETD_SOCKET_FAILURE family=%d type=%d protocol=%d errno=%d\n",domain,type,protocol,error);
    errno=error;return result;
}
int socketpair(int domain,int type,int protocol,int sockets[2]){
    int (*original)(int,int,int,int*)=dlsym(RTLD_NEXT,"socketpair");int result=original(domain,type,protocol,sockets);
    if(!result){Dl_info caller={0};dladdr(__builtin_return_address(0),&caller);fprintf(stderr,"PRIVATE_SOCKETPAIR fds=%d,%d caller=%s:%s\n",sockets[0],sockets[1],caller.dli_fname?caller.dli_fname:"?",caller.dli_sname?caller.dli_sname:"?");}
    return result;
}
int getsockname(int fd,struct sockaddr *address,socklen_t *size){
    int (*original)(int,struct sockaddr*,socklen_t*)=dlsym(RTLD_NEXT,"getsockname");
    int result=original(fd,address,size);
    if(!result&&address->sa_family==AF_UNIX&&*size<=2){
        struct sockaddr_un peer={0};socklen_t peer_size=sizeof(peer);int type=0;socklen_t type_size=sizeof(type);
        int found=getpeername(fd,(struct sockaddr*)&peer,&peer_size);getsockopt(fd,SOL_SOCKET,SO_TYPE,&type,&type_size);
        fprintf(stderr,"PRIVATE_UNNAMED_SOCKET fd=%d type=%d peer_result=%d peer=%s%s\n",fd,type,found,peer.sun_path[0]?"":"ABSTRACT:",peer.sun_path+(peer.sun_path[0]?0:1));
        struct ucred credentials={0};socklen_t credential_size=sizeof(credentials);getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&credentials,&credential_size);
        fprintf(stderr,"PRIVATE_SOCKET_PEER pid=%d uid=%d self=%d\n",credentials.pid,credentials.uid,getpid());
        char filename[128],line[1024];snprintf(filename,sizeof(filename),"/proc/%d/comm",credentials.pid);FILE *file=fopen(filename,"r");if(file){if(fgets(line,sizeof(line),file))fprintf(stderr,"PRIVATE_SOCKET_PEER_COMM=%s",line);fclose(file);}
        file=fopen("/proc/self/maps","r");if(file){while(fgets(line,sizeof(line),file))if(strstr(line,"zygisk")||strstr(line,"riru")||strstr(line,"memfd:"))fprintf(stderr,"PRIVATE_MAP %s",line);fclose(file);}
    }
    return result;
}
__attribute__((constructor)) static void locate_selinux(void){
    void *library=dlopen("libselinux.so",RTLD_NOW|RTLD_NOLOAD);
    void (*set_mount)(const char*)=library?dlsym(library,"set_selinuxmnt"):0;
    // LD_PRELOAD is inherited by tools such as iptables. A process that does
    // not link libselinux has no mount lookup to repair and must run normally.
    if(!set_mount)return;
    set_mount("/sys/fs/selinux");
    if(!strcmp(getprogname(),"netd")){
        for(int family=AF_INET;family<=AF_INET6;family+=AF_INET6-AF_INET){errno=0;int fd=socket(family,SOCK_DGRAM|SOCK_CLOEXEC,0);int error=errno;fprintf(stderr,"PRIVATE_NETD_SOCKET_PROBE family=%d fd=%d errno=%d\n",family,fd,error);if(fd>=0)close(fd);}
        FILE *status=fopen("/proc/self/status","r");char line[256];if(status){while(fgets(line,sizeof(line),status))if(!strncmp(line,"Cap",3))fprintf(stderr,"PRIVATE_NETD_%s",line);fclose(status);}
    }
}
