#!/system/bin/sh
# Verify this transfer only. Never starts/stops Android, mounts or flashes.
set -eu
SRC=/data/local/tmp/gsionlocked-hyperos-transfer-v1
DST=/data/local/tmp/gsionlocked-hyperos-preview
[ "$(id -u)" = 0 ] || exit 3
[ "$#" = 1 ] && [ "${#1}" = 64 ] || exit 3
case "$1" in *[!a-f0-9]*) exit 3 ;; esac
[ "$(getprop ro.product.model)" = SM-S911B ] && [ "$(getprop ro.build.version.sdk)" = 36 ] || exit 3
[ "$(getprop sys.boot_completed)" = 1 ] || exit 3
[ "$(sha256sum /system/framework/services.jar | cut -d' ' -f1)" = 86a4fdbec4469440a8e6cf3670948689ba5cdcc8b6ad1a666ce9d693b4cc86c3 ] || exit 3
[ -d "$SRC" ] && [ ! -L "$SRC" ] && [ ! -e "$DST" ] && [ ! -L "$DST" ] || exit 3
[ ! -e "$SRC/system.img" ] && [ ! -L "$SRC/system.img" ] || exit 3
chown 0:0 "$SRC"
chmod 700 "$SRC"
for file in "$SRC"/*; do
    [ -f "$file" ] && [ ! -L "$file" ] || exit 3
    case "${file##*/}" in *[!A-Za-z0-9_.-]*) exit 3 ;; esac
    chown 0:0 "$file"
    chmod 444 "$file"
done
[ "$(sha256sum "$SRC/prepare.sha256" | cut -d' ' -f1)" = "$1" ] || exit 3
cd "$SRC"
sha256sum -c prepare.sha256 || exit 3
mv system.img.partial system.img
umask 077
sed 's/  system.img.partial$/  system.img/' prepare.sha256 > verified.sha256
chmod 444 verified.sha256
rm prepare.sha256
cd /
mv "$SRC" "$DST"
echo "HYPEROS_PREPARED=$DST"
echo 'HYPEROS_LAUNCH_ENABLED=0 (runtime integration and device validation pending)'
