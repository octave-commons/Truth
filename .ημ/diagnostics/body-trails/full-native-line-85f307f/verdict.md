# Integrated native-line repair: full validation

Exact source: `85f307ffd8b017f7d4c3d498490227eeaf087d93` in truth-focus-input.
The selected source/test/config paths were clean before both commands and stayed
unchanged afterward. No production edits or native input were made by this lane.

| Check | Result | Wall time |
| --- | --- | --- |
| `clojure -M:test` | 928 tests, 15,913 assertions, zero failures/errors; exit 0 | 98.060 s |
| `bin/analyze --strict` | All six stages complete; no blocking findings; exit 0 | 145.300 s |

The strict gate includes test-native source through the new direct paths and
source alias. Correctness, structural, Splint, dead-code, duplication and
formatting gates all passed. Duplication reported 64 clones / 787 lines (1.54%),
within the unchanged 1.7% threshold. No static rule or threshold was relaxed.
The already-passing native regression (one test, nine assertions) was not
repeated, and the ordinary suite still executes without a display.

Both commands used explicit JAVA_OPTS `-Xms256m -Xmx4g`; inherited initial-heap
settings were not relied upon. The native owner's ordinary functional capture
overlapped these checks by prior coordination. These durations are execution
provenance, not isolated performance measurements. Complete logs plus start/end
metadata preserve commands, source, heap, timing, exits and final source state.

All test/gate subprocesses have exited. Native gameplay and trail readability
remain separate acceptance evidence; these green gates do not assert that the
player has committed a natural world or activated a Gate of Truth.
