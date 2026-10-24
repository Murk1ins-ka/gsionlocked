# Setup status

## Already prepared development phone

The app uses `/data/local/tmp/gsionlocked-profile.properties`, owned by root with mode 0600. This file identifies the matching runtime directory, entry class, dex jar and SHA-256 values. The profile is checked before launch. The selected GSI must match the known image digest in `profiles/dm1q-fzg1.json`.

App 0.1.2 requires runtime 198 or newer, including the matching loader, guardian, jar and `artd-session.so` hashes. This prevents starting an older prepared environment without the persistent Play Store installation fix. Installing the APK does not replace runtime files. The original development phone has runtime 198 installed; clean-device provisioning remains separate work.

The runtime also requires the extracted GSI/APEX environment, compatibility helpers, image-service namespace and host ABI libraries prepared by the original experiment. These are not supplied by the APK or the source-only archive.

## New phone

Do not copy another phone's `/data`, EFS, calibration, keystore, biometrics, SIM/modem state or userdata image. Those are not an installation bundle.

Run the read-only audit:

```sh
python tools/check_device.py --adb /path/to/adb
```

The audit checks model, firmware and whether the runtime profile exists. It does not install a runtime or modify Android. A passing model/firmware check alone is not a complete installation.

A reproducible clean-device bootstrap is still required before this preview can become a public end-user release. It must extract required system files from a user-provided GSI, obtain host/vendor files from that same phone, build the adapters from source, prepare a fresh private data volume and atomically install a complete profile. Publishing an APK does not complete those steps.

## GSI input

Use a decompressed raw ext4 `.img`, not `.xz`, `.zip`, Android sparse or EROFS. The file picker uses Android's document provider; broad storage access is not requested. Imports are copied into the app's private storage with bounded size, a free-space reserve, SHA-256 calculation and atomic replacement. The source file is left intact. Both source and imported copy occupy space if both are kept.

The only launchable image in this preview is the exact EvoX image listed in the profile. Other valid ext4 images may be imported and identified, but Start explains that no tested runtime profile is available for them.

Use the same signing key for upgrades. Uninstalling the app deletes its private imported copy and preferences; it does not delete the separate guest data volume.
