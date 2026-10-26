# Third-party material

Original project code uses the repository's Apache-2.0 license. Upstream-derived material and branding retain their respective terms.

- Android SDK/NDK and AOSP interfaces are external build/runtime dependencies. Relevant source and notices: https://source.android.com/ and https://android.googlesource.com/ . Existing source-file copyright/license notices are retained.
- The runtime is a device-specific adaptation of Android/LineageOS components. Device references: https://github.com/LineageOS/android_device_samsung_dm1q and https://github.com/LineageOS/android_device_samsung_sm8550-common . No claim is made that the runtime snapshot relicenses upstream code.
- The default APK build does **not** bundle KernelSU. It invokes the installed root manager's `su` executable. The optional `--root-client-apk` input accepts only the pinned official KernelSU v3.2.5 APK (SHA-256 `1417081413bf7ab1de8e440ecbcb62685037c8f28f048f0f8b79e305b31ab916`), extracts its unmodified arm64 client and runs it as a separate executable. Upstream project/corresponding source: https://github.com/tiann/KernelSU/tree/v3.2.5 . License: https://github.com/tiann/KernelSU/blob/v3.2.5/LICENSE . Preserve the GPL license and corresponding-source information if distributing that optional client.
- The gsionlocked logo and boot wallpaper supplied by the project owner are included as project assets. They are not covered by the application-source license and may not be reused to imply an official release.
- The application/source archive does not include Samsung vendor binaries, Google apps, GSI system images or extracted framework jars. Separate image/runtime release assets contain components from the user-supplied GSI and the prepared port; these retain their original Android, LineageOS, OPPO, Google and other upstream terms. They are not relicensed as application code. Host compatibility libraries are copied from the installing phone, not from another phone's userdata.
- The existing ColorOS supervisor and preparation adaptations originate from https://github.com/fahadalijaved23/OneOS. The curated, modified scripts and S23 patches are in `runtime/coloros-s23/`; the software image is a separate input. Upstream's NOTICE distinguishes OneLineage-derived session scripts from its Apache-2.0 code; this project does not claim to relicense those original scripts or grant third-party reuse permission.
- Runtime loader files with `SPDX-License-Identifier: GPL-2.0-only` retain that license; a copy is in `licenses/GPL-2.0.txt`. Matching adaptation sources are included under `runtime/dm1q/`. Android Bionic/NDK system libraries retain their upstream notices.
- The prepared ColorOS runtime includes Firefox and its libraries from the prepared stage. Firefox source and notices: https://www.mozilla.org/firefox/ and https://firefox-source-docs.mozilla.org/ . Mozilla/Firefox components retain their upstream licenses, including MPL terms; bundled APK notices are retained.

## OneOS Samsung porting source snapshot

`runtime/samsung-oneos/upstream` contains selected OneOS sources at commit
`73c3be36c35462742503380e1f025fead3cea48c`, with its LICENSE, NOTICE and CREDITS.
The source-only selection and SHA-256 provenance are recorded alongside it.
The app's Samsung model/device/SoC recognition data is adapted from that revision's
Profiles.kt. The OneLineage-derived `switcher/assets` session scripts are excluded
from this new snapshot under upstream NOTICE's restriction on reuse outside OneOS.
No new proprietary framework binaries or system images are included in this snapshot.

## HyperOS preparation

The HyperOS preparation uses the pinned OneOS patch source snapshot above and
the earlier S23 compatibility adaptations. The copied ABI, networking and ART
adaptation source under `runtime/hyperos-s23/patches` retains the project source
license; see the upstream NOTICE and existing runtime attribution above.
HyperOS/Xiaomi, Samsung and Android binaries remain external inputs and are not
included in the source tree or relicensed as application code. This preparation
does not import additional OneLineage-derived session assets.
