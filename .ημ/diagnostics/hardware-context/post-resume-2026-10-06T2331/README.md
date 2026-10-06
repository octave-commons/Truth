# Separate post-resume service audit

Root reported pausing the owned hardware JVM at 23:28:13 UTC and resuming it at
23:31:16 UTC for a separate paired cost experiment. This later operation does
not alter the closed fresh-game capture bundle or its observations.

One authorized read-only nREPL audit ran at 23:32:15–23:32:18 UTC using the
existing `clojure -J-Xms64m -J-Xmx256m -M:demo-client` route to port 7896.
Local executable, cwd and process-start ticks were checked first. The audit
asserted the expected PID 4070721, world atom 275227937, window 129843170276688,
live render/simulation workers, advancement beyond earlier tick 4371, and exact
original renderer restoration with an inactive observer. It exited 0; stderr
was empty. No input, capture, GL call, world/config mutation or extra context
was requested.

The same natural world had reached tick **10220**, with 24 planets, 2 stars,
2 brown dwarfs, 3 protostars and 780 nebula parcels. Service and UI errors were
nil; the observer remained complete after 3 attempts, with no errors. These are
state observations at the recorded time, not a guarantee of later liveness,
a performance result or manual commitment/Gate acceptance.

Client outputs are archived byte-for-byte as deterministic gzip; their original
raw files remain untracked. `CLOSED-FILES.txt` is the explicit staging inventory;
`sha256sum -c SHA256SUMS` verifies every listed file except the manifest itself.
