# v0.1.1-preview

Initial public source preview for gsionlocked.

- English UI with a rounded GSI selection panel, white text and animations.
- Prepared EvoX selection, raw ext4 image import and SHA-256 identification.
- Root check, persistent guest-storage settings and actual launch output.
- SM-S911B FZG1 profile checks and current runtime source snapshot.
- Standalone app build, format tests, publication audit and GitHub Actions.

The APK requires an already prepared, matching runtime. A clean-phone installer is not included. Only the exact SM-S911B/FZG1/EvoX combination is tested. Other firmware and devices are blocked; arbitrary image import does not imply launch support.

Known issues: flashlight, SMS/verification-code delivery and calls are reported broken and await device diagnosis. Guest KeyMint uses software. No days-long battery-life validation is claimed.

The APK includes the pinned official KernelSU client, its GPL license and corresponding upstream-source reference. The source archive excludes GSI images, proprietary vendor binaries, Google packages, phone data and signing keys.
