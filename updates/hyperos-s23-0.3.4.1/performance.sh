#!/system/bin/sh
# Owner's daily-use policy, guest only. Keep Samsung thermal controls and 120 Hz.
set -eu
[ "$(id -u)" = 0 ]
[ "$(getprop ro.build.version.sdk)" = 37 ] || exit 2
SS=$(pidof system_server)
case "$SS" in ''|*[!0-9]*) exit 3 ;; esac
BB=/data/adb/ksu/bin/busybox
g() { "$BB" nsenter -t "$SS" -m -r -w /data/oneos_genv.sh /system/bin/"$@"; }
for key in doze_always_on doze_enabled doze_pulse_on_pick_up doze_pulse_on_double_tap \
           Setting_AodEnable Setting_AodSwitchEnable Setting_AodEnableImmediate \
           Setting_AodSecureEnable_For_Test Setting_AodIsGlobalThemeClock; do
    g settings put secure "$key" 0
done
g settings put secure screensaver_enabled 0
g cmd overlay disable --user 0 com.android.shell:OneOSAodClassic >/dev/null 2>&1 || true
g cmd statusbar remove-tile org.oneos.aodcontrol/.AodTile >/dev/null 2>&1 || true
if g pm path org.oneos.aodcontrol | grep -q '^package:'; then
    g pm disable-user --user 0 org.oneos.aodcontrol
fi
B=/data/local/tmp/gsionlocked-performance-backup
[ ! -L "$B" ] || exit 4
mkdir -p "$B"; chmod 700 "$B"
for entry in system:peak_refresh_rate system:min_refresh_rate global:window_animation_scale global:transition_animation_scale global:animator_duration_scale; do
 ns=${entry%%:*}; key=${entry#*:}
 if [ ! -e "$B/$key" ]; then g settings get "$ns" "$key" > "$B/$key"; chmod 600 "$B/$key"; fi
done
g settings put system peak_refresh_rate 120.0
g settings put system min_refresh_rate 120.0
g settings put system screen_off_timeout 120000
g settings put global stay_on_while_plugged_in 0
g svc power stayon false
# Use Android policy so thermal and critical-power constraints still apply.
g settings put global window_animation_scale 0.5
g settings put global transition_animation_scale 0.5
g settings put global animator_duration_scale 0.5
echo 'S23_DAILY_USE_APPLIED AOD=off PEAK_HZ=120 MIN_HZ=120 ANIMATION_SCALE=0.5' 

# GSIONLOCKED_COOL_STARTUP
/system/bin/sh /data/local/tmp/gsionlocked-cool-profile.sh
