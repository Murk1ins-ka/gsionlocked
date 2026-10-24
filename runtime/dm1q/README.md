# dm1q runtime source snapshot

These are generated loader/guardian sources and core Java helpers for the SM-S911B FZG1 profile. Profile 195 adds session persistence for `artd` and removes inherited host app-standby blocking from leased guest UIDs. It retains the image-service binder and audio stop-order fixes. The snapshot contains no device data or vendor binary blobs.

The fixes were isolated on the development device: restarting the missing ART daemon released queued APK installs; a temporary UID lease let DownloadManager complete previously failing transfers. The combined profile still needs full Play Store and hardware regression checks. Removing a README entry does not mean those checks passed.

`artd_session.c` is loaded only by the guest ART daemon. It uses Android's `AServiceManager_forceLazyServicesPersist` API to keep the service registered until guest cleanup. The root-owned profile must hash this helper along with the loader and guardian. Do not copy it into OneUI or preload it globally.

`tests/uid_lease_test.c` exercises lease creation, standby-rule removal, exact restoration and concurrent-change protection using a newly created, unpinned BPF map. It does not edit the live host map. Compile against `include/` with the target NDK and run as root on the development device.

This snapshot is provided for review and porting. The Android app has its own standalone build. A reproducible build and fresh-device installer for the **entire** runtime is still pending. Do not mistake compiling `loader195.c` for preparing a runnable environment: APEX extraction, compatibility shims, framework/face patching, the software KeyMint service, fingerprint adapter and ABI libraries are separate dependencies.

The full environment also uses source derived from Android/AOSP and LineageOS. Preserve upstream notices and review provenance before distributing compiled runtime payloads. Do not substitute a running phone's data directory for a runtime package.
