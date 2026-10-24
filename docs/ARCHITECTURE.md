# Architecture

The app obtains owner-approved root, extracts a small native launcher and starts a detached `app_process`. The launcher retains a single-session file lock. It waits until the session log is open before reporting success.

`SessionMain` checks the device/firmware, selected GSI digest, root-owned profile, available storage and every profile file hash. Runtime 198 or newer is required; mixed entries/jars or missing installer-fix helpers are rejected. It then launches the prepared runtime entry. The app displays shell command tracing and follows the actual session log with bounded memory; leaving the activity stops the log follower, not the detached guest.

The dm1q runtime maps its own ImageService-backed system/data volumes, verifies the executable system image, builds private mount/Binder/network/property environments and coordinates hardware ownership through a host-side guardian. The registered lazy GSI-service binder stays strongly referenced while its image-service child is in use. Existing prepared images are verified directly; an unused source copy is not read on every entry.

Before Zygote starts, the loader makes the guest user-storage parent a shared propagation root and binds installer/Android-writable views to it. Later FUSE mounts then reach app namespaces. Raw guest media files are not exposed as a substitute for FUSE. The guest ART daemon remains registered for the session, while reversible UID leases prevent inherited host standby rules from blocking guest downloads.

The guardian maintains the stock watchdog, enforces temperature limits and restores host services, network ownership, display, USB and SELinux after a session. The audio service group must remain stopped for a quiet interval because stock init can restart HALs during shutdown. Restoration follows the original service states and dependencies. Those checks must not be bypassed to accelerate boot.

The current path does not invoke `ksud soft-reboot`, install a global magic-mount module or replace OneUI's global `/system`. Root is required and KernelSU is the tested provider. Temporary session lifetime does not imply that all temporary-root methods are supported or that no persistent guest files are written.

The source snapshot preserves historical class and file names used in that runtime contract. Numeric suffixes are implementation revisions, not claims that every earlier experiment is needed.
