// SPDX-License-Identifier: Apache-2.0
// Private mount namespace, tmpfs only; no host mount propagation.
#define _GNU_SOURCE
#include <sched.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
static void fail(const char*s){perror(s);exit(2);}
int main(void){
 if(getuid())return 1;
 if(unshare(CLONE_NEWNS)||mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL))fail("private namespace");
 char path[]="/data/local/tmp/storage-propagation198-XXXXXX";
 if(!mkdtemp(path)||mount("tmpfs",path,"tmpfs",MS_NOSUID|MS_NODEV,"size=1m")||chdir(path))fail("test root");
 if(mkdir("user",0700)||mkdir("installer",0700)||mkdir("storage",0700)||mkdir("user/emulated",0700))fail("test dirs");
 if(mount("user","user",NULL,MS_BIND,NULL)||mount(NULL,"user",NULL,MS_SHARED,NULL)||mount("user","installer",NULL,MS_BIND|MS_REC,NULL))fail("shared guest view");
 int channel[2];if(pipe(channel))fail("pipe");pid_t child=fork();if(child<0)fail("fork");
 if(!child){close(channel[1]);if(unshare(CLONE_NEWNS)||mount("installer","storage",NULL,MS_BIND|MS_REC,NULL))_exit(3);char ready;if(read(channel[0],&ready,1)!=1)_exit(4);struct stat st;_exit(stat("storage/emulated/mounted",&st)?5:0);}
 close(channel[0]);usleep(200000);
 if(mount("tmpfs","user/emulated","tmpfs",MS_NOSUID|MS_NODEV,"size=1m"))fail("simulated FUSE mount");
 int fd=open("user/emulated/mounted",O_CREAT|O_WRONLY,0600);if(fd<0)fail("proof file");close(fd);if(write(channel[1],"1",1)!=1)fail("ready");close(channel[1]);
 int result;if(waitpid(child,&result,0)!=child)fail("wait");if(!WIFEXITED(result)||WEXITSTATUS(result))return 6;
 puts("PASS: late guest storage mount reaches installer app namespace");return 0;
}
