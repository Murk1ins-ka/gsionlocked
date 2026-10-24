# dm1q runtime source snapshot

These are the current generated loader/guardian sources and core Java helpers from the tested SM-S911B FZG1 profile, including the lazy-service binder fix and audio stop-order fix. They do not contain device data or vendor binary blobs.

This snapshot is provided for review and porting. The Android app has its own standalone build. A reproducible build and fresh-device installer for the **entire** runtime is still pending. Do not mistake compiling `loader186.c` for preparing a runnable environment: APEX extraction, compatibility shims, framework/face patching, the software KeyMint service, fingerprint adapter and ABI libraries are separate dependencies.

The full environment also uses source derived from Android/AOSP and LineageOS. Preserve upstream notices and review provenance before distributing compiled runtime payloads. Do not substitute a running phone's data directory for a runtime package.
