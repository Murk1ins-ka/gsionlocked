<p align="center"><img src="docs/assets/banner.svg" width="100%" alt="gsionlocked — GSI launcher for Galaxy S23" /></p>
<p align="center">
<a href="https://github.com/Murk1ins-ka/gsionlocked/actions/workflows/build.yml"><img src="https://github.com/Murk1ins-ka/gsionlocked/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
<a href="https://github.com/Murk1ins-ka/gsionlocked/releases"><img src="https://img.shields.io/github/v/release/Murk1ins-ka/gsionlocked?include_prereleases&label=preview&color=6d83bc" alt="Preview" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-8b9dc3" alt="Apache 2.0" /></a>
<img src="https://img.shields.io/badge/device-SM--S911B-353d50" alt="SM-S911B" />
</p>
<p align="center"><a href="https://github.com/Murk1ins-ka/gsionlocked/releases">Downloads</a> · <a href="docs/SETUP.md">Setup</a> · <a href="https://github.com/Murk1ins-ka/gsionlocked/issues">Known issues</a> · <a href="https://t.me/gsionlocked">Telegram</a> · <a href="README.ru.md">Русский</a></p>

> [!IMPORTANT]
> Source preview. A matching, prepared runtime is required; installing the APK alone on a new phone is not sufficient.

## gsionlocked — Booting GSI via Treble Userspace Takeover

Run a prepared Generic System Image, such as Evolution X / Android 16, over stock OneUI using the running Samsung kernel. The tested path starts a separate Android userspace without flashing physical system partitions or performing a cold kernel reboot.

### How it works

1. The app obtains root and verifies the device, GSI digest and prepared runtime files.
2. The loader maps guest system/data images and builds private mount, Binder, property and network environments.
3. A host-side guardian coordinates the handoff of OneUI services and shared hardware.
4. Guest Zygote, SurfaceFlinger and Android services start against the stock kernel and prepared vendor integration.
5. The guardian returns control to OneUI when the session ends; holding both volume buttons for two seconds requests a return.

This is a temporary userspace session with persistent guest storage. The tested root manager is **KernelSU**; exclusive compatibility with arbitrary **TEMPROOT** methods has not been established. The current implementation does not use a global `/system` replacement, a KernelSU magic-mount module or `ksud soft-reboot`. Those are possible alternative designs, not the shipped launch path.

### What this provides

- Switch Android userspace without a full hardware reboot.
- Keep stock system partitions unflashed and guest apps/files in a separate data image.
- Reuse the stock kernel and vendor components, with hardware support tracked in the [test matrix](docs/COMPATIBILITY.md).
- Restore host service ownership through the guardian. This remains an experiment; it does not carry a zero-risk or complete hardware-support guarantee.

### Play Store installation fix

**Runtime 198** connects installer storage views to the guest FUSE mount before apps start, keeps the guest ART service available and leases the required network rules for guest downloads. The owner confirmed Play Store installation again after returning to OneUI and starting a new EvoX session. **Start** requires runtime 198 or newer, verifies its hashes and reports the enabled fixes in its launch output. Updating the APK alone does not install the external prepared runtime.

**Preview release.** The tested combination is **SM-S911B / dm1q, OneUI S911BXXSAFZG1, EvoX 11.11 Android 16**. Newer firmware, other S23 variants, S23+ and S23 Ultra are not validated. The app checks compatibility before starting.

The app can import a raw ext4 `.img`, calculate its SHA-256, select the prepared image, change guest storage, check root, and display actual launch commands and output. Importing an image does not make it compatible: an unknown image cannot start through the dm1q profile.

## App controls

| Control | Action |
| --- | --- |
| **Select GSI** | Choose the prepared image or import a raw ext4 `.img`; verify SHA-256 |
| **Check root** | Check root access and the device profile |
| **Storage** | Allocate persistent guest storage; increasing it preserves data |
| **Start** | Start the prepared runtime and show real commands and logs |

The development phone has a prepared runtime. A bootstrap package for a clean phone is not included yet. Read the [setup status](docs/SETUP.md) before downloading an APK.

The repository does not contain a GSI image, Google apps, Samsung vendor binaries, phone data, signing keys, or a preconfigured userdata image. Use your own GSI file. The runtime source snapshot is included for porting and review; it is not a portable replacement for Android's DSU installer.

## GSI used in development

**Evolution X (EvoX) 11.11, Android 16 / SDK 36**, build **2026-09-20**, ARM64 A/B Treble, GApps variant.

- Original archive: `EvoX-11.11-GSI_treble_arm64-ab-GAPPS-20260920.img.xz`
- Decompressed raw ext4 image size: **3,863,474,176 bytes**.
- SHA-256 of the **decompressed `.img`**, not the `.xz` archive:

```text
5e8333e15798592bae059bcb78ba85394418afc8f035a13fc0e97c086b8c1e49
```

This is the image used by the tested dm1q profile, not a claim that other EvoX versions are interchangeable. The image and its bundled Google apps are not redistributed in this repository. See [profile metadata](profiles/dm1q-fzg1.json).

## Known issues

| Function | Status |
| --- | --- |
| [Flashlight](https://github.com/Murk1ins-ka/gsionlocked/issues/1) | Torch API test passed; physical light / Quick Settings confirmation pending |
| [SMS and verification codes](https://github.com/Murk1ins-ka/gsionlocked/issues/2) | User reported no incoming SMS; investigation deferred |
| [Phone calls](https://github.com/Murk1ins-ka/gsionlocked/issues/3) | Owner confirmed calls work; full incoming/outgoing/audio matrix pending |

See the full [compatibility matrix](docs/COMPATIBILITY.md). These are open bugs, not completed fixes.

## Build the app

Requirements: Python 3.10+, JDK 17, Android SDK platform 35, build-tools 35.0.0, NDK 25.2.9519653.

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;25.2.9519653"
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
python tools/test.py
python tools/build_app.py --debug
```

Output: `dist/gsionlocked.apk`. On Windows, set the same environment variables in PowerShell or pass `--sdk`, `--ndk`, and `--java-home` explicitly.

For a release, keep your key outside this checkout:

```sh
export GSIONLOCKED_STORE_PASS='your-store-password'
export GSIONLOCKED_KEY_PASS='your-key-password'
python tools/build_app.py --keystore /private/release.jks --alias gsionlocked
```

The default build invokes the installed root manager's `su`. A pinned official KernelSU client can optionally be supplied at build time; see [third-party components](THIRD_PARTY.md). Root access must be granted by the device owner.

## On a prepared, supported phone

1. Open **Select GSI** and choose the prepared EvoX image, or import the matching raw `.img`.
2. Press **Check root** and grant access in KernelSU.
3. Set **Storage**. Increasing it preserves existing data. It cannot shrink an allocated filesystem.
4. Press **Start**. Launch commands and real session output appear in the app.
5. Hold both volume buttons for 2 seconds to return to OneUI.

The runtime temporarily changes host service ownership and SELinux mode, and uses a guardian to restore OneUI. It uses the stock kernel and vendor stack with a separate guest data image; it does not flash system partitions. Software KeyMint is used. This is an experimental runtime, not a finished daily-driver ROM or a guarantee of hardware-backed guest security.

## Development

- [Compatibility and test matrix](docs/COMPATIBILITY.md)
- [Runtime architecture](docs/ARCHITECTURE.md)
- [Build and release checklist](docs/RELEASING.md)
- [Runtime source snapshot](runtime/dm1q/README.md)
- [Telegram](https://t.me/gsionlocked)

The Android application ID remains `dev.tempgsi.controller` to preserve the existing runtime/controller contract. The visible app name is gsionlocked.
