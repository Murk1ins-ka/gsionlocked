// SPDX-License-Identifier: Apache-2.0
// Apply one generated stock HAL identity in a newly forked guest child only.
#ifndef TEMPGSI_HAL_IDENTITY151_H
#define TEMPGSI_HAL_IDENTITY151_H
#include <errno.h>
#include <grp.h>
#include <linux/capability.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>

struct hal_identity151 {
    const char *name, *binary, *context;
    uid_t uid;
    gid_t groups[16]; // init: primary group first, then supplemental groups
    size_t group_count;
    uint64_t caps;
    int rtprio;
};

static inline int hal_identity151_apply(const struct hal_identity151 *spec) {
    if (!spec || !spec->group_count || spec->group_count > 16 || getuid()) {
        errno = EINVAL;
        return -1;
    }
    // Do not apply init task_profiles/writepid to the shared host's cgroups.
    // Their guest-owned equivalent is a separate lifecycle integration step.
    if (spec->rtprio >= 0) {
        struct rlimit limit = {(rlim_t)spec->rtprio, (rlim_t)spec->rtprio};
        if (setrlimit(RLIMIT_RTPRIO, &limit)) return -1;
    }
    if (prctl(PR_SET_KEEPCAPS, 1, 0, 0, 0) ||
        setgroups(spec->group_count-1, spec->groups+1) ||
        setresgid(spec->groups[0], spec->groups[0], spec->groups[0]) ||
        setresuid(spec->uid, spec->uid, spec->uid)) return -1;
    struct __user_cap_header_struct header = {_LINUX_CAPABILITY_VERSION_3, 0};
    struct __user_cap_data_struct data[2] = {{0}};
    data[0].effective = data[0].permitted = data[0].inheritable = (uint32_t)spec->caps;
    data[1].effective = data[1].permitted = data[1].inheritable = (uint32_t)(spec->caps >> 32);
    if (syscall(SYS_capset, &header, data)) return -1;
    if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0)) return -1;
    // Android init also preserves the declared capabilities across exec.
    for (unsigned bit = 0; bit < 64; bit++) {
        if ((spec->caps & (UINT64_C(1) << bit)) &&
            prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, bit, 0, 0)) return -1;
    }
    return prctl(PR_SET_KEEPCAPS, 0, 0, 0, 0);
}

static inline int hal_identity151_verify(const struct hal_identity151 *spec) {
    if (!spec || !spec->group_count || spec->group_count>16) return -1;
    gid_t actual[16];
    int count = getgroups(16, actual);
    if (getuid() != spec->uid || geteuid() != spec->uid ||
        getgid() != spec->groups[0] || getegid() != spec->groups[0] ||
        count != (int)spec->group_count-1) return -1;
    // The kernel sorts supplemental groups; the init configuration need not.
    for (size_t i = 1; i < spec->group_count; i++) {
        int found = 0;
        for (int j = 0; j < count; j++) if (actual[j] == spec->groups[i]) found = 1;
        if (!found) return -1;
    }
    struct __user_cap_header_struct header = {_LINUX_CAPABILITY_VERSION_3, 0};
    struct __user_cap_data_struct data[2] = {{0}};
    if (syscall(SYS_capget, &header, data)) return -1;
    uint64_t effective = data[0].effective | ((uint64_t)data[1].effective << 32);
    uint64_t permitted = data[0].permitted | ((uint64_t)data[1].permitted << 32);
    uint64_t inheritable = data[0].inheritable | ((uint64_t)data[1].inheritable << 32);
    if (effective != spec->caps || permitted != spec->caps || inheritable != spec->caps) return -1;
    for (unsigned bit = 0; bit <= CAP_LAST_CAP; bit++) {
        int ambient = prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_IS_SET, bit, 0, 0);
        if (ambient < 0 || !!ambient != !!(spec->caps & (UINT64_C(1) << bit))) return -1;
    }
    return 0;
}
#endif
