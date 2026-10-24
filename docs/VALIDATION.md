# Validation — 2026-10-24

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
