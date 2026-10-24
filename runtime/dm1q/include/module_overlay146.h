/* Only called after CLONE_NEWNS/MS_PRIVATE and private guest ext4 is mounted.
 * Merge installed static system/ payloads into bounded tmpfs, never host mounts.
 */
static unsigned long long module_bytes146;
static int module_merge146(const char *source,const char *target,int depth){
    if(depth>16)return -1;
    DIR *dir=opendir(source);if(!dir)return -1;int result=0;struct dirent *entry;
    while(!result&&(entry=readdir(dir))){
        if(!strcmp(entry->d_name,".")||!strcmp(entry->d_name,".."))continue;
        char a[4096],b[4096];struct stat st,dst;
        if(snprintf(a,sizeof(a),"%s/%s",source,entry->d_name)>=(int)sizeof(a)||snprintf(b,sizeof(b),"%s/%s",target,entry->d_name)>=(int)sizeof(b)||lstat(a,&st)){result=-1;break;}
        if(!lstat(b,&dst)&&(!S_ISREG(dst.st_mode)&&!S_ISDIR(dst.st_mode))){result=-1;break;}
        if(S_ISDIR(st.st_mode)){
            if((mkdir(b,0755)&&errno!=EEXIST)||lstat(b,&dst)||!S_ISDIR(dst.st_mode)||module_merge146(a,b,depth+1))result=-1;
        }else if(S_ISREG(st.st_mode)&&st.st_size>=0&&st.st_size<=64*1024*1024){
            module_bytes146+=(unsigned long long)st.st_size;if(module_bytes146>384ULL*1024*1024){result=-1;break;}
            int in=open(a,O_RDONLY|O_CLOEXEC|O_NOFOLLOW),out=open(b,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,(st.st_mode&0111)?0755:0644);
            if(in<0||out<0)result=-1;
            char buffer[32768];ssize_t n=0;
            if(!result)while((n=read(in,buffer,sizeof(buffer)))>0){ssize_t offset=0;while(offset<n){ssize_t written=write(out,buffer+offset,n-offset);if(written<=0){result=-1;break;}offset+=written;}if(result)break;}
            if(!result&&(n<0||fchmod(out,(st.st_mode&0111)?0755:0644)))result=-1;
            if(in>=0)close(in);if(out>=0)close(out);
        }else result=-1;
    }closedir(dir);return result;
}
static int module_overlay146(const char *guest_root){
    const char *scratch="/data/local/tmp/tempgsi-userspace/modules-work146";
    if(mkdir(scratch,0700)&&errno!=EEXIST)return -1;
    if(mount("tmpfs",scratch,"tmpfs",MS_NOSUID|MS_NODEV,"size=400m,mode=0755"))return -1;
    char merged[4096],store[4096],path[4096];
    snprintf(merged,sizeof(merged),"%s/merged",scratch);if(mkdir(merged,0755))return -1;
    module_bytes146=0;
    // Retain the already installed Pixel file pack, unless disabled in TempGSI.
    if(access("/data/local/tmp/tempgsi-control/pixel145.disabled",F_OK)&&!access("/data/local/tmp/tempgsi-pixel145/system",R_OK)){
        if(module_merge146("/data/local/tmp/tempgsi-pixel145/system",merged,0))return -1;
    }
    snprintf(path,sizeof(path),"%s/active",scratch);FILE *active=fopen(path,"w");if(!active)return -1;
    snprintf(store,sizeof(store),"%s/data/tempgsi-modules146",guest_root);
    struct stat st;if(!lstat(store,&st)&&(!S_ISDIR(st.st_mode)||st.st_uid!=0)){fclose(active);return -1;}
    struct dirent **modules=NULL;int count=access("/data/local/tmp/tempgsi-control/modules146.disabled",F_OK)?scandir(store,&modules,NULL,alphasort):0,result=0,accepted=0;
    if(count<0&&errno!=ENOENT){fclose(active);return -1;}
    for(int i=0;i<count;i++){
        const char *id=modules[i]->d_name;if(id[0]=='.'){free(modules[i]);continue;}
        if(++accepted>16||strlen(id)>64||!strchr("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",id[0])||strspn(id,"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-")!=strlen(id))result=-1;
        char module[8192],payload[16384],disabled[16384],meta[16384];snprintf(module,sizeof(module),"%s/%s",store,id);
        snprintf(disabled,sizeof(disabled),"%s/disable",module);snprintf(payload,sizeof(payload),"%s/system",module);snprintf(meta,sizeof(meta),"%s/module.prop",module);
        if(!result&&(lstat(module,&st)||!S_ISDIR(st.st_mode)||st.st_uid!=0))result=-1;
        if(!result&&access(disabled,F_OK)){
            char revision[80]="",line[1024];FILE *prop=fopen(meta,"r");if(!prop)result=-1;
            else {while(fgets(line,sizeof(line),prop))if(!strncmp(line,"tempgsi.revision=",17)){snprintf(revision,sizeof(revision),"%.79s",line+17);revision[strcspn(revision,"\r\n")]=0;}fclose(prop);}
            if(!result&&(strlen(revision)!=36||strspn(revision,"0123456789abcdef-")!=36||module_merge146(payload,merged,0)))result=-1;
            if(!result){fprintf(active,"%s %s\n",id,revision);printf("PRIVATE_MODULE146_PREPARED=%s\n",id);}
        }
        free(modules[i]);
    }free(modules);if(fclose(active))result=-1;if(result)return -1;
    struct dirent **branches=NULL;int size=scandir(merged,&branches,NULL,alphasort);if(size<0)return -1;
    for(int i=0;i<size;i++){
        const char *branch=branches[i]->d_name;if(branch[0]=='.'){free(branches[i]);continue;}
        char target[4096],canonical[4096],upper[8192],work[4096],options[16384];
        snprintf(target,sizeof(target),"%s/system/%s",guest_root,branch);
        if(!realpath(target,canonical)||strncmp(canonical,guest_root,strlen(guest_root))||canonical[strlen(guest_root)]!='/'||lstat(canonical,&st)||(!S_ISDIR(st.st_mode)&&!S_ISREG(st.st_mode)))result=-1;
        snprintf(upper,sizeof(upper),"%s/%s",merged,branch);snprintf(work,sizeof(work),"%s/work%d",scratch,i);
        if(!result&&lstat(upper,&st))result=-1;
        if(!result&&S_ISREG(st.st_mode)){
            struct stat target_st;if(lstat(canonical,&target_st)||!S_ISREG(target_st.st_mode)||mount(upper,canonical,NULL,MS_BIND,NULL)||mount(NULL,canonical,NULL,MS_BIND|MS_REMOUNT|MS_RDONLY|MS_NOSUID|MS_NODEV,NULL))result=-1;
        }else if(!result&&S_ISDIR(st.st_mode)){
            struct stat target_st;if(lstat(canonical,&target_st)||!S_ISDIR(target_st.st_mode)||mkdir(work,0755))result=-1;
            if(!result){snprintf(options,sizeof(options),"lowerdir=%s,upperdir=%s,workdir=%s",canonical,upper,work);if(mount("overlay",canonical,"overlay",MS_RDONLY|MS_NOSUID|MS_NODEV,options))result=-1;}
        }else if(!result)result=-1;
        if(!result)printf("PRIVATE_MODULE146_SYSTEM_OVERLAY=%s\n",branch);free(branches[i]);
    }free(branches);return result;
}
