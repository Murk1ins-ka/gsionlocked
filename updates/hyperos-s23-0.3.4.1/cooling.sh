#!/system/bin/sh
set -eu
[ "$(id -u)" = 0 ] && [ "$(getprop ro.build.version.sdk)" = 37 ] || exit 2
[ -d /data/local/tmp/gsionlocked-hyperos-preview ] || exit 2
B=/data/local/tmp/gsionlocked-cool-backup
[ ! -L "$B" ] || exit 3
mkdir -p "$B"; chmod 700 "$B"
if [ ! -f "$B/buffers.txt" ]; then logcat -g > "$B/buffers.txt"; fi
for tag in system_server shsusrd ThermalResourceMonitor io_stats SDM; do
 key=log.tag.$tag
 [ ! -L "$B/$key" ] || exit 3
 if [ ! -f "$B/$key" ]; then getprop "$key" > "$B/$key"; chmod 600 "$B/$key"; fi
 setprop "$key" W
done
# Lower default log noise; retain tags used by guest audio/return supervision.
for key in log.tag log.tag.AHAL_SecModulePrimary log.tag.AHAL_Telephony_QTI log.tag.OneOS log.tag.ShutdownThread log.tag.PowerManagerService log.tag.GlobalActions log.tag.RescueParty log.tag.sulogd log.tag.KernelSU; do
 [ ! -L "$B/$key" ] || exit 3
 if [ ! -f "$B/$key" ]; then getprop "$key" > "$B/$key"; chmod 600 "$B/$key"; fi
 case "$key" in log.tag) setprop "$key" W ;; *) setprop "$key" I ;; esac
done
logcat -b main -G 1M
logcat -b system -G 512K
echo COOL_PROFILE_APPLIED
