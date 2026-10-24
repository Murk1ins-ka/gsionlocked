/* Only this utility gets a private proc/1 view. Forced init-namespace
 * switches resolve to the live, validated GUEST supervisor instead. */
#include <sys/stat.h>
#include <sys/mount.h>
#include <sched.h>
#include <fcntl.h>
static int guest_pid1_view145(void){
    struct stat root,host,own_binder,anchor_root,anchor_binder,expected;
    if(getuid()!=0||stat("/",&root)||stat("/proc/1/root",&host)||
       (root.st_dev==host.st_dev&&root.st_ino==host.st_ino)||
       stat("/dev/binder",&own_binder))return -1;
    FILE *marker=fopen("/data/local/tmp/guest-root-anchor145.pid","r");long anchor=0;
    if(!marker)return -1;int parsed=fscanf(marker,"%ld",&anchor);fclose(marker);if(parsed!=1||anchor<=1||anchor==getpid())return -1;
    char path[128],source[128];snprintf(path,sizeof(path),"/proc/%ld/root",anchor);
    if(stat(path,&anchor_root)||root.st_dev!=anchor_root.st_dev||root.st_ino!=anchor_root.st_ino)return -1;
    snprintf(path,sizeof(path),"/proc/%ld/root/dev/binder",anchor);
    if(stat(path,&anchor_binder)||own_binder.st_rdev!=anchor_binder.st_rdev)return -1;
    snprintf(source,sizeof(source),"/proc/%ld/ns/mnt",anchor);if(stat(source,&expected))return -1;
    if(unshare(CLONE_NEWNS)||mount(NULL,"/",NULL,MS_PRIVATE|MS_REC,NULL))return -1;
    if(mount("tmpfs","/proc/1","tmpfs",MS_NOSUID|MS_NODEV|MS_NOEXEC,"size=32k,mode=0555"))return -1;
    if(mkdir("/proc/1/ns",0555)||symlink(source,"/proc/1/ns/mnt")||symlink("/","/proc/1/root"))return -1;
    struct stat view;return stat("/proc/1/ns/mnt",&view)||expected.st_ino!=view.st_ino?-1:0;
}
