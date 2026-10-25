/*
 * zygote_launcher: stands in for init when starting the guest's zygote.
 *
 * Drop-in replacement for OneLineage's zygote_exec, which does the same socket
 * setup but execs app_process64 with a hardcoded environment, including
 * LineageOS 23.2's BOOTCLASSPATH and SYSTEMSERVERCLASSPATH. That breaks any
 * other guest: ColorOS's zygote aborts in libhwui because oplus-framework.jar
 * is not on the boot class path. This launcher takes the class paths (and the
 * other runtime variables below) from its own environment, as exported by
 * guest_start.sh from the guest's derive_classpath, and only falls back to
 * the original defaults for variables that are not set.
 *
 * Like init's "socket zygote stream 660 root system" it creates the zygote and
 * usap pool sockets, passes them as fds 3 and 4, points stdio at /dev/null and
 * closes every other fd before the exec.
 *
 * -Xforcejitzygote (always passed by OneLineage's launcher, and by this one up
 * to run 44) only when ONEOS_GUEST_JIT_ZYGOTE=1, which guest_start.sh exports
 * (1 for ColorOS guests, 0 for the others). With it ART ignores the image's
 * AOT boot image, compiles the boot class path in memory at every zygote start
 * (dex2oat64 --compiler-filter=verify) and runs framework code JIT-compiled
 * only. ONEOS_GUEST_JIT_ZYGOTE itself is not passed on to zygote.
 */
#include <fcntl.h>
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

#define AID_SYSTEM 1000

/* A bind of an individual socket cannot survive an unlink in another mount
 * namespace sharing its parent directory. Give the guest its own directory
 * entries, and forward the other sockets to the live host directory. */
static int private_socket_directory(void) {
    const char *mirror = "/data/oneos_host_sockets";
    if (mkdir(mirror, 0755) && errno != EEXIST) return -1;
    if (mount("/dev/socket", mirror, NULL, MS_BIND, NULL)) return -1;
    if (mount("tmpfs", "/dev/socket", "tmpfs", MS_NOSUID | MS_NODEV,
              "mode=0755,size=1m")) return -1;
    DIR *dir = opendir(mirror);
    if (!dir) return -1;
    struct dirent *entry;
    int count = 0, result = 0;
    while ((entry = readdir(dir))) {
        const char *name = entry->d_name;
        if (!strcmp(name, ".") || !strcmp(name, "..") ||
            !strcmp(name, "zygote") || !strcmp(name, "usap_pool_primary")) continue;
        char source[512], target[512];
        snprintf(source, sizeof(source), "%s/%s", mirror, name);
        snprintf(target, sizeof(target), "/dev/socket/%s", name);
        if (symlink(source, target)) { result = -1; break; }
        count++;
    }
    closedir(dir);
    if (count < 10) result = -1;
    return result;
}

static void make_socket(const char *path, int target_fd) {
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) { perror("socket"); exit(1); }
    if (fd != target_fd) {
        if (dup3(fd, target_fd, 0) < 0) { perror("dup3"); exit(1); }
        close(fd);
    }
    unlink(path);
    strncpy(addr.sun_path, path, sizeof(addr.sun_path) - 1);
    if (bind(target_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) { perror(path); exit(1); }
    if (listen(target_fd, 10) < 0) { perror("listen"); exit(1); }
    chmod(path, 0660);
    chown(path, 0, AID_SYSTEM);
}

/* Variables taken from our environment when set, else these defaults. */
static const char *const kInherited[][2] = {
    {"PATH", "/system/bin:/system/xbin"},
    {"ANDROID_ROOT", "/system"},
    {"ANDROID_DATA", "/data"},
    {"ANDROID_STORAGE", "/storage"},
    {"ANDROID_ART_ROOT", "/apex/com.android.art"},
    {"ANDROID_I18N_ROOT", "/apex/com.android.i18n"},
    {"ANDROID_TZDATA_ROOT", "/apex/com.android.tzdata"},
    {"BOOTCLASSPATH", NULL},
    {"DEX2OATBOOTCLASSPATH", NULL},
    {"SYSTEMSERVERCLASSPATH", NULL},
    {"STANDALONE_SYSTEMSERVER_JARS", NULL},
    {"EXTERNAL_STORAGE", "/sdcard"},
    {"ASEC_MOUNTPOINT", "/mnt/asec"},
    {"ANDROID_ASSETS", "/system/app"},
};

int main(void) {
    static char *envp[32];
    int n = 0;

    setpgid(0, 0);

    /* The S23 host still manages its own zygote sockets. A shared /dev
     * pathname can disappear while our listener FD stays alive. Pin only
     * these two guest endpoints in the already-private guest namespace.
     * No socket or mount in init's namespace may be pinned here. */
    const char *profile = getenv("ONEOS_PROFILE");
    if (profile && strcmp(profile, "s23-8550-ossi37") == 0) {
        char self_ns[80], init_ns[80];
        ssize_t sa = readlink("/proc/self/ns/mnt", self_ns, sizeof(self_ns)-1);
        ssize_t ia = readlink("/proc/1/ns/mnt", init_ns, sizeof(init_ns)-1);
        if (sa < 0 || ia < 0) return 3;
        self_ns[sa] = 0; init_ns[ia] = 0;
        if (strcmp(self_ns, init_ns) == 0) {
            fprintf(stderr, "zygote_launcher: refusing socket pins in init namespace\n");
            return 4;
        }
        if (private_socket_directory()) {
            perror("zygote_launcher: private socket directory");
            return 5;
        }
        make_socket("/dev/socket/zygote", 3);
        make_socket("/dev/socket/usap_pool_primary", 4);
        fprintf(stderr, "zygote_launcher: private guest socket directory, host sockets forwarded\n");
    } else {
        make_socket("/dev/socket/zygote", 3);
        make_socket("/dev/socket/usap_pool_primary", 4);
    }

    envp[n++] = "ANDROID_SOCKET_zygote=3";
    envp[n++] = "ANDROID_SOCKET_usap_pool_primary=4";
    for (size_t i = 0; i < sizeof(kInherited) / sizeof(kInherited[0]); i++) {
        const char *name = kInherited[i][0];
        const char *value = getenv(name);
        if (!value || !*value) value = kInherited[i][1];
        if (!value) {
            fprintf(stderr, "zygote_launcher: %s is not set\n", name);
            continue;
        }
        size_t len = strlen(name) + strlen(value) + 2;
        char *entry = malloc(len);
        snprintf(entry, len, "%s=%s", name, value);
        envp[n++] = entry;
    }
    envp[n] = NULL;

    const char *jit = getenv("ONEOS_GUEST_JIT_ZYGOTE");
    int force_jit = jit && strcmp(jit, "1") == 0;
    /* Still on guest_start.sh's stdout and stderr (takeover.log). */
    fprintf(stderr, "zygote_launcher: %s (ONEOS_GUEST_JIT_ZYGOTE=%s)\n",
            force_jit ? "-Xforcejitzygote" : "the image's boot image, no -Xforcejitzygote",
            jit ? jit : "unset");

    int null_fd = open("/dev/null", O_RDWR);
    if (null_fd >= 0) {
        dup3(null_fd, 0, 0);
        dup3(null_fd, 1, 0);
        dup3(null_fd, 2, 0);
        if (null_fd > 4) close(null_fd);
    }
    for (int fd = 5; fd < 1024; fd++) close(fd);

    char *argv[8];
    int a = 0;
    argv[a++] = "zygote64";
    argv[a++] = "-Xzygote";
    if (force_jit) argv[a++] = "-Xforcejitzygote";
    argv[a++] = "/system/bin";
    argv[a++] = "--zygote";
    argv[a++] = "--start-system-server";
    argv[a++] = "--socket-name=zygote";
    argv[a] = NULL;
    execve("/system/bin/app_process64", argv, envp);
    perror("gsionlocked: exec app_process64");
    return 127;
}
