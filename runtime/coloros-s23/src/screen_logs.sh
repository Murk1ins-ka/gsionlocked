#!/system/bin/sh
# Loopback-only, read-only console. Serves only the dedicated web directory.
set -eu
S=/data/local/tmp/oneos
BB=/data/adb/ksu/bin/busybox
WEB=$S/log-screen
[ "$(id -u)" = 0 ] || exit 1
OLD=$(cat "$S/screen_logs.pid" 2>/dev/null || true)
case "$OLD" in
    ''|*[!0-9]*) ;;
    *)
        if [ "$OLD" != "$$" ]; then
            case "$(tr '\0' ' ' < "/proc/$OLD/cmdline" 2>/dev/null)" in
                "/system/bin/sh $S/screen_logs.sh "*) exit 0 ;;
            esac
        fi
        ;;
esac
mkdir -p "$WEB"
chmod 755 "$WEB"
cp "$S/logs-index.html" "$WEB/index.html"
chmod 644 "$WEB/index.html"
START=$(date +%s)
printf 'ColorOS: экран журналов готов.\nОжидание следующего запуска…\n' > "$WEB/logs.txt"
"$BB" httpd -f -p 127.0.0.1:10888 -h "$WEB" &
HTTP=$!
trap 'kill "$HTTP" 2>/dev/null || true' EXIT
trap 'exit 0' HUP INT TERM
sleep 1
kill -0 "$HTTP" || exit 1
echo "$$" > "$S/screen_logs.pid"
for tick in $(seq 1 900); do
 {
  echo "ColorOS / S23 · $(date '+%H:%M:%S')"
  echo "SDK: $(getprop ro.build.version.sdk) · SELinux: $(getenforce)"
  echo "Для возврата: громкость вверх + вниз, удерживать 3 секунды."
  echo
  any=0
  for f in "$S/takeover_s23_trial3.log" "$S/data/takeover.log" "$S/return_inplace.log"; do
   [ -f "$f" ] || continue
   MTIME=$(stat -c %Y "$f")
   [ "$MTIME" -ge "$START" ] || continue
   any=1
   echo "=== ${f##*/} ==="
   tail -n 100 "$f"
  done
  [ "$any" = 1 ] || echo "Экран журналов подключён. Ожидание запуска ColorOS…"
 } > "$WEB/logs.next"
 mv "$WEB/logs.next" "$WEB/logs.txt"
 chmod 644 "$WEB/logs.txt"
 sleep 1
done
