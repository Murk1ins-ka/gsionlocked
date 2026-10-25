#!/system/bin/sh
# Repair the observed package-level denial for the actual default SMS app.
# This does not prove modem delivery and never sends or reads an SMS body.
set -eu
[ "$(id -u)" = 0 ]
[ "$(getprop ro.build.version.sdk)" = 37 ]
SS=$(pidof system_server)
case "$SS" in ''|*[!0-9]*) exit 2 ;; esac
BB=/data/adb/ksu/bin/busybox
g() { "$BB" nsenter -t "$SS" -m -r -w /data/oneos_genv.sh /system/bin/"$@"; }
ROLE=$(g cmd role get-role-holders android.app.role.SMS)
if [ "$ROLE" != com.google.android.apps.messaging ]; then
    echo 'SMS role differs from the inspected Google Messages package; unchanged'
    exit 0
fi
g cmd appops get "$ROLE" WRITE_SMS
g cmd appops set "$ROLE" WRITE_SMS allow
g cmd appops get "$ROLE" WRITE_SMS
echo 'Default SMS app write operation repaired; end-to-end SMS still needs testing'
