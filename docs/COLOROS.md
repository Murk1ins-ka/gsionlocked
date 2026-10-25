# ColorOS 17 preview for the prepared Galaxy S23

The app retains the gsionlocked UI and EvoX launcher. ColorOS has a separate
backend: selecting it never invokes the EvoX loader or resizes EvoX storage.
The input is the exact Find X9 Ultra **IN** image in
[profile metadata](../profiles/dm1q-coloros17.json), not an interchangeable CN image.
Its source project is [OneOS](https://github.com/fahadalijaved23/OneOS).
No GSI download URL for this exact IN file is asserted here; use the matching
user-provided raw image and verify its digest. The image is not a release asset.

## Prepared phone

1. Install the APK in One UI. Temporary KernelSU root must already work.
2. Open **Select GSI → Use prepared ColorOS**. The app checks the host, runtime
   manifest, every runtime file, script syntax and the complete image SHA-256.
   This can take time for the 12 GB image. Grant root in KernelSU when requested.
3. Unlock One UI and disable its secure lock/biometrics before **Start**.
   The app and root wrapper both check the host; credential guards remain active.
4. Save open files and confirm Start. On-screen logs show the handoff.
5. In ColorOS, **Return to One UI** writes a private request for the host-side
   watcher. It needs no guest root. Alternatively hold both volume keys for
   three seconds. Return reboots; restore temporary root before the next launch.

The launch confirmation also offers **Save root — experimental mode**, disabled
by default. It requests restoration on the same kernel boot, after APEX, host
service, SELinux and framework stability checks. The S23 coordinator stops and
starts netd separately, then starts primary zygote once, avoiding netd's restart
trigger race. Any failed restoration check still reboots. Retaining root is a
runtime test requirement, not a guarantee: both an unchanged boot ID and a fresh
root request from the host app must pass after return. An automatic return after
a failed guest boot passed these checks on the development S23, including a new
root request from this app. A later healthy-session return restored One UI on
the same boot with SELinux Enforcing, but the app's root client then failed with
SIGSYS/seccomp even though an existing ADB root path still answered. App root
retention therefore remains unresolved; temporary root/access may need restoring.

Selecting prepared ColorOS shows a dedicated screen-lock warning. Launch checks
KeyguardManager and root-side trust state again; it does not remove a password
automatically. Startup logs reuse the repository's native BootLogOverlay with
gsionlocked branding and bundled project wallpaper. A separate host layer keeps
the live progress visible while One UI's framework is stopped, and releases its
SurfaceFlinger at the guest display handoff. The guest layer exits when OPPO boot
animation starts, before desktop boot completion. The earlier OneOS WebView log screen is disabled.
The AOD policy remains disabled without an AOD-off label in the app interface.

The prepared runtime copies the launcher's APK into guest storage and updates
the guest launcher after boot so its return button matches the host version.
This new automatic update path still needs a live-session check. The hardware
return chord remains available independently of the guest app.

Return uses a styled confirmation sheet followed by a logo, an indeterminate
spinner and a request-sent message. These describe the request, not a percentage
or a claim that restoration has already completed. The app screen disappears
when the guest framework stops; a temporary black screen remains possible.

## Runtime contract

Stage `/data/local/tmp/oneos` must already contain the matching image, private
guest data, extracted APEX/host components, adapters and supervisory scripts.
The APK pins the exact host `services.jar` and the runtime manifest digest.
Hashes are published in `runtime/coloros-s23/fix.sha256`; the manifest is metadata,
not an installable runtime. A mismatch stops launch before stopping One UI.
Do not replace the manifest or remove checks to make a different runtime pass.
Updating the runtime requires regenerating the profile, manifest pin and app.

The launcher detaches from its app cgroup, enters PID 1's mount namespace and
holds a launch lock. Its logs go to `takeover-s23-launcher.log` in the stage.
Guest return requests use `dev.tempgsi.controller/files/oneos_return` under the
guest's own data volume; they are not host credential operations.

AOD, doze triggers, the earlier AOD controller and tile are disabled on startup.
Refresh remains adaptive with a 120 Hz maximum; the screen can sleep on USB.
Existing bounded diagnostics and less frequent storage maintenance are retained.
Samsung thermal controls are not disabled. No blanket frequency caps, forged
temperature readings or claims of stock-equivalent performance are applied.

## Limits

This is an experimental port, with no clean-phone bootstrap in the APK.
PIN/password, fingerprint and face unlock are unsupported in this guest.
SELinux Enforcing compatibility, operator SMS, all-lens capture, prolonged
thermal/battery behavior and every application upload flow are not established.
See the [separate ColorOS compatibility table](COMPATIBILITY.md#coloros-17--find-x9-ultra-in).
The EvoX credential/hardware results must not be used as ColorOS claims.
