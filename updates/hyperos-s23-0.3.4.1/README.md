# gsionlocked 0.3.4.1 - HyperOS S23 maintenance

Maintenance update for the already prepared HyperOS guest on Galaxy S23 SM-S911B. Experimental, matching runtime required. This is not a universal firmware installer.

## Downloads

- gsionlocked-0.3.4-hyperos-preview.apk: the original signed 0.3.4 APK extracted from the development phone. Its code, signature and versionCode are unchanged. The maintenance settings are NOT embedded in this APK.
- gsionlocked-hyperos-s23-maintenance-0.3.4.1.zip: the separate root update for an existing matching runtime.
- SHA256SUMS: release asset checksums.

## Maintenance changes

Requests minimum/peak 120 Hz and animation scales 0.5 through the S23 startup hook. Reduces excessive diagnostic logging and main/system logcat buffer sizes. Warnings, errors, and tags used by audio/return supervision remain available. Thermal protection and CPU/GPU frequency controls are retained.

In a short repeated launcher-swipe test, logd CPU use changed from 5.61% to 2.07% of one core; the panel reported 120 Hz. End-of-test SKIN temperature was 33.8 C and Thermal Status was 0. These tests do not establish a universal speedup or absence of heating during games or charging. The reported problematic overheating scenario has not yet been reproduced.

## Install the maintenance ZIP

Requires root, ADB, and the prepared HyperOS S23 runtime whose manifest hash matches the installer. Other runtimes are rejected. Back up important data first. No system.img is included and no physical partition is flashed.

Extract the ZIP on your computer, then run:

```text
adb push hyperos-s23-0.3.4.1 /data/local/tmp/
adb shell "su -c 'sh /data/local/tmp/hyperos-s23-0.3.4.1/install-update.sh'"
```

Inside HyperOS the profile applies immediately; from One UI it applies on the next guest start. Script backups are kept in /data/local/tmp/gsionlocked-maintenance-backup-0.3.4.1. Original logging settings are stored in /data/local/tmp/gsionlocked-cool-backup. Protected runtime scripts and their manifest are preserved.

Restore logging settings, keeping 120 Hz:

```text
adb shell "su -c 'sh /data/local/tmp/hyperos-s23-0.3.4.1/restore-cooling.sh'"
```

The packaged installer was tested successfully on the original S23: checksums passed and both profiles applied. Installation on another phone is unverified. The APK is provided to preserve the installed app version; it is not a rebuilt app containing the new settings.
