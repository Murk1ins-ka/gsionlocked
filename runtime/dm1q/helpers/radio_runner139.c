// Run the original Samsung RIL with guest Binder devices and stock libraries.
// The host guardian exclusively owns stop/start and kills this exact child.
#define _GNU_SOURCE
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <linux/capability.h>
#include <linux/mount.h>
#include <sched.h>
#include <unistd.h>
#include <fcntl.h>
#include <grp.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include <signal.h>

static int descriptor(const char *value){char *end;long fd=strtol(value,&end,10);return *value&&!*end&&fd>2&&fd<INT_MAX?(int)fd:-1;}
static int private_device(int fd,const char *host){
    struct stat a,b;return fd>=0&&!fstat(fd,&a)&&!stat(host,&b)&&S_ISCHR(a.st_mode)&&S_ISCHR(b.st_mode)&&a.st_rdev!=b.st_rdev;
}
static int attach_device(int tree,const char *destination){
    char target[PATH_MAX];
    if(!realpath(destination,target))return -1;
    return syscall(SYS_move_mount,tree,"",AT_FDCWD,target,MOVE_MOUNT_F_EMPTY_PATH);
}
int main(int argc,char **argv){
    setbuf(stdout,NULL);
    pid_t guardian=getppid();
    if(getuid()!=0||(argc!=5&&argc!=6)||(strcmp(argv[1],"--check-fds")&&strcmp(argv[1],"--check-mounts")&&strcmp(argv[1],"--check-credentials")&&strcmp(argv[1],"--run-fds")))return 2;
    int binder=descriptor(argv[2]),hardware=descriptor(argv[3]);
    int guest_mounts=descriptor(argv[4]);
    if(!private_device(binder,"/dev/binder")||!private_device(hardware,"/dev/hwbinder")){puts("RADIO_REFUSE_HOST_OR_INVALID_BINDER");return 3;}
    struct stat a,b;if(fstat(binder,&a)||fstat(hardware,&b)||a.st_rdev==b.st_rdev)return 3;
    if(!strcmp(argv[1],"--check-fds")){puts("RADIO_PRIVATE_BINDER_FDS_VALID_NO_CHANGES");return 0;}
    puts("RADIO_RUNNER_VALIDATED_PRIVATE_DEVICES");
    // No chroot or guest network join: Samsung libraries, QMI/QRTR and radio
    // data paths remain the originals. Only this child's Binder mounts change.
    if(guest_mounts<0||unshare(CLONE_NEWNS)||mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL)){perror("RIL clone host mounts");return 4;}
    int host_mounts=open("/proc/self/ns/mnt",O_RDONLY|O_CLOEXEC),host_root=open("/",O_PATH|O_DIRECTORY|O_CLOEXEC);
    if(host_mounts<0||host_root<0||setns(guest_mounts,CLONE_NEWNS)){perror("RIL source mount namespace");return 4;}
    int binder_tree=syscall(SYS_open_tree,binder,"",AT_EMPTY_PATH|OPEN_TREE_CLONE|OPEN_TREE_CLOEXEC);
    int hardware_tree=syscall(SYS_open_tree,hardware,"",AT_EMPTY_PATH|OPEN_TREE_CLONE|OPEN_TREE_CLOEXEC);
    if(binder_tree<0||hardware_tree<0){perror("RIL clone private Binder trees");return 4;}
    if(setns(host_mounts,CLONE_NEWNS)||fchdir(host_root)||chroot(".")||chdir("/")){perror("RIL restore stock filesystem view");return 4;}
    close(host_mounts);close(host_root);close(guest_mounts);
    if(attach_device(binder_tree,"/dev/binder")||attach_device(hardware_tree,"/dev/hwbinder")){perror("RIL attach cloned Binder trees");return 4;}
    close(binder_tree);close(hardware_tree);
    close(binder);if(hardware!=binder)close(hardware);
    puts("RADIO_RUNNER_PRIVATE_MOUNTS_READY");
    if(!strcmp(argv[1],"--check-mounts")){puts("RADIO_MOUNT_PREFLIGHT_OK_NO_RIL_EXEC_NO_HOST_MOUNT_CHANGES");return 0;}
    if(argc==6){
        int network=descriptor(argv[5]),host_network=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);struct stat host_ns,guest_ns;
        if(network<0||host_network<0||fstat(host_network,&host_ns)||fstat(network,&guest_ns)||(host_ns.st_dev==guest_ns.st_dev&&host_ns.st_ino==guest_ns.st_ino)||setns(network,CLONE_NEWNET))return 10;
        close(host_network);close(network);puts("RADIO_RUNNER_PRIVATE_MODEM_NETWORK_SELECTED143");
        // sysfs captures its network namespace at mount time; the copied host
        // mount would hide the modem links after they move into the guest.
        if(mount("sysfs","/sys","sysfs",MS_NOSUID|MS_NODEV|MS_NOEXEC,NULL))return 11;
    }
    const char context[]="u:r:rild:s0";
    int exec_context=open("/proc/self/attr/exec",O_WRONLY|O_CLOEXEC);
    if(exec_context<0||write(exec_context,context,sizeof(context)-1)!=(ssize_t)sizeof(context)-1)return 5;
    close(exec_context);
    puts("RADIO_RUNNER_EXEC_DOMAIN_PREPARED");
    gid_t groups[]={1000,1005,1007,1016,1026,2001,2901,3003,3005,3009,3010,9998};
    if(prctl(PR_SET_KEEPCAPS,1)||setgroups(sizeof(groups)/sizeof(groups[0]),groups)||setgid(1001)||setuid(1001))return 6;
    puts("RADIO_RUNNER_STOCK_UID_GROUPS_READY");
    struct __user_cap_header_struct header={.version=_LINUX_CAPABILITY_VERSION_3,.pid=0};
    struct __user_cap_data_struct caps[2]={{0},{0}};
    unsigned required[]={CAP_BLOCK_SUSPEND,CAP_NET_ADMIN,CAP_NET_RAW,CAP_SYS_ADMIN,CAP_CHOWN,CAP_SYS_TIME};
    for(unsigned i=0;i<sizeof(required)/sizeof(required[0]);i++){unsigned n=required[i];caps[n/32].permitted|=1U<<(n%32);caps[n/32].effective|=1U<<(n%32);caps[n/32].inheritable|=1U<<(n%32);}
    if(syscall(SYS_capset,&header,caps))return 7;
    for(unsigned i=0;i<sizeof(required)/sizeof(required[0]);i++)if(prctl(PR_CAP_AMBIENT,PR_CAP_AMBIENT_RAISE,required[i],0,0))return 7;
    // setuid clears PDEATHSIG; re-arm it after the credential transition.
    if(guardian<=1||prctl(PR_SET_PDEATHSIG,SIGKILL)||getppid()!=guardian)return 8;
    puts("RADIO_RUNNER_CAPABILITIES_READY_ORIGINAL_RILD_EXEC_NEXT");
    if(!strcmp(argv[1],"--check-credentials")){puts("RADIO_CREDENTIAL_PREFLIGHT_OK_NO_RIL_EXEC");return 0;}
    for(int fd=3;fd<4096;fd++)close(fd);
    char *args[]={"/vendor/bin/hw/rild",NULL};
    char *env[]={"PATH=/vendor/bin:/system/bin","ANDROID_ROOT=/system","ANDROID_DATA=/data",NULL};
    // The original RIL normally has null stdio. Keep identity-bearing vendor
    // logging out of the guardian log, while retaining runner setup checkpoints.
    int null=open("/dev/null",O_RDWR);if(null<0)return 9;
    for(int fd=0;fd<3;fd++)if(dup2(null,fd)<0)return 9;if(null>2)close(null);
    execve(args[0],args,env);perror("original Samsung RIL exec");return 127;
}
