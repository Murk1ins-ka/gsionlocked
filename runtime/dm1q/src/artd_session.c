// SPDX-License-Identifier: Apache-2.0
// Loaded only by the guest artd. The session guardian owns its lifetime.
#include <dlfcn.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

__attribute__((constructor)) static void keep_artd_for_session(void) {
    if (strcmp(getprogname(), "artd")) return;
    void (*persist)(bool) = dlsym(RTLD_DEFAULT, "AServiceManager_forceLazyServicesPersist");
    if (!persist) {
        fputs("ARTD_SESSION_PERSIST_API_MISSING\n", stderr);
        _exit(96);
    }
    // There is no guest init to restart a lazy service after its last client.
    // Keep this process registered until the guardian destroys the guest.
    persist(true);
    fputs("ARTD_SESSION_PERSIST_ENABLED\n", stderr);
}
