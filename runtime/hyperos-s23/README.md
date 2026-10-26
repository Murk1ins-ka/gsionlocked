# HyperOS 4 preparation for SM-S911B

This is an unfinished port, not an enabled launch profile. The released APK
does not yet install or start this image. `profiles/dm1q-hyperos4.json` records
its identity with `launch_enabled: false`.

The owner's `hyper.img` is a 7,203,971,072-byte raw ext4 ARM64 MysticGSI image:
HyperOS 4 CN, `OS4.0.0.39.XPBCNXM`, Android 17 / SDK37. SHA-256:
`a0d622cff955e907b89bf7691d6d12102587bf9333ad89e92aebee0fe57b12ba`.
No firmware image, extracted framework, host binaries or user data are committed.

## Prepared components

- 34 APEX payloads and hash-pinned local-extraction recipes from this image.
- Guest services guards for vold keys, gatekeeper, Weaver, biometric cleanup,
  FRP writes, host storage/keystore maintenance, power commands and secondary
  users. Eight user/profile creation paths were checked by the upstream guard.
- A HyperOS-specific XSpace repair omission, pinned to the inspected method,
  followed by the full single-user validation. The original repair function
  used a temporary SparseArray that triggered the conservative writer check;
  the validator itself was not weakened.
- The guest lock extension uses its base implementation instead of loading
  Xiaomi's hardware/old-credential plugin. This is not password/biometric support.
- Guest-only ARM64 ABI arrays, including HyperOS MTK/fake-32-bit arrays, and
  the legacy netstats flag for the S23 host.
- A guarded installd proxy with 25 permitted methods using per-host dispatch;
  destructive/unmatched methods remain blocked. The generated HyperOS proxy
  initially denied 44 methods.
- Pinned native gvld/incremental candidates and three guest Java lookups renamed
  to gvld. The actual instruction/string references were inspected; no native
  command was executed on the device during preparation.
- An ART adapter with 20 selected host wire methods and 21 unsupported commands
  refused, including incompatible dexopt/pre-reboot cleanup requests.

## ART findings and live checks

The S23's live ART module SHA-256 matched the captured Android 16 input:
`17d44eafadfae9616ff3a90620342933a14531eb1c924523b4dc40ed2893139b`.
HyperOS's module is
`24bd859f09fcfbe9f07be85a97147dc57b70667849aac67162b99ea132b13543`.

Eighteen inspected parcel writers match. `GetDexoptStatusResult` adds a final
`dex2oatCmdline` string. The old Samsung reply leaves that field null in the
original guest object, while a later HyperOS model constructor rejects null.
The candidate initializes the field to an empty string. Field order was checked,
and a real Android Parcel probe on the S23 confirmed host-to-guest decoding and
old-host skipping of the new trailing field in the reverse direction. The probe
uses shell UID and makes no Binder calls or guest/system service changes.
The append-only shape follows the [structured AIDL compatibility rules](https://source.android.com/docs/core/architecture/aidl/stable-aidl).

That probe also caught a builder bug: APKTool 3.0.3's API35 writer emitted
DEX041 magic with a 112-byte header. Android rejected it before loading the
class. `AssembleSmali` now uses API34's valid legacy format and rejects an
inconsistent output header. All affected HyperOS candidate jars were rebuilt;
the earlier invalid candidates are superseded and must not be used.

The image and staged components were checksum-verified under the separate root
directory `/data/local/tmp/gsionlocked-hyperos-preview`; directory mode 0700,
image/component modes 0444. One UI and temporary root remained available,
SELinux stayed Enforcing, and no guest switch or partition write was performed.

## Reproduction

External inputs: the owner's raw image, APKTool 3.0.3, JDK, Android SDK
build-tools 35.0.0 and a host capture. Use new output directories.

1. Compile `tools/InspectGsi.java` with `GsiFormat`, `ReadOnlyExt4` and
   `RuntimeBundle`. Run it with `--framework`, `--apex` or `--native` to obtain
   system-only inputs. It never mounts the image.
2. Run `tools/prepare_gsi_apex.py` against the APEX inspection directory,
   passing the pinned image hash, to derive payloads and recipes.
3. Run `tools/build_hyperos_framework.py` with the framework inputs, the captured
   host installd table, APKTool, SDK and JDK. The normal path decodes fresh
   inputs and applies all guards; `--prepared-trees` explicitly records reuse
   of a developer's already patched trees, not a clean patch execution.
4. Run `tools/hyperos_native_vold.py` against the native inspection inputs.
5. Read `/javalib/service-art.jar` from the extracted ART payload, capture the
   host ART jar, then run `tools/build_hyperos_art.py`.
6. Run `python tools/test.py` and additionally
   `python tools/test_smali_assembler.py --apktool /path/to/apktool.jar`.

The S23 checks used prepared smali trees with logged patch passes and a corrected
single-user patch attempt. Rebuilt jars passed assembly, ZIP CRC and unchanged
entry checks. Native outputs changed only the inspected byte ranges. These
checks do not prove a full Android guest will start.

## Work still required before launch

- Complete MIUI extension and native dependency review, including other key,
  storage and service calls outside the intercepted lock plugin.
- Integrate the complete guest mount/classpath/property stage, ART daemon
  namespace/profile isolation and app-installation behavior.
- Validate host recovery/return and the actual HyperOS desktop, input, display,
  network and storage on the phone.
- Enable an app launch profile only after those checks. No HyperOS boot success
  or password/biometric support is claimed.

For the following app revision the owner requested the OneOS app as the UI base,
a gsionlocked design and the project's GSI catalog. Preserve upstream app
licenses and credits. The upstream NOTICE's separate restriction on the
OneLineage-derived session assets still applies; an app-source fork does not
grant permission to reuse all runtime assets.
