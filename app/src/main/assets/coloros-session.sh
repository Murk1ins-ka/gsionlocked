#!/system/bin/sh
set -eu
S=/data/local/tmp/oneos
BB=/data/adb/ksu/bin/busybox
fail() { echo "Проверка не пройдена: $*"; exit 2; }
[ "$(id -u)" = 0 ] || fail 'нет root'
[ "$(getprop ro.product.manufacturer | tr '[:upper:]' '[:lower:]')" = samsung ] || fail 'поддерживаются только телефоны Samsung'
[ "$(getprop ro.product.brand | tr '[:upper:]' '[:lower:]')" = samsung ] || fail 'поддерживаются только телефоны Samsung'
[ "$(getprop ro.build.version.sdk)" = 36 ] || fail 'запуск разрешён только из One UI Android 16'
[ "$(getprop ro.product.cpu.abi)" = arm64-v8a ] || fail 'нужна архитектура arm64-v8a'
[ "$(getenforce)" = Enforcing ] || fail 'состояние One UI не подтверждено'
[ -x "$BB" ] || fail 'не найден KernelSU busybox'
[ -d "$S" ] && [ ! -L "$S" ] || fail 'Среда отсутствует: импортируй .img через Browse files и нажми Start для автоматической подготовки'
[ "$(getprop sys.boot_completed)" = 1 ] || fail 'One UI ещё запускается'
[ -z "$(awk '$5 ~ /^\/data\/local\/tmp\/oneos(\/|$)/ {print}' /proc/1/mountinfo)" ] || fail 'среда уже используется'
if [ -d "$S/return.lock" ]; then
    if [ -f "$S/return.lock/owner" ]; then
        read -r owner_boot owner_pid < "$S/return.lock/owner" || fail 'неизвестное состояние возврата'
        [ -n "$owner_boot" ] && [ "$owner_boot" != "$(cat /proc/sys/kernel/random/boot_id)" ] || fail 'идёт возврат в One UI'
        rm -f "$S/return.lock/owner"
    fi
    rmdir "$S/return.lock" || fail 'не удалось завершить старый возврат'
fi
[ -n "$(pidof system_server)" ] || fail 'One UI ещё не загрузилась'
[ "$(sha256sum /system/framework/services.jar | cut -d' ' -f1)" = 86a4fdbec4469440a8e6cf3670948689ba5cdcc8b6ad1a666ce9d693b4cc86c3 ] || fail 'эта прошивка One UI не проверена'
[ "$(sha256sum "$S/fix.sha256" | cut -d' ' -f1)" = 9bfcb5a5d87d01d0afc81c200ee71a8a4bca7dd412253716a759531e80369284 ] || fail 'нужно обновить подготовленный комплект ColorOS'
echo 'Проверяю файлы ColorOS…'
(cd "$S"; sha256sum -c fix.sha256) > "$S/launcher-file-check.log" || fail 'файлы ColorOS изменились'
for file in "$S"/*.sh; do /system/bin/sh -n "$file" || fail 'ошибка сценария'; done
echo 'Комплект ColorOS проверен. 120 Гц.'
[ -f "$S/system.raw" ] && [ ! -L "$S/system.raw" ] || fail 'нет подготовленного образа'
[ "$(stat -c %s "$S/system.raw")" = 12069945344 ] || fail 'размер образа не совпадает'
case "${1:-status}" in
    status) exit 0 ;;
    select|start|start-preserve)
        echo 'Проверяю SHA-256 образа ColorOS…'
        [ "$(sha256sum "$S/system.raw" | cut -d' ' -f1)" = a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0 ] || fail 'образ ColorOS изменён'
        echo COLOROS_IMAGE_VERIFIED
        [ "$1" != select ] || exit 0
        if dumpsys trust | grep -Eq '(deviceLocked=1|secure=true)'; then fail 'разблокируй One UI и отключи защищённую блокировку'; fi
        [ -f "${2:-}" ] && [ ! -L "$2" ] || fail 'нет помощника запуска'
        mkdir -p /data/local/tmp/tempgsi-control
        [ -f "${3:-}" ] && [ ! -L "$3" ] || fail 'нет APK gsionlocked'
        rm -f /data/local/tmp/tempgsi-control/gsionlocked.apk
        cp "$3" /data/local/tmp/tempgsi-control/gsionlocked.apk
        chmod 600 /data/local/tmp/tempgsi-control/gsionlocked.apk
        cp "$2" /data/local/tmp/tempgsi-control/coloros-launch
        chmod 700 /data/local/tmp/tempgsi-control/coloros-launch
        export GSIONLOCKED_RETURN_MODE=reboot
        [ "$1" != start-preserve ] || export GSIONLOCKED_RETURN_MODE=inplace
        exec /data/local/tmp/tempgsi-control/coloros-launch --coloros
        ;;
    *) fail 'неизвестное действие' ;;
esac
