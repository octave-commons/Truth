---
category: "tasks"
labels: "hygiene, regression, genesis"
parent: "formation-placement-v2"
type: "task"
write-id: "1791312223275-0.0uh0jrpec88gku2xqu7q"
points: "2"
title: "Forward requested genesis seed to nebula bootstrap"
priority: "P1"
status: "review"
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

Minimal green repair complete on red checkpoint 08161ab: default-world-options now carries :seed42, seeded-world forwards the requested value into the existing seed-nebula RNG, and bootstrap create-world docstring records omitted-seed behavior. Focused genesis seed, seed-contract and architecture suite passes 12 tests / 38 assertions, 0 failures, 0 errors. Touched-file clj-kondo has 0 errors/0 warnings; cljfmt check and git diff --check pass. Same isolated probe now reports same-components? false and same-positions? false for seeds41/43; stderr empty. Evidence: natural-formation-observation/seed-forwarding-green.log, seed-forwarding-format.log and seed-forwarding-after.edn under .ημ/diagnostics/playable-foundation/. No RNG replacement or physics change. Awaiting root green source checkpoint before full canonical review gate; no prior gate result applies to this new source.

Canonical gated review transition completed on exact source revision 81207e7c65212685456ebe0faff3cac1cec50613. Rheos actually executed clojure -M:test: 901 tests, 15642 assertions, 0 failures, 0 errors; then bin/analyze --strict: exit 0, no blocking findings. Move in_progress to review exited 0. Per-transition log: .ημ/diagnostics/playable-foundation/rheos-review-seed-81207e7.log. Independent read-only review by runtime_scout found no blocking issue: omitted seed42 remains compatible; explicit nil/nonnumeric values reach the existing seed-nebula (long seed) failure instead of being silently ignored, consistent with the initializer contract. No new validation/fallback recommended for this forwarding repair. Root owns the source checkpoint and hosted review; this local verification does not claim final done or two-seed live formation acceptance.
---