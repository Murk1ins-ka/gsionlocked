#!/system/bin/sh
set -eu
[ "$(id -u)" = 0 ] || { echo 'Root required'; exit 2; }
S=/data/local/tmp/gsionlocked-hyperos-preview
B=/data/local/tmp/gsionlocked-maintenance-backup-0.3.4.1
D=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
[ "$(getprop ro.product.model)" = SM-S911B ] || { echo 'Only the validated Galaxy S23 SM-S911B runtime is supported'; exit 3; }
[ -d "$S" ] && [ ! -L "$S" ] && [ -f "$S/session.sha256" ] || exit 3
[ "$(sha256sum "$S/session.sha256" | cut -d' ' -f1)" = 84f52013cd97d92c61a47d64f4a854a033b82127ddad0069822b358e1ca16d37 ] || { echo 'Runtime version mismatch; existing runtime preserved'; exit 3; }
(cd "$D" && sha256sum -c SHA256SUMS) || exit 4
[ ! -L "$B" ] || exit 4
mkdir -p "$B"; chown 0:0 "$B"; chmod 700 "$B"
for pair in "$S/fix-s23-daily-use.sh:startup-hook" /data/local/tmp/fix-s23-daily-use.sh:external-hook /data/local/tmp/gsionlocked-cool-profile.sh:cooling-script; do
 target=${pair%:*}; name=${pair##*:}
 [ ! -L "$target" ] || exit 4
 if [ ! -e "$B/$name.saved" ]; then
  if [ -f "$target" ]; then cp -p "$target" "$B/$name.original"; else touch "$B/$name.absent"; fi
  touch "$B/$name.saved"
 fi
done
for pair in "$D/performance.sh:$S/fix-s23-daily-use.sh" "$D/performance.sh:/data/local/tmp/fix-s23-daily-use.sh" "$D/cooling.sh:/data/local/tmp/gsionlocked-cool-profile.sh"; do
 source=${pair%:*}; target=${pair##*:}
 [ ! -L "$target.pending" ] || exit 4
 cp "$source" "$target.pending"; chown 0:0 "$target.pending"; chmod 700 "$target.pending"
 sh -n "$target.pending"; mv "$target.pending" "$target"
done
if [ "$(getprop ro.build.version.sdk)" = 37 ]; then sh "$S/fix-s23-daily-use.sh"; fi
echo 'MAINTENANCE_0.3.4.1_INSTALLED; existing APK and runtime manifest retained'
