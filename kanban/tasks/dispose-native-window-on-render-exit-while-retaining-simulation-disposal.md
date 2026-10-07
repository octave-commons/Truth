---
category: "tasks"
labels: "hygiene, render, lifecycle"
parent: "f369c598-279c-498d-a64f-45d2ce16ad34"
type: "task"
write-id: "1791347005327-0.c9j3nk4av1p9e10u8tu"
points: "5"
title: "Dispose native window on render exit while retaining simulation"
priority: "P1"
status: "incoming"
uuid: "native-window-close-disposal"
created_at: "2026-10-07T03:26:09.798Z"
---

## Context

The existing native verification owner recorded real Escape ending rendering
while an X11 window remained mapped. Continued simulation/nREPL is intentional;
the leaked visible window is the defect. The original measurement card remains
unchanged. This is a separate proposed hygiene repair, not implementation
admission or a fresh reproduction.

> Grounding and proposed contract: [Native window closure](../../docs/notes/2026-10-06-native-window-close-lifecycle.md#proposed-disposal-contract--2026-10-07), pinned to source `b395c4049718f7ce015ddf25fc0a192d97821373` and its existing native evidence.

The [merged launch contract](https://github.com/octave-commons/Truth/pull/7)
says Escape closes the window. The [development service policy](../../AGENTS.md#running-the-dev-service)
retains the running simulation. The note cites the relevant source and GLFW
primary contracts; hygiene does not invent a new gameplay or force law.

## Outcome

Normal and exceptional render exits retire only their owned native window and
stale host resource references. Ordinary Escape leaves the same simulation/world
and nREPL service running. Full service stop cannot lose ownership of a live
worker or race destruction through a stale window pointer.

## Scope

- One owner finalizer for normal exit, setup/render failure and cleanup failure;
  include an allocated handle whose constructor fails before returning.
- Event polling/close handling also reaches the error-overlay branch.
- Free per-window callbacks, retire known owned GPU resources in their context,
  detach/clear GL capabilities, destroy the unshared window, and clear host
  handles. Discard unproven foreign-context cache IDs without issuing deletion
  against another context. Cover direct meshes and volume refs as well as assets.
- Match exact service/stop identity when publishing/clearing window state and
  errors. Signal full stop through the captured stop atom; keep bounded joins,
  retain ownership/report failure while any captured worker lives, and forbid
  overlapping restart.
- Clarify ambiguous Escape wording only where this existing close-window
  contract requires it.

## Non-goals

No JVM/nREPL/simulation stop on Escape, world replacement, automatic/window-only
reopen, input-neutralization policy, camera/control change, gameplay shortcut,
new renderer/cache framework, offscreen screenshot redesign, reload-helper
repair or cross-platform main-thread guarantee. The existing named Linux render
owner remains the tested host architecture. Do not claim all component values
or held-input effects stop changing when the window closes.

## Acceptance criteria

1. Real Escape and OS close retire the owned window after callbacks return,
   including error-overlay mode; normal closure preserves the simulation stop
   flag, world atom and nREPL. Primary render errors remain visible.
   Error-overlay closure preserves its error and live service; the existing
   simulation pause on that error remains, so this case does not promise ticks
   advance or silently clear the error.
2. Partial allocation/setup failure and failure inside cleanup still attempt
   remaining retirement stages, retain primary/secondary error evidence and
   remove only the matching published handle. Never call GLFW on a freed handle.
3. No stale program/mesh/texture/volume handle is reused by a later service;
   GL deletion occurs only for known owned IDs with that live context current.
   Do not call global GLFW termination/error-callback cleanup on window close.
4. Stop timeout/interruption retains the exact service while workers remain;
   only completed stop permits a new service. A delayed old finalizer cannot
   clear a different service, including reused numeric window handles.
5. A disposable ordinary-nebula native process proves window disappearance,
   ended render worker, same advancing world/sim worker and available nREPL;
   completed full stop then restart with that world and the recorded ordinary
   nebula launch options (same tick/body-projection route, fresh GL resources)
   proves real fresh-resource drawing. The retained primary game is untouched.

## Verification

Before production edits, commit real failing regressions through the actual
owner loop and lifecycle adapter: normal/error close, partial setup, cleanup
failure and stale-owner/stop-deadline cases. Pure spies/latches establish ordering
and identity but cannot replace native proof. Use a separate JVM/display/free
port for actual window input and record source/process/window/world identities,
external X11 disappearance, advancing ticks, GL status and complete cleanup.
Never query a destroyed GLFW pointer. Label injected native faults separately
from the ordinary-nebula Escape run. Run directly relevant ordinary/native
tests, full ordinary suite and all six strict gates after implementation.

## Risks and estimate

Proposed **5 points**, superseding the note's earlier tentative 3: partial
construction, normal/error exit, resource ownership and stop races form one
coherent disposal invariant and must not be split into unsafe partial repairs.
The offscreen path already switches contexts and global caches; its functional
limitations remain. Confirm bounded cache invalidation and the final lifetime
test seams during planning review. If this requires a context-cache architecture
or screenshot rewrite, return to breakdown instead of expanding this card.
Incoming only: review convergence, final size and legal InProgress admission
remain required. No implementation, executed RED/native result or Done claim.

---

Planning only on exact b395c4049718f7ce015ddf25fc0a192d97821373. This new Incoming hygiene child is proposed at 5 points, superseding the historical tentative 3 because partial construction, normal/error exit, resource cleanup and stop ownership form one lifetime invariant. Parent f369c598-279c-498d-a64f-45d2ce16ad34 remains byte-identical and InProgress3 with its measurement scope. Grounded note docs/notes/2026-10-06-native-window-close-lifecycle.md records existing actual Escape evidence, source/API boundaries and later RED/native matrix. Independent source/planning review found no blocker at note60ac1d8904e7addadd5eef388e3a12b278a7ee2eec0b119a8932bd31da363bc9 and pre-comment card89b88ea64fd6ae4f97e5e19c4161be74a0519b1f97e938a6797139c0d9c15bee. Preserve normal running simulation, error-overlay pause/error evidence, exact captured service identity, and original ordinary-nebula launch options on explicit full-stop/restart. Discard unproven foreign-context cached IDs without deleting them in another context; no offscreen architecture or input-neutralization policy expansion. Required native proof uses a disposable secondary JVM/display/free port and never the retained primary game. Cache teardown strategy, test seams, final size and configured review convergence remain prerequisites; this is no implementation admission, executed RED/native result, Ready transition or Done.

Append-only amendment record for PR37 review5437458020 / comment4202906657, inspected head9a73733d93338d8a0e1abb4034632f992e20e8ec. After the original task-created event, the Incoming card was manually refined in AC1 and AC5 before its first planning commit. This records that already-present Markdown refinement explicitly; it does not rewrite the creation event. AC1 added: "Error-overlay closure preserves its error and live service; the existing simulation pause on that error remains, so this case does not promise ticks advance or silently clear the error." AC5 replaced the generic retained-world restart wording with: "completed full stop then restart with that world and the recorded ordinary nebula launch options (same tick/body-projection route, fresh GL resources) proves real fresh-resource drawing. The retained primary game is untouched." These clarify preservation of existing error state and the exact ordinary-nebula launch route required for the later native proof. Current body, scope, parent and Incoming5 status remain unchanged. No source edit, test/native execution, new implementation admission or completed review is asserted.

2026-10-07 appended source-specific cache/test-seam proposal to docs/notes/2026-10-06-native-window-close-lifecycle.md, noteSHAe64cfaee1f45a01947a2f69e05761821892dc61c397f9eac3a652aefb97adc51. Both constructors use unshared contexts. Proposed close finalizer issues zero glDelete calls: discard host cache/config references under proven renderer-entry ownership, then retire the owned context/window so its objects cannot be reused; no immediate physical-memory-release promise. Global caches require an explicit reentrant visible/offscreen entry guard, foreign-thread rejection before allocation/mutation, and serialized service-generation claims. Those compatibility/size decisions and exact failure-injection seams remain review prerequisites, not current guarantees. Arbitrary low-level reload/cache calls, screenshot restoration, offscreen construction leaks and repeated sphere allocation are excluded, not silently repaired. Runtime scout independently verified all10 source hashes and primaryAPIbasis; its PDF viewer-page citation nit is corrected from75 to76, with no substantive delta. Existing note12258-byte prefix, card body and Incoming5 preserved. No source/test/native calls, executed RED, implementation admission or completed disposal claim.

---