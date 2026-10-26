# Setup status

## 0.3.1 automatic first-start setup

Use **Select GSI → Browse files → import the matching .img → Start**.
The app downloads and verifies a separate ColorOS/EvoX package, checks the host
framework and prepares missing files automatically, then continues launch checks.
There is no separate Install runtime action. See [runtime installation](RUNTIME_INSTALLER.md).
Initial packages target the existing S23/FZG1 port and have offline validation;
fresh installation/boot on hardware is not yet established. This is not an
automatic port for arbitrary devices. Existing runtime folders are preserved.

## Already prepared development phone

For **ColorOS 17**, use the separate [ColorOS setup](COLOROS.md). The EvoX
properties file, storage resizing and runtime-198 contract below apply to EvoX.

The app uses `/data/local/tmp/gsionlocked-profile.properties`, owned by root with mode 0600. This file identifies the matching runtime directory, entry class, dex jar and SHA-256 values. The profile is checked before launch. The selected GSI must match the known image digest in `profiles/dm1q-fzg1.json`.

App 0.1.2 requires runtime 198 or newer, including the matching loader, guardian, jar and `artd-session.so` hashes. This prevents starting an older prepared environment without the persistent Play Store installation fix. Installing the APK does not replace runtime files. The original development phone has runtime 198 installed; clean-device provisioning remains separate work.

The runtime also requires the extracted GSI/APEX environment, compatibility helpers, image-service namespace and host ABI libraries prepared by the original experiment. These are not supplied by the APK or the source-only archive.

## New phone

Do not copy another phone's `/data`, EFS, calibration, keystore, biometrics, SIM/modem state or userdata image. Those are not an installation bundle.

Run the read-only audit:

```sh
python tools/check_device.py --adb /path/to/adb
```

The audit reports model/firmware as tested or untested, but does not reject by
their names. It checks Samsung manufacturer/brand, Android 16 / ARM64 and whether the runtime profile exists.
It does not install a runtime or modify Android. An accepted host platform alone
is not a complete installation. Model-name restrictions were removed in 0.2.1;
device-specific adapters and the ColorOS host-framework digest remain required.

The 0.3.0 installer supplies the curated prepared dependencies as separate,
hash-pinned runtime archives and copies declared host components locally.
A fully source-reproducible bootstrap and adapters for arbitrary firmware remain
separate work. The provided packages are not a phone userdata snapshot.

## GSI input

Use a decompressed raw ext4 `.img`, not `.xz`, `.zip`, Android sparse or EROFS. The file picker uses Android's document provider; broad storage access is not requested. Imports are copied into the app's private storage with bounded size, a free-space reserve, SHA-256 calculation and atomic replacement. The source file is left intact. Both source and imported copy occupy space if both are kept.

The launchable profiles are the exact EvoX image and the exact ColorOS 17 IN image
listed in `profiles/`. Each requires its own prepared runtime. Other valid ext4
images may be imported and identified, but cannot launch without a matching port.

Use the same signing key for upgrades. Uninstalling the app deletes its private imported copy and preferences; it does not delete the separate guest data volume.
