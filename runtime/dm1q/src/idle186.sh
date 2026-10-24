#!/system/bin/sh
# Explicit measurement mode only. Sleeps between raw counter snapshots.
# No network requests, application logs, screenshots or forced device-idle.
export LD_LIBRARY_PATH=/apex/com.android.runtime/lib64/bionic:/system/lib64
export LD_PRELOAD=/data/local/tmp/private-selinux-mount.so
while read command name value; do export "$name=$value"; done < /data/system/environ/classpath
n=0
while [ "$n" -lt 90 ]; do
    [ "$(getprop sys.boot_completed)" = 1 ] && break
    sleep 2
    n=$((n+1))
done
[ "$(getprop sys.boot_completed)" = 1 ] || exit 20
echo IDLE_PROFILE_BEGIN
dumpsys display
cat /proc/sys/kernel/random/boot_id
dumpsys power
dumpsys deviceidle
sample=0
while [ "$sample" -lt 31 ]; do
    echo "IDLE_SAMPLE=$sample"
    date +%s
    cat /proc/uptime
    head -1 /proc/stat
    for field in temp status capacity charge_counter current_now voltage_now energy_now; do
        echo "BATTERY_RAW_FIELD=$field"
        if [ -r "/sys/class/power_supply/battery/$field" ]; then
            cat "/sys/class/power_supply/battery/$field"
        else
            echo UNAVAILABLE
        fi
    done
    sample=$((sample+1))
    sleep 60
done
dumpsys power
dumpsys deviceidle
dumpsys batterystats --charged
echo IDLE_PROFILE_FINISHED
