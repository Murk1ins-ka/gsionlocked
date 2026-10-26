# 0.3.1-samsung-preview

Samsung-only host checks, local Compatibility report, and 13 OneOS reference
identity tuples. Added 106 pinned OneOS porting source files, provenance checks
and an offline overlay hash validator. OneLineage-derived session assets are
excluded under upstream NOTICE. **No new device runtime or Android 17 backend
is enabled.** Existing S23/FZG1 runtime downloads stay on the 0.3.0 release.
Desktop tests and APK build/signature checks passed; no phone was used.
See [Samsung porting status](SAMSUNG_PORTING.md).

# 0.3.0-runtime-preview

Added **Install runtime** for the exact supported ColorOS/EvoX images. The APK
downloads a pinned package, verifies every file, checks the host ABI framework,
copies declared host components locally and publishes a prepared environment.
No guest is automatically launched. Existing runtime directories are preserved.
Offline archive/filesystem/rollback tests pass; a fresh physical-device install
and subsequent boot have not been tested. Initial packages target the existing
S23/FZG1 port, not arbitrary devices. See [installer](RUNTIME_INSTALLER.md).

# 0.2.1-preview

Removed model/codename and bootloader-name blocks from the app, ColorOS wrapper
and new EvoX loader source. Other models can use the interface and reach runtime
preflight. Android 16 / ARM64, image/runtime hashes, credential guards and the
ColorOS host-framework digest are retained. This does not provide a universal
runtime or establish compatibility with another device. Existing runtime binaries
are not rebuilt by an APK update. Untested hosts show an informational notice.
Native Android 17 phones are no longer identified as a ColorOS guest by SDK alone;
the guest environment marker must exist too.

# 0.2.0-coloros-preview

Added a separate ColorOS 17 / Find X9 Ultra IN profile alongside EvoX in Select
GSI, preserving the existing gsionlocked interface. Includes matching-host/runtime
checks, complete image SHA-256 verification, dedicated native launch, a styled
confirmation sheet, a screen-lock warning at selection, and a styled guest return
sheet/progress screen. Prepared runtime updates the guest launcher after boot.
The optional root-preserving return remains experimental with reboot fallback.
A healthy-session return restored One UI without reboot, but app root failed
afterwards (SIGSYS/seccomp); restoring temporary root may still be necessary.
Native startup logs close at the OPPO animation. AOD is disabled by the prepared
runtime and its status label is omitted from the interface; adaptive 120 Hz stays.

Exact GSI: `OneOS_ColorOS17_FindX9Ultra_IN.img`, Android 17 / SDK 37, raw ext4,
12,069,945,344 bytes. SHA-256:

```text
a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0
```

Image archives were subsequently added as separate release assets; see
[GSI downloads](GSI_DOWNLOADS.md). The APK/source archive does not include the
images or private prepared runtime. This APK requires the
matching prepared SM-S911B environment. Importing a GSI does not build a port.
ColorOS PIN/fingerprint/face unlock remain unsupported and incoming operator SMS
is unresolved. EvoX compatibility results are separate. See [ColorOS](COLOROS.md).

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
