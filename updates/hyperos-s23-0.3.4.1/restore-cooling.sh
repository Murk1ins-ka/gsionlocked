#!/system/bin/sh
set -eu
[ "$(id -u)" = 0 ] || exit 2
B=/data/local/tmp/gsionlocked-cool-backup
for tag in system_server shsusrd ThermalResourceMonitor io_stats SDM; do
 key=log.tag.$tag
 [ -f "$B/$key" ] && [ ! -L "$B/$key" ] || exit 3
 setprop "$key" "$(cat "$B/$key")"
done
for key in log.tag log.tag.AHAL_SecModulePrimary log.tag.AHAL_Telephony_QTI log.tag.OneOS log.tag.ShutdownThread log.tag.PowerManagerService log.tag.GlobalActions log.tag.RescueParty log.tag.sulogd log.tag.KernelSU; do
 [ -f "$B/$key" ] && [ ! -L "$B/$key" ] || exit 3
 setprop "$key" "$(cat "$B/$key")"
done
# Original measured sizes on this device, also recorded in B/buffers.txt.
logcat -b main -G 5M
logcat -b system -G 2M
H=/data/local/tmp/gsionlocked-hyperos-preview/fix-s23-daily-use.sh
[ -f "$H" ] && [ ! -L "$H" ] || exit 4
awk '$0 != "# GSIONLOCKED_COOL_STARTUP" && $0 != "/system/bin/sh /data/local/tmp/gsionlocked-cool-profile.sh" {print}' "$H" > "$B/hook-restored.pending"
cat "$B/hook-restored.pending" > "$H"
chown 0:0 "$H"; chmod 700 "$H"
echo COOL_PROFILE_RESTORED_120HZ_PRESERVED
