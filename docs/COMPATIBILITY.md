# Compatibility

## EvoX / Android 16

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
| Microphone | Not working, reported by the owner; initial PCM probe captured silence ([issue #5](https://github.com/Murk1ins-ka/gsionlocked/issues/5)) |
| Flashlight | Not working, reported by the owner; passing torch API calls did not establish working light ([issue #1](https://github.com/Murk1ins-ka/gsionlocked/issues/1)) |
| SMS / verification codes | Not working; owner reports no incoming SMS. Further investigation deferred at the owner's request ([issue #2](https://github.com/Murk1ins-ka/gsionlocked/issues/2)) |
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

## ColorOS 17 / Find X9 Ultra IN

These results concern the separate prepared SM-S911B takeover, not the EvoX
runtime or a clean-device installation. See [profile](../profiles/dm1q-coloros17.json).

| Component | Evidence / limitation |
| --- | --- |
| Boot, display and touch | Guest desktop visible and touch confirmed by owner |
| Refresh and AOD | Peak 120 Hz, adaptive minimum; AOD explicitly disabled at each startup |
| Storage | Non-root app external-file and MediaStore image/download roundtrips passed |
| Firefox files | Actual browser download, system picker and local multipart upload passed; exact test contents verified |
| Photos | Two Open Camera JPEGs saved at 4080×3060; all four camera previews checked earlier, complete per-lens capture matrix pending |
| Audio | Owner confirmed sound; complete audio/microphone matrix pending |
| Bluetooth | Redmi 9C discovery/pairing checked; Bluetooth audio not fully verified |
| Calls / SMS | Owner confirmed calls; incoming SMS and Google codes may not arrive: [ColorOS #7](https://github.com/Murk1ins-ka/gsionlocked/issues/7); exact cause unresolved |
| PIN/password, fingerprint, face unlock | Password/PIN does not work: [ColorOS #6](https://github.com/Murk1ins-ka/gsionlocked/issues/6); biometrics unsupported, credential guards retained, do not enroll |
| SELinux | Guest still experimental/Permissive; full enforcing policy not completed |
| Temperature | Battery 36.8 °C, thermal status 0 after short camera test; endurance and sustained load not verified |
| Other devices / arbitrary GSI | Unsupported; prepared runtime required |
