#!/system/bin/sh
# Run before pivot_root, only inside the new guest mount namespace.
set -eu
fail() { echo "HOST ABI BIND REFUSED: $*"; exit 3; }
[ "$(id -u)" = 0 ] || fail 'root required'
SELF=$(readlink /proc/self/ns/mnt)
INIT=$(readlink /proc/1/ns/mnt)
[ -n "$SELF" ] && [ -n "$INIT" ] && [ "$SELF" != "$INIT" ] || fail 'refusing a bind in the host mount namespace'
ROOTFS=${1:-}
[ "$ROOTFS" = /data/local/tmp/oneos/rootfs ] || fail 'unexpected guest root'
[ -d "$ROOTFS" ] && [ ! -L "$ROOTFS" ] && [ -d "$ROOTFS/data" ] && [ ! -L "$ROOTFS/data" ] || fail 'invalid guest data path'
awk -v target="$ROOTFS" '$5 == target {found=1} END {exit !found}' /proc/self/mountinfo || fail 'guest root is not a mountpoint'
BASE=/data/local/tmp/gsionlocked-host-runtimes
CAPSULE=${GSIONLOCKED_HOST_RUNTIME:-}
KEY=${CAPSULE#"$BASE/"}
[ "$CAPSULE" = "$BASE/$KEY" ] && [ "${#KEY}" = 64 ] || fail 'unexpected capsule path'
case "$KEY" in *[!0-9a-f]*) fail 'invalid capsule identity' ;; esac
MAP_SHA=${GSIONLOCKED_HOST_MAP_SHA256:-}
[ "${#MAP_SHA}" = 64 ] || fail 'missing host mapping digest'
case "$MAP_SHA" in *[!0-9a-f]*) fail 'invalid mapping digest' ;; esac
root_private() {
    [ -d "$1" ] && [ ! -L "$1" ] || return 1
    case "$(stat -c '%u %A' "$1")" in
        "0 "?????w*|"0 "????????w*) return 1 ;;
        "0 "*) return 0 ;;
    esac
    return 1
}
root_private "$BASE" && root_private "$CAPSULE" || fail 'capsule is not protected root storage'
SOURCE="$CAPSULE/installd.properties"
[ -f "$SOURCE" ] && [ ! -L "$SOURCE" ] && [ "$(stat -c %u "$SOURCE")" = 0 ] || fail 'invalid host mapping file'
[ "$(sha256sum "$SOURCE" | cut -d' ' -f1)" = "$MAP_SHA" ] || fail 'host mapping changed'
same_framework() {
    [ -f "$CAPSULE/$1" ] && [ ! -L "$CAPSULE/$1" ] && [ -f "/system/framework/$1" ] || return 1
    local_sum=$(sha256sum "$CAPSULE/$1" | cut -d' ' -f1)
    host_sum=$(sha256sum "/system/framework/$1" | cut -d' ' -f1)
    [ "${#local_sum}" = 64 ] && [ "$local_sum" = "$host_sum" ]
}
same_framework services.jar || fail 'host services changed after capture'
same_framework framework.jar || fail 'host boot framework changed after capture'
BB=${GSIONLOCKED_BIND_BUSYBOX:-}
case "$BB" in /data/adb/ksu/bin/busybox|/data/adb/ap/bin/busybox|/data/adb/magisk/busybox) ;; *) fail 'unexpected root tool' ;; esac
[ -x "$BB" ] || fail 'root busybox is unavailable'
TARGET_DIR="$ROOTFS/data/gsionlocked-host-abi"
[ ! -L "$TARGET_DIR" ] || fail 'guest mapping directory is a symlink'
mkdir -p "$TARGET_DIR"
chown 0:0 "$TARGET_DIR"
chmod 555 "$TARGET_DIR"
TARGET="$TARGET_DIR/installd.properties"
# Unlink a possible stale link first; never truncate through a guest-controlled name.
[ ! -d "$TARGET" ] || fail 'mapping target is a directory'
rm -f "$TARGET"
: > "$TARGET"
chown 0:0 "$TARGET"
chmod 444 "$TARGET"
MOUNTED=0
OK=0
cleanup() { if [ "$MOUNTED" = 1 ] && [ "$OK" != 1 ]; then "$BB" umount "$TARGET" 2>/dev/null || true; fi; }
trap cleanup 0
"$BB" mount --bind "$SOURCE" "$TARGET" || fail 'could not bind host mapping'
MOUNTED=1
"$BB" mount -o remount,bind,ro,nosuid,nodev,noexec "$SOURCE" "$TARGET" || fail 'could not protect host mapping'
awk -v target="$TARGET" '$5 == target && $6 ~ /(^|,)ro(,|$)/ {found=1} END {exit !found}' /proc/self/mountinfo || fail 'mapping mount is not read-only'
[ "$(sha256sum "$TARGET" | cut -d' ' -f1)" = "$MAP_SHA" ] || fail 'bound mapping differs'
OK=1
echo 'HOST_ABI_BOUND_READ_ONLY=1'
