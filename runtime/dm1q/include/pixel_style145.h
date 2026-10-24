/* Sparse cosmetic files only, mounted after CLONE_NEWNS + MS_PRIVATE.
 * Never executes third-party installer/service scripts or mounts on host /system.
 * Each leaf keeps the original GSI directory as its lower layer.
 */
static int pixel_copy145(const char *source,const char *target,int depth){
    if(depth>12)return -1;
    DIR *dir=opendir(source);if(!dir)return -1;
    int result=0;struct dirent *entry;
    while(!result&&(entry=readdir(dir))){
        if(!strcmp(entry->d_name,".")||!strcmp(entry->d_name,".."))continue;
        char a[2048],b[2048];struct stat st;
        if(snprintf(a,sizeof(a),"%s/%s",source,entry->d_name)>=(int)sizeof(a)||snprintf(b,sizeof(b),"%s/%s",target,entry->d_name)>=(int)sizeof(b)||lstat(a,&st)){result=-1;break;}
        if(S_ISDIR(st.st_mode)){
            if(mkdir(b,0755)||pixel_copy145(a,b,depth+1))result=-1;
        }else if(S_ISREG(st.st_mode)&&st.st_size>=0&&st.st_size<=32*1024*1024){
            int in=open(a,O_RDONLY|O_CLOEXEC|O_NOFOLLOW),out=open(b,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC|O_NOFOLLOW,0644);
            if(in<0||out<0)result=-1;
            char buffer[32768];ssize_t n;
            if(!result)while((n=read(in,buffer,sizeof(buffer)))>0){
                ssize_t offset=0;while(offset<n){ssize_t written=write(out,buffer+offset,n-offset);if(written<=0){result=-1;break;}offset+=written;}
                if(result)break;
            }
            if(!result&&n<0)result=-1;
            if(in>=0)close(in);if(out>=0)close(out);
        }else result=-1;
    }
    closedir(dir);return result;
}
static int pixel_style145(const char *guest_root){
    const char *pack="/data/local/tmp/tempgsi-pixel145";
    const char *scratch="/data/local/tmp/tempgsi-userspace/pixel-work145";
    if(!access("/data/local/tmp/tempgsi-control/pixel145.disabled",F_OK)||access(pack,R_OK)){puts("PRIVATE_PIXEL_STYLE145_DISABLED_OR_ABSENT");return 0;}
    if(mkdir(scratch,0700)&&errno!=EEXIST)return -1;
    if(mount("tmpfs",scratch,"tmpfs",MS_NOSUID|MS_NODEV,"size=64m,mode=0755"))return -1;
    const char *leaves[]={"/system/fonts","/system/product/fonts","/system/product/media","/system/media/audio/ui","/system/product/etc/sysconfig"};
    for(unsigned i=0;i<sizeof(leaves)/sizeof(leaves[0]);i++){
        char source[1024],target[1024],upper[1024],work[1024],canonical[4096],options[7168];struct stat st;
        snprintf(source,sizeof(source),"%s%s",pack,leaves[i]);if(stat(source,&st)||!S_ISDIR(st.st_mode))continue;
        snprintf(target,sizeof(target),"%s%s",guest_root,leaves[i]);
        if(!realpath(target,canonical)||stat(canonical,&st)||!S_ISDIR(st.st_mode)){printf("PRIVATE_PIXEL_STYLE145_MISSING_TARGET=%s\n",leaves[i]);continue;}
        // realpath must stay within the freshly mounted GSI root.
        size_t root_length=strlen(guest_root);
        if(strncmp(canonical,guest_root,root_length)||canonical[root_length]!='/')return -1;
        snprintf(upper,sizeof(upper),"%s/upper%u",scratch,i);snprintf(work,sizeof(work),"%s/work%u",scratch,i);
        if(mkdir(upper,0755)||mkdir(work,0755)||pixel_copy145(source,upper,0))return -1;
        snprintf(options,sizeof(options),"lowerdir=%s,upperdir=%s,workdir=%s",canonical,upper,work);
        if(mount("overlay",canonical,"overlay",MS_RDONLY|MS_NOSUID|MS_NODEV,options))return -1;
        printf("PRIVATE_PIXEL_STYLE145_MOUNTED=%s\n",leaves[i]);
    }
    return 0;
}
