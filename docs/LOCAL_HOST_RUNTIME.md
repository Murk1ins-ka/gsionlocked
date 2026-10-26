# Local host runtime work — 2026-10-26

This work is part of replacing a single-phone prepared environment with preparation
from each Samsung's own system under temporary root. **It is not yet a universal
launch backend, nor evidence that another Samsung has booted.**

## Implemented in current source

- `HostPrepareMain` captures only fixed `/system/framework` JARs and selected
  `/system/lib64` libraries, resolving files only within immutable `/system` or
  `/apex` roots. No account, app, EFS, keystore or modem data is collected.
- The root-owned local capsule lives below
  `/data/local/tmp/gsionlocked-host-runtimes/<content-hash>` and contains copied
  system inputs, their provenance hashes and a generated installd mapping.
  Boot-class-path `framework.jar` definitions take precedence; `services.jar`
  is used only when the interface is absent from readable framework DEX.
  A corrupt/stripped framework is not silently treated as a compatible fallback.
  Cache identity includes framework and selected library contents; firmware/APEX
  changes produce another capsule. Existing capsules are verified and preserved.
- First-start preparation invokes this capture on Samsung ARM64 hosts with
  Android 12+ before checking the currently available launch backend. This does
  not relax the backend's Android 16 or S23 runtime checks.
- `DexBinderTable` extracts transaction constants and method prototypes without
  executing/loading captured framework code. It supports standard DEX and the
  logical-container layout in DEX 041. Offsets, hashes, counts, duplicate classes,
  ambiguous method names and transaction collisions are checked.
- The guest adapter `GsionlockedInstalldMap` resolves permitted calls from the
  locally generated mapping with an exact method signature. Destructive calls
  remain denied even if their codes are present in the host table.
- The offline builder patches **already guarded** OneOSInstalldProxy calls to
  dynamic lookup and assembles a candidate services JAR. Existing denied methods
  remain denied; only a pinned guarded input JAR is accepted.
- An integration builder connects local capture to that candidate: it prepares
  launcher/guest-start overrides and a dedicated read-only map bind before pivot,
  only in the private guest namespace. It updates candidate guard hashes but does
  not install them or relax any current platform gates.

DEX format and Binder compatibility references:
[AOSP DEX](https://source.android.com/docs/core/runtime/dex-format),
[AOSP stable AIDL](https://source.android.com/docs/core/architecture/aidl/stable-aidl).
The host framework's internal Binder interfaces do not acquire a cross-version
compatibility guarantee just from their Java method names; full runtime/hardware
validation is still required.

## Offline checks completed

- Extracted 78 installd transactions from the previously saved Samsung host
  `services.jar`, including its two-logical-file DEX 041 container.
- Compiled real synthetic Android Binder fixtures with D8: changed host IDs are
  extracted without model-name assumptions. Corruption, invalid offsets/counts,
  duplicate definitions/codes and ambiguous signatures are rejected.
- Capsule tests copy fixture system files and libraries, generate the second
  host's distinct code table, verify hashes, preserve existing directories and
  reject unexpected/private metadata. These are not live SELinux/permission tests.
- Dispatch tests preserve signature matching and the destructive-operation guard.
- The actual guarded guest proxy has 25 permitted methods adapted; its existing
  33 denied-method blocks are retained. Candidate assembly is tested separately.
- The binding script passes shell syntax and rejects the host namespace before
  mutation in a test with fake identity/mount commands. A real namespace/bind and
  actual Android file labels are not validated by that test.

Run: `python tools/test_host_abi.py` (JDK and Android SDK build-tools 35.0.0).

Candidate builder (external APKTool 3.0.3, no phone access):

```sh
python tools/build_samsung_dispatch.py \
  --base-services /path/to/pinned/services_patched.jar \
  --apktool /path/to/apktool.jar --sdk /path/to/android-sdk \
  --java-home /path/to/jdk --output /path/to/new-build-directory
```

## Still required for the requested end state

1. Validate the candidate read-only binding on-device and ship a signed common
   runtime with updated guard hashes. The candidate source integration is built;
   the current published runtime does not contain it.
2. Replace the internal S23 checks and device-specific paths with captured
   platform contracts, without simply deleting compatibility validation.
3. Package local guest-component recipes and the remaining bootstrap components
   into the APK/catalog. Direct extraction and installer format 2 are implemented
   below; published packages still use format 1.
4. Adapt display/composer, input, vendor namespaces, service ownership and return
   across Samsung platforms/host SDKs. Java transaction signatures alone do not
   prove Parcelable layout or HAL/native compatibility.
5. Validate actual start/desktop/input/storage and recovery on the relevant
   Samsung combinations. No other-device hardware validation is claimed.

Current release downloads remain unchanged until a replacement backend is ready.
The project goal remains active; implementing local extraction is an intermediate
step toward it, not completion.

## Local guest components (development)

`ReadOnlyExt4` reads raw extent-based ext4 images without mounting them.
`GuestImageComponents` reads APEX or nested compressed APEX ZIP streams and writes
only declared payload names, checking exact length and SHA-256 before publication.
It rejects unsupported filesystem features, malformed extents/directories,
excessive nesting and existing output files. No native image utility is needed.

On the selected ColorOS Find X9 Ultra IN image (SHA-256
`a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0`), all 27
APEX payloads were extracted locally and matched the prepared runtime's pinned
hashes. This validates image extraction, not another Samsung's boot compatibility.

Runtime package format 2 keeps `sha.<target>` for each component and adds
`image.<target>=/system/apex/<module>.apex` (or `.capex`) and `bytes.<target>`.
Such entries must be absent from ZIP payloads. The installer first copies and
hash-verifies the selected GSI, then derives these modules from that verified
copy before publishing any runtime root. Format 1 remains supported.

Run `python tools/test_image_components.py` for synthetic ext4/APEX fixtures,
malformed input rejection, existing-data protection and complete format-2 file
installation, including a bad module digest that must not publish runtime files.
The released catalog and archives have not yet switched to this format.

`tools/DeriveRuntimePackage.java` converts an authenticated format-1 ColorOS
archive using those pinned recipes, verifies the resulting format-2 archive,
and preserves existing outputs. It retains all framework/script guards.
The local conversion removed 298,369,024 unpacked bytes (27 APEX payloads),
reducing the compressed candidate from 555,545,955 to 422,264,379 bytes.
The remaining archive still includes bootstrap patches and application assets;
this is not yet a runtime-free APK. Conversion plus installation is covered by
the synthetic image tests. No new runtime archive has been published.

## Host display service discovery (development)

The host capture also reads bounded `.rc` files directly within immutable
`/{system,system_ext,vendor,odm,product}/etc/init` directories. It identifies a
unique running service declaring the default composer AIDL or HIDL interface.
It records the service name, interface, executable, source path and source hash;
these facts participate in the capsule cache identity. No service is restarted
during capture. Imports, properties and init commands are not executed.

The candidate integration now uses this captured service instead of the fixed
Qualcomm service name, and checks its captured AIDL/HIDL interface for readiness.
Missing, duplicate or critical service definitions prevent candidate takeover.
This is deliberately conservative: imports outside the scanned directories,
property-expanded executable paths and override ordering are not resolved yet.
Such hosts retain their captured Binder/library inputs but report an unresolved
display service. This discovery does not by itself enable another device.

Fixture tests cover Qualcomm-style HIDL and Samsung-style AIDL declarations,
line continuations and quoting, stopped services, duplicate/critical services,
action-section boundaries and invalid executable paths. Generated candidate
shell scripts pass syntax checks; real service discovery/restart on hardware
has not been tested. See the [Android init language reference](https://android.googlesource.com/platform/system/core/+/refs/heads/main/init/README.md).
