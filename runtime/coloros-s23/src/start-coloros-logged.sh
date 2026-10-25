#!/system/bin/sh
S=/data/local/tmp/oneos
BB=/data/adb/ksu/bin/busybox
export ONEOS_APP=0 ONEOS_PROFILE=s23-8550-ossi37 ONEOS_SCREEN_LOGS=0
export ONEOS_GAP_SPLASH=0
export ONEOS_NATIVE_HOST_LOGS=1
export ONEOS_RETURN_MODE=${GSIONLOCKED_RETURN_MODE:-reboot} ONEOS_RETURN_MAX=180 ONEOS_RETURN_BLACK=90 ONEOS_SESSION_MAX=0
export ONEOS_RETURN_KEYS=1 ONEOS_LOGCAT=1 ONEOS_GESTURES=1
# This S23's guest audioserver repeatedly falls back to Samsung's at boot.
# Keep that stable host service instead of restarting it during zygote setup.
export ONEOS_GUEST_AUDIO=1 ONEOS_GUEST_WIFI_CN=0 ONEOS_GUEST_HOST_GMS=1 ONEOS_GUEST_HWC=1
BOOT=$(cat /proc/sys/kernel/random/boot_id)
SESSION="$BOOT:$$:$(date +%s)"
echo "$SESSION" > "$S/trial-rescue-session"
(
 sleep 900
 [ "$(cat "$S/trial-rescue-session" 2>/dev/null)" = "$SESSION" ] || exit
 [ "$(cat /proc/sys/kernel/random/boot_id)" = "$BOOT" ] || exit
 [ "$(getprop init.svc.zygote)" = running ] && [ "$(getprop ro.build.version.sdk)" = 36 ] && exit
 # The external rescue is for a failed takeover supervisor, not a time limit
 # on a healthy guest. The normal watcher still handles crashes/black frames,
 # the in-guest return request and the hardware key chord.
 if [ "$(getprop ro.build.version.sdk)" = 37 ] &&
    [ -n "$(pidof system_server)" ] && [ -n "$(pidof com.android.systemui)" ] &&
    [ -n "$(pidof surfaceflinger)" ]; then
     echo "$(date) external rescue: guest services alive; no timed return"
     exit 0
 fi
 "$BB" pgrep -f '^(/system/bin/)?sh /data/local/tmp/oneos/return_to_oneui.sh' >/dev/null && exit
 exec "$BB" nsenter -t 1 -m -- /system/bin/sh "$S/return_to_oneui.sh"
) </dev/null >>"$S/trial3-rescue.log" 2>&1 &
/system/bin/sh "$S/oneos_switch.sh"
echo "SWITCH_EXIT=$? $(date)"

