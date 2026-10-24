// SPDX-License-Identifier: Apache-2.0
// Run as root on the target kernel. Uses only a new, unpinned test map.
#define _GNU_SOURCE
#include "background_uid_lease.h"

int main(void) {
    if (getuid()) return 2;
    union bpf_attr attr = {0};
    attr.map_type = BPF_MAP_TYPE_HASH;
    attr.key_size = sizeof(uint32_t);
    attr.value_size = sizeof(struct bg_uid_value);
    attr.max_entries = 4;
    int fd = syscall(__NR_bpf, BPF_MAP_CREATE, &attr, sizeof(attr));
    if (fd < 0) return 3;
    const uint32_t uid = 10131;
    struct bg_uid_value value = {0};
    struct bg_uid_lease lease = {.fd = dup(fd), .uid = uid};
    if (bg_uid_begin(&lease) || bg_uid_lookup(fd, uid, &value) ||
        value.rule != ((1U << 12) | (1U << 4)) || bg_uid_restore(&lease)) return 4;
    if (!bg_uid_lookup(fd, uid, &value) || errno != ENOENT) return 5;

    // Clear host standby while retaining OEM deny rules and interface state.
    const struct bg_uid_value original = {.iif = 17, .rule = (1U << 3) | (1U << 9)};
    if (bg_uid_write(fd, uid, &original, BPF_NOEXIST)) return 6;
    lease = (struct bg_uid_lease){.fd = dup(fd), .uid = uid};
    if (bg_uid_begin(&lease) || bg_uid_lookup(fd, uid, &value) || value.iif != 17 ||
        value.rule != ((1U << 12) | (1U << 4) | (1U << 9))) return 7;
    if (bg_uid_restore(&lease) || bg_uid_lookup(fd, uid, &value) ||
        memcmp(&value, &original, sizeof(value))) return 8;

    // A concurrent external rule change must never be overwritten by restore.
    lease = (struct bg_uid_lease){.fd = dup(fd), .uid = uid};
    if (bg_uid_begin(&lease)) return 9;
    const struct bg_uid_value external = {.iif = 23, .rule = (1U << 5)};
    if (bg_uid_write(fd, uid, &external, BPF_EXIST)) return 10;
    if (!bg_uid_restore(&lease) || bg_uid_lookup(fd, uid, &value) ||
        memcmp(&value, &external, sizeof(value))) return 11;
    close(fd);
    puts("PASS: private map lease, exact restoration, external-change protection");
    return 0;
}
