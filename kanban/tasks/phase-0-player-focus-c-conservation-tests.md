---
category: "specs"
labels: ["specs", "phase0", "player", "test", "epic-player-focus-promotion-demotion"]
write-id: "1784751639182-0.xr2fxm0r74fi3tnavwl"
source: "kanban/tasks/phase-0-player-focus-c-conservation-tests.md"
title: "Player Focus C: conservation tests + invariant validator"
priority: "P1"
status: "done"
estimate: "2"
uuid: "phase-0-player-focus-c-conservation-tests"
created_at: "2026-07-22T00:00:00Z"
---

# Player Focus C: conservation tests + invariant validator

> Parent epic: `kanban/tasks/phase-0-player-focus-promotion-demotion.md`
> Depends on: Player Focus B (the `:focus-zone` system) for the 6 world-level
> tests. The pure `promotion-invariant-validator` test has NO ECS dependency and
> can be written any time.

**Goal:** Prove the promotion/demotion round-trip conserves mass, momentum,
angular momentum, flux, and energy, and that the ledger and threshold-delay
rules hold.

## Scope

Write the 7 named tests (fixtures follow `test/domain/field_test.clj` /
`test/domain/stellar_test.clj` — build a frozen world with `ecs/spawn` +
`put-components`, call `(:run sys) world` directly, assert on the write-set):

- `promotion-conserves-mass` — promote a 1e27 kg cell; spawn spec mass = cell
  mass (within `promotion-invariant?` tol); cell debited.
- `promotion-conserves-momentum` — nonzero cell velocity; `m·v` preserved.
- `promotion-conserves-angular-momentum` — nonzero cell L; L preserved.
- `demotion-conserves-mass` — resolved body outside immediate-r with
  `c/promoted-from-cell`; cell credited by body mass; body marked
  `c/consumed-demote`.
- `demotion-preserves-ledger` — despawning a body leaves prior `:ledger` events
  intact.
- `demotion-threshold-events-delay` — a body in a this-tick threshold event
  (`:event/collision`, `:event/stellar-ignition`, …) is NOT demoted.
- `promotion-invariant-validator` — pure `law.field/promotion-invariant?`
  returns true for a valid before/after and false for a perturbed one (new
  `law.field-test` ns or added to `field_test.clj`).

## Done when

- All 7 tests pass; `clojure -M:test` green; `architecture-test` green.
- Conservation asserted on a single materialized world/write-set (no settle
  window needed — debit+spawn and credit+despawn are emitted in one write-set
  and resolved in one `tick-physics` call; see parent plan §3).
- Parent epic closed when A + B + C are all done.

---
Created 2026-07-22 (Claude): child C. The pure invariant test is unblocked now;
the 6 world-level tests are blocked on child B.

Triage 2026-07-22 (resumed session): child B done + committed (0dedd69). Dispatching impl agent for the 7 named conservation tests. blocked -> in_progress.

Complete + independently verified 2026-07-22 (resumed session). focus-conservation-test 7 named tests green; full suite 676/13561 (was 669/13536) 0 failures; architecture green; write-conflicts {}. All 7 card tests in test/domain/focus_conservation_test.clj: promotion mass/momentum/L conserved via promotion-invariant? tol; demotion mass credit + consumed-demote; ledger events survive despawn; same-tick threshold event blocks demotion; validator true/false cases. FOUND + FIXED latent bug in law.field/promotion-invariant? (schema.clj:254-255): every? over mapped pairs called 2-arity pred with 1 arg -> ArityException on any real input; shipped latent in child A (validator had zero callers until now). Fixed to destructured fn. Epic closes: A+B+C all done.
---