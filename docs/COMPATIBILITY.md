# Compatibility

| Component | Evidence |
| --- | --- |
| SM-S911B / dm1q / OneUI S911BXXSAFZG1 | One physical development device tested |
| EvoX 11.11 Android 16, exact profile hash | Boot completed in bounded regression test |
| Guest storage | Expanded from 10 to 80 GiB; filesystem check, UUID preservation and subsequent boot verified |
| Bluetooth | User confirmed pairing with Redmi 9C |
| PIN | User confirmed setting, persistence and unlock |
| Fingerprint | User confirmed enrollment, unlock, persistence and corrected icon |
| Face | User confirmed enrollment and unlock; RGB/software implementation |
| Camera | User confirmed operation. Foreground Camera2/MediaRecorder test saved a readable three-second 640×480 H.264 MP4 with an AAC track; audible microphone capture and all lenses remain unverified |
| Microphone | Initial PCM probe returned zero samples; further capture testing required |
| Flashlight | Camera API successfully enabled and disabled the rear torch; Quick Settings tile and physical light confirmation remain pending |
| SMS / verification codes | User reported no incoming SMS; further investigation deferred at the owner's request |
| Phone calls | Owner confirmed calls work in EvoX; separate outgoing/incoming and two-way audio checks remain pending |
| App downloads and installation | Owner confirmed Play Store download and installation both after the live storage fix and after a new profile-198 session. Installer FUSE views, ART session persistence and inherited standby filtering are fixed in that runtime ([issue #4](https://github.com/Murk1ins-ka/gsionlocked/issues/4)) |
| Google login/network/storage | Debugged in the development environment; not certified across devices |
| Battery life / temperature | No days-long battery-life validation |
| Hardware-backed guest KeyMint | Not supported; software implementation |
| Newer OneUI firmware | Not tested; app blocks it |
| S23+, S23 Ultra, other regional S23 models | Not tested; app blocks them |
| Arbitrary third-party GSI | Import/identification only; launch requires a matching port |
| APK-only installation on a clean phone | Not available in this source preview |

Changing this table or removing a model check does not create compatibility. Validate vendor ABI, service identities, display/input ownership, watchdog restoration, storage namespace, SELinux restoration and hardware on each new combination.

Background on the GSI/vendor-interface relationship: [AOSP GSI documentation](https://source.android.com/docs/core/tests/vts/gsi).
