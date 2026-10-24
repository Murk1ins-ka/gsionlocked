# dm1q runtime source snapshot

These are generated loader/guardian sources and core Java helpers for the SM-S911B FZG1 profile. Profile 198 includes session persistence for `artd`, removal of inherited host app-standby blocking from leased guest UIDs, persistent installer storage views and audio handoff convergence. The snapshot contains no device data or vendor binary blobs.

The fixes were isolated on the development device: restarting the missing ART daemon released queued APK installs; a temporary UID lease let DownloadManager complete previously failing transfers. Play Store's installer mount view was empty even though normal apps could access guest storage. After connecting that view to guest FUSE, the device owner confirmed a successful download and installation.

Profile 198 creates a guest-only shared propagation root for user storage before Zygote starts. Installer and Android-writable views receive later FUSE mounts; apps continue through FUSE, without a raw media-directory bind. Guest `/cache` also lives on userdata. The namespace propagation test passed on the target kernel. The owner then confirmed a Play Store download and installation after a fresh profile-198 launch, without applying the live fix again.

`tests/storage_propagation_test.c` reproduces the late FUSE-mount propagation contract using private namespaces and a temporary tmpfs. It does not mount or modify the host media directory. The guardian also waits for the host audio service group to remain stopped before handoff, accounting for init's automatic audio HAL restarts.

`artd_session.c` is loaded only by the guest ART daemon. It uses Android's `AServiceManager_forceLazyServicesPersist` API to keep the service registered until guest cleanup. The root-owned profile must hash this helper along with the loader and guardian. Do not copy it into OneUI or preload it globally.

`tests/uid_lease_test.c` exercises lease creation, standby-rule removal, exact restoration and concurrent-change protection using a newly created, unpinned BPF map. It does not edit the live host map. Compile against `include/` with the target NDK and run as root on the development device.

This snapshot is provided for review and porting. The Android app has its own standalone build. A reproducible build and fresh-device installer for the **entire** runtime is still pending. Do not mistake compiling `loader198.c` for preparing a runnable environment: APEX extraction, compatibility shims, framework/face patching, the software KeyMint service, fingerprint adapter and ABI libraries are separate dependencies.

The full environment also uses source derived from Android/AOSP and LineageOS. Preserve upstream notices and review provenance before distributing compiled runtime payloads. Do not substitute a running phone's data directory for a runtime package.
