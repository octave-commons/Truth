---
category: "tasks"
labels: "hygiene, regression, genesis"
parent: "formation-placement-v2"
type: "task"
write-id: "1791311841213-0.8pj19xmdhn4e5yf1hoz"
points: "2"
title: "Forward requested genesis seed to nebula bootstrap"
priority: "P1"
status: "in_progress"
uuid: "genesis-seed-forwarding"
created_at: "2026-10-06T18:34:49.297Z"
---

# Forward the requested genesis seed to the nebula bootstrap

## Outcome

`domain.genesis/create-world` honors an explicit `:seed`, so recorded distinct
seeds produce distinct reproducible initial worlds for formation verification.

## Scope

- Preserve the existing default seed 42 when callers omit `:seed`.
- Forward the requested seed through bootstrap options to the existing
  `seed-nebula` generator, using its existing deterministic RNG.
- Add focused public-entry-point regression tests and clarify the bootstrap
  docstring. This is a 2-point option-forwarding hygiene/regression repair.

## Non-goals

- No RNG replacement, physics tuning, new world model, public option validation
  expansion, simulation reset, live-service mutation, or Gate implementation.
- No claim that distinct initial seeds already satisfy two-seed live survival.

## Evidence

`src/domain/genesis/bootstrap.clj` seeded-world drops :seed while seed-nebula
defaults it to 42. Fresh read-only executable evidence at
`.ημ/diagnostics/playable-foundation/natural-formation-observation/seed-forwarding-before.edn`
shows requested seeds 41 and 43 yield identical initial physical component maps
with gas-count 4. The neighboring seed-forwarding-probe.clj preserves the probe.
This blocks the existing formation-placement-v2 two-seed acceptance from being
meaningful; it does not identify a separate physical formation failure.

## Acceptance criteria

- Repeating the same explicit seed reproduces the same initial physical state.
- Requested seeds 41 and 43 produce different positions and velocities.
- Omitting :seed matches explicit seed 42, preserving current default behavior.
- Gas entity count, total mass, composition, and initial matter states remain
  invariant when only :seed changes.
- Existing seed-contract and architecture tests remain green.

## Verification

Write and run the failing focused tests first, preserve the red output, and
wait for root's test checkpoint before implementation. Then run the focused
tests and existing seed-contract/architecture checks, style checks, and rerun
the same evidence probe. Full test/strict canonical review gates run on the
final committed source; previous exact-head review outcomes are not reused.

---
Red checkpoint ready on source 56f2a19d02085b3f2e86c9926b837bc990859ef8: new public-entry-point test/domain/genesis_seed_test.clj runs 2 tests / 7 assertions, 2 expected failures, 0 errors. Both failures are precisely seed41 versus seed43 positions and velocities being identical. Repeatability, omitted seed equals explicit42, four gas parcels, total mass4e30, unchanged mass/state/composition controls pass. Test-file clj-kondo is 0 errors/0 warnings and diffcheck passes. Log: .ημ/diagnostics/playable-foundation/natural-formation-observation/seed-forwarding-red.log. A first unmatched-parenthesis test-read attempt was corrected and retained separately as seed-forwarding-test-read-check.log; it is not regression evidence. No implementation changes yet; awaiting root red commit before forwarding seed through existing options/RNG, with default42 explicitly preserved to avoid passing nil to seed-nebula.
---