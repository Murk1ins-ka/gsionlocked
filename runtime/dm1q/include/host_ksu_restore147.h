/* KernelSU 3.2.5 UAPI v2 only. Preserve its syscall-hook marking when init
 * recreates the stock Zygotes. No kernel image/module/manager UID patches. */
struct ksu_init_lease147 {int fd,original,changed;};
struct ksu_mark147 {unsigned operation;int pid;unsigned result;};
static struct ksu_init_lease147 ksu_init_begin147(void){
    struct ksu_init_lease147 lease={.fd=-1,.original=0,.changed=0};
    int fd=-1;syscall(SYS_reboot,0xDEADBEEF,0xCAFEBABE,0,&fd);if(fd<0){puts("HOST_KSU147_INTERFACE_UNAVAILABLE");return lease;}
    struct {unsigned version,flags,features,uapi;} info={0};
    if(ioctl(fd,_IOR('K',2,info),&info)||info.version!=32525||info.uapi!=2){puts("HOST_KSU147_UNSUPPORTED_INTERFACE_NO_MARK_CHANGES");close(fd);return lease;}
    struct ksu_mark147 mark={.operation=1,.pid=1};
    if(ioctl(fd,_IOC(_IOC_READ|_IOC_WRITE,'K',16,0),&mark)||mark.result>1){close(fd);return lease;}
    lease.original=(int)mark.result;lease.fd=fd;
    if(!mark.result){mark.operation=2;if(ioctl(fd,_IOC(_IOC_READ|_IOC_WRITE,'K',16,0),&mark)){close(fd);lease.fd=-1;return lease;}lease.changed=1;}
    printf("HOST_KSU147_INIT_MARK_LEASE_ORIGINAL=%d_ACTIVE=1\n",lease.original);return lease;
}
static void ksu_init_end147(struct ksu_init_lease147 *lease){
    if(lease->fd<0)return;
    if(lease->changed){struct ksu_mark147 mark={.operation=3,.pid=1};int result=ioctl(lease->fd,_IOC(_IOC_READ|_IOC_WRITE,'K',16,0),&mark);printf("HOST_KSU147_ORIGINAL_INIT_MARK_RESTORED=%d\n",!result);}
    unsigned manager=0;if(!ioctl(lease->fd,_IOC(_IOC_READ,'K',10,0),&manager))printf("HOST_KSU147_MANAGER_UID_AFTER_RETURN=%u\n",manager);
    close(lease->fd);lease->fd=-1;
}
