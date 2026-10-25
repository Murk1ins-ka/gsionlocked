# Third-party material

Original project code uses the repository's Apache-2.0 license. Upstream-derived material and branding retain their respective terms.

- Android SDK/NDK and AOSP interfaces are external build/runtime dependencies. Relevant source and notices: https://source.android.com/ and https://android.googlesource.com/ . Existing source-file copyright/license notices are retained.
- The runtime is a device-specific adaptation of Android/LineageOS components. Device references: https://github.com/LineageOS/android_device_samsung_dm1q and https://github.com/LineageOS/android_device_samsung_sm8550-common . No claim is made that the runtime snapshot relicenses upstream code.
- The default APK build does **not** bundle KernelSU. It invokes the installed root manager's `su` executable. The optional `--root-client-apk` input accepts only the pinned official KernelSU v3.2.5 APK (SHA-256 `1417081413bf7ab1de8e440ecbcb62685037c8f28f048f0f8b79e305b31ab916`), extracts its unmodified arm64 client and runs it as a separate executable. Upstream project/corresponding source: https://github.com/tiann/KernelSU/tree/v3.2.5 . License: https://github.com/tiann/KernelSU/blob/v3.2.5/LICENSE . Preserve the GPL license and corresponding-source information if distributing that optional client.
- The gsionlocked logo and boot wallpaper supplied by the project owner are included as project assets. They are not covered by the application-source license and may not be reused to imply an official release.
- Samsung vendor firmware/binaries, Google apps, GSI system images and extracted framework jars are not included. Their licenses and distribution terms remain with their respective owners/projects.
