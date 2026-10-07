# Native service loss across host reboot

Observed at 2026-10-07T00:32:20.391577Z: Linux /proc/stat reports btime
1791332499, corresponding to 2026-10-07T00:21:39Z. The current boot ID is
b04f7fb6-ea65-4b47-8866-73e24a6887f6; /proc/uptime reports 640.45 seconds.
Neither /proc/4070721 nor /proc/2372912 exists. These are separate current
host observations, recorded in observation.json and exact boot evidence files.

The fresh hardware game PID 4070721 was launched before this boot, with world
atom identity 275227937 on DISPLAY :0 and port 7896. It cannot remain the same
live process across the host reboot. The previously paused process 2372912 is
also absent. No live world recovery or serialization is established here.

This explains why the prior process handles cannot now refer to their old
running worlds. It does not determine whether either process exited before
the reboot, the precise process exit time, what caused the host to reboot, or
whether a game or driver error preceded the reboot. No such cause is inferred
from the runtime log or from the last file modification time.

The complete stopped fresh-game stdout is preserved byte-for-byte as
runtime.stdout.gz: 13,799,424 decoded bytes, SHA256
1d7516e990cdedf30fb74e230adcf414209af7e0b98dced7ebdd4d5edfc73080.
The original file mtime is 2026-10-07T00:15:41.284265Z. Its ending includes
interleaved kinematics clamp messages and NUL bytes; these are preserved.
The stopped stderr is empty and is preserved as runtime.stderr.gz. Both gzip
files have deterministic mtime zero. observation.json records original paths,
sizes, mtimes, source and decoded hashes, gzip hashes, and stable source stats
during reading. Decompression was checked against the complete source bytes.

The follow-camera bundle remains unchanged and closed. Its pre-install guard
prevented any client or observer installation after process loss. The earlier
passive hardware captures remain evidence only for their own recorded
interval, and readable close-view fading remains unverified. No new service,
process stop/start, desktop input, camera mutation, or GL context occurred
while collecting this reboot bundle.

CLOSED-FILES.txt is the exact staging inventory; SHA256SUMS covers every file
except itself. Original runtime logs are left unmodified and untracked at
their earlier paths. A later game launch would be a fresh runtime and world,
unless separate recovery evidence establishes otherwise. Root owns that
operational decision and publication.
