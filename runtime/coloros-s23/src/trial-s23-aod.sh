#!/system/bin/sh
# Explicit bounded AOD trial, never run automatically during guest boot.
set -eu
S=/data/local/tmp/oneos
BB=/data/adb/ksu/bin/busybox
BOOT=$(cat /proc/sys/kernel/random/boot_id)
SS=$(pidof system_server)
[ "$(id -u)" = 0 ] || exit 1
[ "$(getprop ro.build.version.sdk)" = 37 ] || exit 2
case "$SS" in ''|*[!0-9]*) exit 3 ;; esac
g() { "$BB" nsenter -t "$SS" -m -r -w /data/oneos_genv.sh /system/bin/"$@"; }
restore() {
    [ "$(cat "$D/core")" = "$(pidof system_server)" ] && [ "$(cat "$D/boot")" = "$BOOT" ] || {
        echo 'AOD restoration pending: guest core or boot changed; no host setting touched'
        exit 4
    }
    while read -r key value; do
        case "$key" in doze_always_on|Setting_AodEnable|Setting_AodSwitchEnable|Setting_AodEnableImmediate|Setting_AodSecureEnable_For_Test|Setting_AodUserEnergySavingSet|Setting_AodUserSetTime|Setting_AodIsGlobalThemeClock) ;; *) exit 5 ;; esac
        case "$value" in
            null) g settings delete secure "$key" ;;
            0|1) g settings put secure "$key" "$value" ;;
            2) [ "$key" = Setting_AodUserEnergySavingSet ] || exit 6
               g settings put secure "$key" "$value" ;;
            *) exit 6 ;;
        esac
    done < "$D/before"
    if [ -f "$D/night-before" ]; then
        night=$(cat "$D/night-before")
        case "$night" in yes|no|auto) g cmd uimode night "$night" ;; *) exit 16 ;; esac
    fi
    if [ -f "$D/theme-overlay" ]; then
        overlay=$(cat "$D/theme-overlay")
        case "$overlay" in com.android.shell:OneOSAodLegacy[0-9]*) g cmd overlay disable --user 0 "$overlay" ;; *) exit 16 ;; esac
    fi
    if [ -f "$D/feature-before" ]; then
        prior=$(cat "$D/feature-before")
        current=$(g cmd package has-oplusfeature oplus.software.display.aod_support)
        case "$prior" in
            false|true) ;;
            *) exit 13 ;;
        esac
        if [ "$current" != "oplus.software.display.aod_support = $prior" ]; then
            echo 'Unexpected AOD capability change: manual review required'; exit 13
        fi
    fi
    touch "$D/restored"
    g input keyevent 224
    echo 'AOD_TRIAL_RESTORED'
}
case "${1:-trial}" in
    rollback|restore)
        D=${2:-}
        case "$D" in "$S"/aod-trial-*) ;; *) exit 7 ;; esac
        [ -f "$D/before" ] && [ -f "$D/core" ] && [ -f "$D/boot" ] || exit 8
        [ "${1:-}" != rollback ] || sleep 75
        [ ! -f "$D/restored" ] || exit 0
        restore
        exit 0
        ;;
    trial) ;;
    *) exit 9 ;;
esac
COMPONENT=$(g cmd overlay lookup android android:string/config_dozeComponent)
[ "$COMPONENT" = com.android.systemui/com.android.systemui.doze.DozeService ] || {
    echo "Unexpected doze component: $COMPONENT"; exit 10;
}
[ "$(g cmd overlay lookup android android:bool/config_dozeAlwaysOnDisplayAvailable)" = true ] || exit 11
FEATURE=$(g cmd package has-oplusfeature oplus.software.display.aod_support)
[ "$FEATURE" = 'oplus.software.display.aod_support = true' ] || {
    echo 'AOD static capability missing; prepare it before the next guest launch'
    exit 14
}
D="$S/aod-trial-$(date +%s)-$$"
mkdir "$D"
printf '%s\n' "$BOOT" > "$D/boot"
printf '%s\n' "$SS" > "$D/core"
KEYS='doze_always_on Setting_AodEnable Setting_AodSwitchEnable Setting_AodEnableImmediate'
if [ "${ONEOS_AOD_BUILTIN_TRIAL:-0}" = 1 ]; then
    # This is an OEM AOD observer switch in Settings.Secure, unrelated to
    # credential authentication. Without the optional com.oplus.aod app,
    # AodSettingsObserver ignores clock-option changes unless this is 1.
    KEYS="$KEYS Setting_AodSecureEnable_For_Test Setting_AodUserEnergySavingSet Setting_AodUserSetTime"
fi
if [ "${ONEOS_AOD_LEGACY_THEME_TRIAL:-0}" = 1 ]; then
    KEYS="$KEYS Setting_AodIsGlobalThemeClock"
    night=$(g cmd uimode night)
    case "$night" in 'Night mode: yes') night=yes ;; 'Night mode: no') night=no ;; 'Night mode: auto') night=auto ;; *) echo 'Unsupported prior night mode'; exit 16 ;; esac
    printf '%s\n' "$night" > "$D/night-before"
    [ "$(g cmd overlay lookup com.android.systemui com.android.systemui:integer/aod_clock_theme_type)" = 0 ] || exit 16
fi
for key in $KEYS; do
    value=$(g settings get secure "$key")
    case "$value" in
        null|0|1) ;;
        2) [ "$key" = Setting_AodUserEnergySavingSet ] || exit 12 ;;
        *) echo "Unexpected $key=$value"; exit 12 ;;
    esac
    printf '%s %s\n' "$key" "$value" >> "$D/before"
done
# The restorer remains alive if the initiating ADB command disconnects.
# If the guest dies, it refuses to address the new host core and leaves a
# pending restoration record, requiring review before a subsequent AOD trial.
nohup /system/bin/sh "$0" rollback "$D" > "$D/rollback.log" 2>&1 < /dev/null &
echo "AOD_TRIAL_BACKUP=$D RESTORER=$!"
if [ "${ONEOS_AOD_BUILTIN_TRIAL:-0}" = 1 ]; then
    g settings put secure Setting_AodSecureEnable_For_Test 1
    g settings put secure Setting_AodUserEnergySavingSet 0
    g settings put secure Setting_AodUserSetTime 0
fi
if [ "${ONEOS_AOD_LEGACY_THEME_TRIAL:-0}" = 1 ]; then
    overlay=OneOSAodLegacy$$
    g cmd overlay fabricate --target com.android.systemui --name "$overlay" com.android.systemui:integer/aod_clock_theme_type 0x10 1
    printf 'com.android.shell:%s\n' "$overlay" > "$D/theme-overlay"
    g cmd overlay enable --user 0 "com.android.shell:$overlay"
    [ "$(g cmd overlay lookup com.android.systemui com.android.systemui:integer/aod_clock_theme_type)" = 1 ] || exit 16
    g cmd uimode night no
    g settings put secure Setting_AodIsGlobalThemeClock 1
fi
for key in doze_always_on Setting_AodEnable Setting_AodSwitchEnable Setting_AodEnableImmediate; do
    g settings put secure "$key" 1
done
if [ "${ONEOS_AOD_RESTART_UI_TRIAL:-0}" = 1 ]; then
    UI=$(pidof com.android.systemui)
    case "$UI" in ''|*[!0-9]*) echo 'Expected one guest SystemUI'; exit 15 ;; esac
    [ "$(pidof system_server)" = "$SS" ] || exit 15
    kill -TERM "$UI"
    n=0
    NEW_UI=""
    while [ "$n" -lt 15 ]; do
        sleep 1
        NEW_UI=$(pidof com.android.systemui || true)
        if [ -n "$NEW_UI" ] && [ "$NEW_UI" != "$UI" ]; then break; fi
        n=$((n + 1))
    done
    [ -n "$NEW_UI" ] && [ "$NEW_UI" != "$UI" ] || exit 15
    sleep 4
fi
date > "$D/before-state"
dumpsys battery | grep -E 'temperature:|level:' >> "$D/before-state"
g input keyevent 223
sleep 15
g dumpsys activity service com.android.systemui/.SystemUIService > "$D/systemui-asleep" 2>&1 || true
g dumpsys SurfaceFlinger --list > "$D/layers-asleep" 2>&1 || true
{
    for node in /sys/class/backlight/panel/brightness /sys/class/backlight/panel/actual_brightness \
                /sys/class/backlight/panel/max_brightness /sys/class/backlight/panel0-backlight/brightness \
                /sys/class/backlight/panel0-backlight/actual_brightness /sys/class/backlight/panel0-backlight/max_brightness \
                /sys/class/lcd/panel/alpm /sys/class/lcd/panel/lcd_power; do
        [ ! -r "$node" ] || { printf '%s=' "$node"; cat "$node" || true; }
    done
} > "$D/panel-asleep" 2>&1
{
    date
    dumpsys dreams
    dumpsys power | grep -E 'mWakefulness=|mDozeScreenState|mScreenOffTimeoutSetting'
    dumpsys display | grep -E 'state=DOZE|mState=DOZE|DOZE_SUSPEND'
    dumpsys battery | grep -E 'temperature:|level:'
} > "$D/asleep-state" 2>&1 || true
g input keyevent 224
echo "AOD_TRIAL_CAPTURED=$D/asleep-state (visual clock and sustained low-power behavior still need verification)"
echo 'Refresh-rate settings unchanged: this trial does not impose a 60 Hz cap.'
