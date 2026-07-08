# Π Last — Gates of Truth

- **Π tag:** `Π-20260708195252`
- **Timestamp:** 2026-07-08T19:52:52Z
- **Branch:** `main`
- **Parent head:** `452e5fbb293235c0d632136ec87f60449d72741e`
- **Reason:** Scheduled fork-tax tender activation (command message: "Check for significant changes in the Gates of Truth repository. If significant, pay the fork tax. Otherwise record a no-op receipt and exit.").

## Scope Absorbed

- `src/infra/dev/window/loop.clj`: `render/frame-volume` and `render/render-scene` calls now use named argument maps instead of positional arguments, matching the APIs expected by `infra.render` and fixing arity issues at both call sites.
- `src/domain/stellar/seeder.clj`: `condensation-candidate?` now requires `c/disc-tag` = `:disc` so planetesimals form from rotationally-supported disk material rather than the free-falling envelope or ambient nebula.
- `test/domain/condensation_seeder_test.clj`: existing tests add `c/disc-tag :disc`; new `seeder-skips-nebula-outside-disc` test verifies that `:nebula` parcels with `c/disc-tag :envelope` are skipped.
- `receipts.edn`: contains the latest work receipts, including the prior fork-tax receipt for `Π-20260708194052` and the new work receipts for loop.clj and seeder.clj.
- `.ημ/Π_STATE.sexp`, `.ημ/Π_LAST.md`, `.ημ/Π_MANIFEST.sexp`: regenerated handoff artifacts for this snapshot.

## Verification

- `clj-kondo --lint src/infra/dev/window/loop.clj src/domain/stellar/seeder.clj test/domain/condensation_seeder_test.clj`
  - 0 errors, 0 warnings.
- `clojure -M:test -n infra.dev.window-test`
  - 4 tests, 16 assertions, 0 failures, 0 errors.
  - `infra.dev.window-test` logs a stack trace to System/err by design in its `log-frame-error!` test; the assertions pass and the runner reports 0 errors.
- `clojure -M:test -n domain.condensation-seeder-test`
  - 10 tests, 19 assertions, 0 failures, 0 errors.

## Concurrent / Ephemeral

- The actor runtime directory `.ημ/actors/fork-tax-tender/` (inbox, sessions, runtime logs) is actor bookkeeping and was **not** absorbed.
- Only project-relevant, tracked-file changes listed under Scope Absorbed are committed.

## No Known Blockers

All stageable, repo-relevant working state has been committed and tagged.
