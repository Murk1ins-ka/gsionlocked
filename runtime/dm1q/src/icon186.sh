#!/system/bin/sh
export LD_LIBRARY_PATH=/apex/com.android.runtime/lib64/bionic:/system/lib64
export LD_PRELOAD=/data/local/tmp/private-selinux-mount.so
while read command name value; do export "$name=$value"; done < /data/system/environ/classpath
n=0
while [ "$n" -lt 60 ]; do
    [ "$(getprop sys.boot_completed)" = 1 ] && break
    sleep 2
    n=$((n+1))
done
[ "$(getprop sys.boot_completed)" = 1 ] || exit 21
changed=0
if ! dumpsys package org.evolution.udfps.icons | grep -q 'versionCode=179 '; then
    pm install -r --user 0 /data/local/tmp/UdfpsIcons186.apk || exit 22
    changed=1
fi
if ! cmd overlay list android | grep -q '\[x\] com.android.shell:dm1qAdaptive180'; then
    cmd overlay fabricate --target android --name dm1qAdaptive180 android:integer/config_defaultRefreshRate 0x10 120 && cmd overlay enable --user 0 com.android.shell:dm1qAdaptive180
fi
echo ICON186_PREVIOUS_VALUE
settings get system udfps_icon
if [ "$(settings get system udfps_icon)" != 1 ]; then
    settings put system udfps_icon 1 || exit 23
    changed=1
fi
[ "$(settings get system udfps_icon)" = 1 ] || exit 24
if [ "$changed" = 1 ]; then
    echo ICON186_GUEST_SYSTEMUI_RELOAD
    am crash --user 0 com.android.systemui
    sleep 4
fi
echo ICON186_PACKAGE_READY
