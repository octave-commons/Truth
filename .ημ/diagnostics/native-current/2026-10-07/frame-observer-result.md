# Fresh PM2 native frame observation

The fresh game rendered one full scene/HUD frame on the owning native thread.
GL identity is Intel / Mesa Intel(R) Arc(tm) Graphics (MTL) / OpenGL 4.6 Core
Profile, Mesa 25.2.8-0ubuntu0.24.04.4. The actual framebuffer is 2536 by 1490.
This is a new natural seed-42 world launched at source 9c5c889, not recovery of
an earlier world lost across the host reboot.

Two read-only health audits first recorded ticks 1791 and 1913 with the same
fresh PID 131581, process start ticks 125503, world atom 919497450 and window
137768861339584. Both workers were alive with nil service/UI errors. The JVM
reported an effective maximum heap of 2,147,483,648 bytes despite the earlier
global JVM flags recorded in root's launch audit.

The first installer failed before directory creation, symbol interning or Var
mutation: Java FileInputStream.available raised Invalid argument while slurp
read /proc/self/stat. A read-only audit confirmed the observer was absent and
the same world/window were healthy. BufferedReader reproduced the failure.
The original scripts and outcomes remain under frame-install-attempt1.

Root authorized the bounded diagnostic I/O repair. An actual read-only probe
verified RandomAccessFile with one 4096-byte maximum read, positive count below
that bound, and required trailing newline. It returned the exact current boot
ID and start ticks. All prior identity assertions remained; independent source
review passed before retry. No production source or game state changed.

The repaired installer exited 0. The observer made exactly one attempt and
one capture, then automatically restored the original scene facade Var.
Active duration was 849 ms. Its three bounded GL error samples were each [0].
The previous GL read buffer was restored and verified. An explicit restore and
health audit also exited 0, confirming the same world/window at tick 8254,
both workers alive, nil errors, and the same 2 GiB heap maximum. No camera,
configuration, selection, input, body, time, world, PM2 or lifecycle mutation
was performed by the observer or its audit.

The unmodified PNG shows HUD tick 7975, 2.13 Myr, Planets formed, 24 planets;
bright stellar points within faint purple nebula, with prominent cyan field
glyphs around several bodies. The scene remains dark in fit-all view and the
field glyphs dominate body silhouettes. Bottom-left status bars overlap bottom
control text. These are visual observations, not a new implementation scope.
The separately recorded later world read is tick 7978; it is not asserted to
be the image's exact projection input. A single still cannot establish motion
or readable trail fading, and does not prove manual approach, commitment,
sculpting, or Gate use. Readback and PNG writing have host cost; no native FPS
or isolated performance claim is made.

Original pixels, EDN records, failed and successful command outputs, guard
probes, exact scripts and review hashes are retained. Root owns the full bundle
closure, growing runtime logs, provenance receipts and publication. No further
native query is needed for this observation.
