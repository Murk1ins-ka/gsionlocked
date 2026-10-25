# Validation — 2026-10-24

## ColorOS launcher — 2026-10-25

- Android/native build and APK signature verification passed. Release signing
  certificate matches 0.1.2, so the app updates in place on One UI.
- Source/profile tests pass; the pinned ColorOS digest and runtime manifest are
  checked independently of the EvoX profile. No phone data or image is packaged.
- Host app root check, full-image selection, dedicated password warning and
  the styled launch sheet were exercised on SM-S911B / One UI Android 16.
- A native host preview posted visible full-screen logs over the bundled project
  wallpaper and exited cleanly. Host-mode logs read only the takeover progress
  file; they do not collect host app/message logcat. The old WebView screen is off.
- The first APK launch exposed restrictive overlay directory permissions;
  explicit 0755 modes and normalized launcher umask address that path.
- A diagnostic regular-file descriptor added to zygote caused an allowlist abort.
  It was removed; zygote stdio is again `/dev/null`. Diagnostic rendering runs in
  a separate app_process, never in the zygote fork path.
- An automatic return after that failed guest boot restored One UI with a stable
  system_server, SELinux Enforcing and no unreadable host APEX files. A fresh
  root request from the updated host APK then passed. This verifies that return
  without restoring temporary root manually; it is not a universal guarantee.
- The native startup lock now closes across exec. A boot/PID/command owner record
  protects the active startup without letting persistent watchdogs retain a lock.
- The final candidate reached `sys.boot_completed=1`, SystemUI and a visible
  ColorOS desktop with core PID 20050. The touch controller was enabled, AOD
  read back off and peak refresh read back 120 Hz. Firefox loaded an external
  HTTPS page. A fresh non-root storage probe passed external app files and
  MediaStore image/download write-publish-read roundtrips, including cleanup.
- The signed launcher APK installed inside that guest. Its return action is a
  private-file request and does not grant guest apps root. Healthy-session return
  is a separate validation from the earlier automatic return. Prior browser
  upload/camera evidence is listed in the compatibility table; these checks do
  not establish that every application or hardware feature works.

## App/source preview

- Standalone Android app compilation and APK signature verification passed.
- A source-only copy builds without the original workspace and without a bundled root client.
- Desktop tests reject sparse, truncated, unaligned, oversized and non-ext4 inputs and accept the supported raw ext4 layout.
- The public-source audit passed: no phone data, private signing keys, images, proprietary binary payloads or detected access tokens are included.
- Prepared-image selection and the signed app/native-launcher path were exercised on the development phone. The selection panel compiled and installed; final layout and raw-file import still require device UI validation.
- Ordinary-app HTTPS, Google account/gstatic DNS queries and the Google sign-in page in WebView passed. The user reported no Google error in that session. Account sign-in and Play Integrity certification are not established by these checks.

## Existing runtime

On the original development phone, the image-service connection fix passed a full image SHA-256 check. The audio stop-order fix then passed a bounded guest boot with `sys.boot_completed`, guardian exit 0 and restoration to OneUI/SELinux Enforcing. The 80 GiB data volume retained its filesystem UUID and was used by that successful boot.

This is evidence for one prepared device. A second-phone clean install, newer OneUI firmware and arbitrary GSI launch have not been validated.
