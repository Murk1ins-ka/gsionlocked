#!/system/bin/sh
# Sourced after return gates A/B. Never restart netd and race its onrestart
# zygote action against an explicit zygote start. Failure remains fail-closed.
s23_wait_init_state() {
    svc=$1; target=$2; ticks=0
    while [ "$ticks" -lt 40 ]; do
        [ "$(getprop "init.svc.$svc")" = "$target" ] && return 0
        sleep 0.5; ticks=$((ticks + 1))
    done
    return 1
}
s23_restart_host_framework() {
    setprop ctl.stop zygote
    secondary=$(base_prop init.svc.zygote_secondary)
    [ -z "$secondary" ] || setprop ctl.stop zygote_secondary
    s23_wait_init_state zygote stopped || failsafe 'primary zygote did not stop'
    [ -z "$secondary" ] || s23_wait_init_state zygote_secondary stopped || failsafe 'secondary zygote did not stop'
    [ -z "$(pidof system_server)" ] || failsafe 'system_server remains before host restart'
    setprop ctl.stop netd
    s23_wait_init_state netd stopped || failsafe 'netd did not stop'
    setprop ctl.start netd
    s23_wait_init_state netd running || failsafe 'netd did not start'
    wait_found dnsresolver 10 || failsafe 'fresh DNS resolver not registered'
    [ "$(getprop init.svc.zygote)" = stopped ] || failsafe 'zygote restarted unexpectedly during netd handoff'
    [ -z "$(pidof system_server)" ] || failsafe 'unexpected system_server before explicit start'
    setprop ctl.start zygote
    s23_wait_init_state zygote running || failsafe 'primary zygote did not start'
    [ "$secondary" != running ] || setprop ctl.start zygote_secondary
    say 'S23 framework: netd stop/start settled; one explicit primary zygote start'
}
