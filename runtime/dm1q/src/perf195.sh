#!/system/bin/sh
# Bounded read-only profiling. Does not test connectivity or save screen/face data.
export LD_LIBRARY_PATH=/apex/com.android.runtime/lib64/bionic:/system/lib64
export LD_PRELOAD=/data/local/tmp/private-selinux-mount.so
while read command name value; do export "$name=$value"; done < /data/system/environ/classpath
n=0
while [ "$n" -lt 60 ]; do
    [ "$(getprop sys.boot_completed)" = 1 ] && break
    sleep 2
    n=$((n+1))
done
sample=0
while [ "$sample" -lt 5 ]; do
    echo "PERF195_SAMPLE=$sample"
    date +%s
    for field in temp status current_now voltage_now capacity; do
        echo "BATTERY_FIELD=$field"
        cat "/sys/class/power_supply/battery/$field"
    done
    echo CPU
    top -b -n 1 -m 18
    echo SYSTEMUI_FRAMES
    dumpsys gfxinfo com.android.systemui
    echo SYSTEMUI_MEMORY
    dumpsys meminfo com.android.systemui | head -50
    echo WAKELOCKS
    dumpsys power | grep -E 'Wake Locks|PARTIAL_WAKE_LOCK|mWakefulness=|mHoldingWakeLockSuspendBlocker|mWakeLockSummary'
    sample=$((sample+1))
    sleep 20
done
echo PERF195_FINISHED
