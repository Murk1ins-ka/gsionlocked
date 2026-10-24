# Architecture

The app obtains owner-approved root, extracts a small native launcher and starts a detached `app_process`. The launcher retains a single-session file lock. It waits until the session log is open before reporting success.

`SessionMain` checks the device/firmware, selected GSI digest, root-owned profile, available storage and every profile file hash. It then launches the prepared runtime entry. The app displays shell command tracing and follows the actual session log with bounded memory; leaving the activity stops the log follower, not the detached guest.

The dm1q runtime maps its own ImageService-backed system/data volumes, verifies the executable system image, builds private mount/Binder/network/property environments and coordinates hardware ownership through a host-side guardian. The registered lazy GSI-service binder stays strongly referenced while its image-service child is in use. Existing prepared images are verified directly; an unused source copy is not read on every entry.

The guardian maintains the stock watchdog, enforces temperature limits and restores host services, network ownership, display, USB and SELinux after a session. The audio dependency order is host audioserver, primary audio HAL, then Samsung audio HAL when stopping, with reverse dependency restoration. Those checks must not be bypassed to accelerate boot.

The source snapshot preserves historical class and file names used in that runtime contract. Numeric suffixes are implementation revisions, not claims that every earlier experiment is needed.
