#pragma once
#include <stdio.h>
// Only Samsung template files, excluding *_meta.dat and partial name matches.
static inline int guest_template170(const char *name) {
    int user = -1, finger = -1, consumed = 0;
    if (sscanf(name, "User_%d_%dtmpl.dat%n", &user, &finger, &consumed) != 2 ||
        consumed <= 0 || name[consumed] != '\0' || user != 10000 || finger < 1 || finger > 4)
        return 0;
    return finger;
}
