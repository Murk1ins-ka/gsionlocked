<p align="center"><img src="app/src/main/res/drawable-nodpi/brand_logo.png" width="144" alt="gsionlocked logo" /></p>
<h1 align="center">gsionlocked</h1>
<p align="center">GSI launcher and experimental userspace runtime for Galaxy S23.</p>
<p align="center">
<a href="https://github.com/Murk1ins-ka/gsionlocked/actions/workflows/build.yml"><img src="https://github.com/Murk1ins-ka/gsionlocked/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
<a href="https://github.com/Murk1ins-ka/gsionlocked/releases"><img src="https://img.shields.io/github/v/release/Murk1ins-ka/gsionlocked?include_prereleases&label=preview&color=6d83bc" alt="Preview" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-8b9dc3" alt="Apache 2.0" /></a>
<img src="https://img.shields.io/badge/device-SM--S911B-353d50" alt="SM-S911B" />
</p>
<p align="center"><a href="https://github.com/Murk1ins-ka/gsionlocked/releases">Downloads</a> · <a href="docs/SETUP.md">Setup</a> · <a href="https://github.com/Murk1ins-ka/gsionlocked/issues">Known issues</a> · <a href="https://t.me/gsionlocked">Telegram</a> · <a href="README.ru.md">Русский</a></p>

> [!IMPORTANT]
> Source preview. A matching, prepared runtime is required; installing the APK alone on a new phone is not sufficient.

**Preview release.** The tested combination is **SM-S911B / dm1q, OneUI S911BXXSAFZG1, EvoX 11.11 Android 16**. Newer firmware, other S23 variants, S23+ and S23 Ultra are not validated. The app checks compatibility before starting.

The app can import a raw ext4 `.img`, calculate its SHA-256, select the prepared image, change guest storage, check root, and display actual launch commands and output. Importing an image does not make it compatible: an unknown image cannot start through the dm1q profile.

## Current installation status

This is a source preview for the working device-specific experiment. **Installing the APK alone on a new phone is not sufficient.** It requires a prepared, matching userspace runtime. The original development phone has that runtime; a clean-device bootstrap package has not yet been validated or included. See [setup status](docs/SETUP.md) before downloading an APK.

The repository does not contain a GSI image, Google apps, Samsung vendor binaries, phone data, signing keys, or a preconfigured userdata image. Use your own GSI file. The runtime source snapshot is included for porting and review; it is not a portable replacement for Android's DSU installer.

## Known issues

| Function | Status |
| --- | --- |
| [Flashlight](https://github.com/Murk1ins-ka/gsionlocked/issues/1) | Reported broken; device diagnosis pending |
| [SMS and verification codes](https://github.com/Murk1ins-ka/gsionlocked/issues/2) | Sending and receipt not validated |
| [Phone calls](https://github.com/Murk1ins-ka/gsionlocked/issues/3) | Call setup and audio not validated |

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
