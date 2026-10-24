// SPDX-License-Identifier: Apache-2.0
// Host service ownership only. Call restore AFTER guest hardware users exit.
#ifndef HAL_SERVICE_LEASE159_H
#define HAL_SERVICE_LEASE159_H
#define HAL_LEASE159_MAX 5
enum hal_service_state159 { HAL159_UNKNOWN=-1, HAL159_STOPPED=0, HAL159_RUNNING=1 };
struct hal_service_ops159 {
    int (*state)(void *, const char *);
    int (*set)(void *, const char *, int);
    void *context;
};
struct hal_service_lease159 {
    const char *names[HAL_LEASE159_MAX];
    unsigned count, original_running, restore_pending;
    int captured, acquired;
};

static inline int hal_lease159_capture(struct hal_service_lease159 *lease,
                                     const struct hal_service_ops159 *ops) {
    if (!lease || !ops || !ops->state || !ops->set || lease->captured ||
        !lease->count || lease->count>HAL_LEASE159_MAX) return -1;
    unsigned running=0;
    for (unsigned i=0;i<lease->count;i++) {
        if (!lease->names[i] || !lease->names[i][0]) return -1;
        // Ambiguous service states cannot establish exclusive ownership.
        int state=ops->state(ops->context,lease->names[i]);
        if (state!=HAL159_STOPPED && state!=HAL159_RUNNING) return -1;
        if (state==HAL159_RUNNING) running|=1U<<i;
        for (unsigned j=0;j<i;j++) {
            const char *a=lease->names[i],*b=lease->names[j];
            while (*a && *a==*b) { a++;b++; }
            if (*a==*b) return -1;
        }
    }
    lease->original_running=running;
    lease->restore_pending=0;
    lease->captured=1;
    lease->acquired=0;
    return 0;
}

static inline int hal_lease159_acquire(struct hal_service_lease159 *lease,
                                     const struct hal_service_ops159 *ops) {
    if (!lease || !ops || !ops->state || !ops->set || !lease->captured ||
        lease->acquired || lease->restore_pending) return -1;
    // Check the whole snapshot before stopping the first service.
    for (unsigned i=0;i<lease->count;i++)
        if (ops->state(ops->context,lease->names[i])!=
            ((lease->original_running&(1U<<i))?HAL159_RUNNING:HAL159_STOPPED)) return -1;
    for (unsigned i=0;i<lease->count;i++) {
        if (!(lease->original_running&(1U<<i))) continue;
        // A timed-out stop request may still have taken effect.
        lease->restore_pending|=1U<<i;
        if (ops->set(ops->context,lease->names[i],HAL159_STOPPED) ||
            ops->state(ops->context,lease->names[i])!=HAL159_STOPPED) return -1;
    }
    for (unsigned i=0;i<lease->count;i++)
        if (ops->state(ops->context,lease->names[i])!=HAL159_STOPPED) return -1;
    lease->acquired=1;
    return 0;
}

static inline int hal_lease159_restore(struct hal_service_lease159 *lease,
                                     const struct hal_service_ops159 *ops,
                                     int guest_hardware_users_exited) {
    if (!lease || !ops || !ops->state || !ops->set || !lease->captured ||
        !guest_hardware_users_exited) return -1;
    int failed=0;
    // Attempt every restoration even if one init request fails.
    for (unsigned n=lease->count;n>0;n--) {
        unsigned i=n-1,bit=1U<<i;
        if (!(lease->restore_pending&bit)) continue;
        if (ops->state(ops->context,lease->names[i])!=HAL159_RUNNING &&
            ops->set(ops->context,lease->names[i],HAL159_RUNNING)) { failed=1;continue; }
        if (ops->state(ops->context,lease->names[i])!=HAL159_RUNNING) { failed=1;continue; }
        lease->restore_pending&=~bit;
    }
    lease->acquired=0;
    return failed || lease->restore_pending ? -1:0;
}
#endif
