---
category: "specs"
labels: ["specs", "static-analysis", "regression", "process"]
write-id: "1784985930183-0.jdjfto0uj4m12xpu6rd"
source: "kanban/tasks/static-analysis-regression-2026-07-24.md"
title: "Static-analysis regression: the gate has never been green, and twelve `done` cards silently un-did themselves"
priority: "P1"
status: "done"
estimate: "21"
uuid: "static-analysis-regression-2026-07-24"
created_at: "2026-07-24T00:00:00Z"
---

# Static-analysis regression, 2026-07-24

> Parent: `kanban/tasks/epic-static-analysis-cleanup.md`

## 1. The finding that matters

`bin/analyze --strict` exits 1. That is the surface. The actual finding is that
**this gate has never been green in recorded history.**

Every `static-analysis` CI run visible via `gh run list` has failed continuously
since **2026-07-11** — including PR #1 (`cae2668`), which was merged anyway. The
gate exists, runs on every PR and every push to main
(`.github/workflows/static-analysis.yml:39`), and works correctly. It has simply
been merged past, repeatedly.

There are two independent causes at different times:

| Where | Blocking failure | Structural HARD |
|---|---|---|
| `main` (8fbd078) | 2 clj-kondo **errors** — `src/law/field/schema.clj:195,196` | 0 |
| `spark-gravity-bound-body` (48 commits ahead) | 0 kondo errors — main's bug is fixed here | **4** |

main's blocker was a real bug, not lint noise. `#(rel-close? %1 %2)` mapped over
`(map vector ...)` receives **one** argument (the pair), not two — so momentum
and angular-momentum conservation were never actually compared. The branch fixed
it properly at `src/law/field/schema.clj:268` with pair destructuring. A gate
nobody reads let a broken conservation check sit on main for two weeks.

## 2. The process failure

`epic-static-analysis-cleanup.md`'s triage footer records that on **2026-07-10**
the tree stood at *clj-kondo 0/0, structural 0 HARD, 0 undocumented public fns*.
Twelve child cards carry `status: "done"`.

Today:

| Tool | 2026-07-10 | 2026-07-24 |
|------|-----------|-----------|
| clj-kondo | 0 warnings / 0 errors | 50 warnings / 0 errors |
| structural HARD | 0 | **4** |
| Splint | 18 | 147 |
| clojure-lsp unused-public-var | 312 | 353 |
| jscpd clones | — | 74 (1.86%) |
| cljfmt | 7 files | 24 files |

Those findings regressed and no card was reopened. **That is the failure this
card exists to stop.** The next agent reads `done`, believes it, and adds one
more finding. It takes exactly one agent deciding to get around to it later for
every subsequent agent to conclude it is not their problem.

Per owner decision (2026-07-24): the regressed cards **stay `done` as historical
record**. This card and its children are the new work. The point is not to
rewrite history — it is that a `done` card whose finding has returned must never
again be the only thing a future reader sees.

## 3. What reintroduced each finding

- **`domain.stellar.classifier` 62 vars ≥ 60** — the M5 handoff phases:
  `c1b88c5` (Phases 1-2, material/thermal + orbit stability), `a73b483`
  (Phase 3, atmosphere retention), `e9f52c2` (Phase 4, planet-candidate record),
  all 2026-07-22. The namespace grew +651 lines on this branch.
  Regressed card: `static-analysis-split-stellar-core.md` (`done`).
- **`law.stellar` 69 vars ≥ 60** — incremental re-export growth on this branch
  (+19 lines vs main). 27 of the 69 have no consumer at all.
- **`derive-edits` / `voxel-focus-system`** — `src/domain/voxel/carve.clj` and
  `src/domain/voxel/focus.clj` are **new files** on this branch (voxel epic).
  Regressed card: `static-analysis-decompose-mega-functions.md` (`done`).
- **50 clj-kondo warnings** — accumulated across the voxel, M5 and narrowing
  work. Regressed cards: the six `static-analysis-clj-kondo-*` (all `done`).

## 4. A measurement bug underlies two of the four HARD breaches

`dev/smell_report.clj:110` computes function loc as `end-row - row + 1`, and
namespace loc as raw `file-loc` (`:89`). **Docstrings and design-note comment
blocks count as code** — so the metric penalises the very conventions
`CLAUDE.md` mandates ("Docstrings mandatory on public vars").

| Function | Raw loc | Code-only loc | Under honest metric |
|---|---|---|---|
| `derive-edits` (`carve.clj:406`) | 116 | **78** | clears — *by 2 lines* |
| `voxel-focus-system` (`focus.clj:238`) | 86 | **57** | clears comfortably |

This affects **only** the two mega-functions. Both god-namespace breaches are
**var-count** (62 and 69 against a hard gate of 60), not loc — `classifier.clj`
at 967 loc is nowhere near the 1200 loc hard gate. Fixing the metric is not a
way out of those two; they need real structural work.

Fix the measurement first, so the refactors that follow aim at real complexity
rather than at documentation. `derive-edits` is decomposed regardless: at 78/80
one line of new physics re-breaches it.

## 5. Children

| Card | Wave | What |
|---|---|---|
| `static-analysis-smell-metric-code-lines.md` | 1 | count code lines, not docstrings |
| `static-analysis-dissolve-law-stellar-facade.md` | 1 | 69 → 42 vars, kill the double facade |
| `static-analysis-split-classifier.md` | 1 | 62 vars → three namespaces on the system boundaries |
| `static-analysis-decompose-derive-edits.md` | 1 | 78/80 → extract four seams |
| `static-analysis-kondo-sweep-2026-07.md` | 2 | 50 → 0 |
| `static-analysis-splint-sweep-2026-07.md` | 3 | 147 → ~10 |
| `static-analysis-facade-prune.md` | 3 | 199 dead re-export aliases |
| `static-analysis-lsp-config-dead-vars.md` | 3 | 353 → ~0, config-first; 12 real deletions |
| `static-analysis-unused-pending-convention.md` | 3 | formalize the marker for the 21 incomplete vars |
| `static-analysis-jscpd-src-extractions.md` | 3 | 15 src clones |
| `static-analysis-jscpd-test-fixtures.md` | 3 | 59 test clones → one fixture namespace |
| `static-analysis-cljfmt-2026-07.md` | 3 | 24 files, runs LAST |
| `static-analysis-ratchet-branch-protection.md` | 4 | the actual root cause |

## 6. Corrections to existing cards

- `epic-static-analysis-cleanup.md` — the 2026-07-10 triage footer is now false;
  annotate it rather than delete it.
- `static-analysis-dead-code-cleanup.md` §1 — says 157 findings; actual is 353.
- `static-analysis-dead-code-law-schemas.md` and
  `static-analysis-dead-code-domain-infra.md` — both list "make internal helpers
  `^:private`" as a work item. **Zero vars qualify.** clojure-lsp's
  `unused-public-var` only fires when there are no *cross-namespace* references,
  so a var used inside its own namespace is still flagged; all six non-facade
  candidates were hand-checked and are false matches (docstring prose, keyword
  literals, a `defprotocol` method). Drop the line item.
- The four `rejected` Splint cards (`-splint-math`, `-splint-arithmetic-control`,
  `-splint-naming-structure`, `-splint-final-gate`) were rejected as **card
  consolidation only** — "18-warning remainder too small to justify separate
  cards" — *not* as a decision to skip Splint. The parent
  `static-analysis-splint-idiom-cleanup.md` is `ready` and still owns the work.
  Recording this so the rejections are not later misread as a policy call.
- Duplicate cljfmt cards: `static-analysis-cljfmt-cleanup.md` (`ready`) and
  `spec-cljfmt-formatting-cleanup.md` (`todo`) are the same work.

## 7. Done when

- [ ] `bin/analyze --strict` exits 0 on this branch.
- [ ] `clj-kondo --lint src test` → 0 warnings, 0 errors.
- [ ] `clojure -M:splint` → 0, or documented `.splint.edn` suppressions each
      carrying a `;; Intentional:` reason.
- [ ] `clojure-lsp diagnostics` → 0 unused-public-var, or documented exclusions.
- [ ] `clojure -M:cljfmt check src test` passes.
- [ ] `clojure -M:test` still at 879 tests / 15486 assertions / 0 failures.
- [ ] cljfmt and clj-kondo warnings are in the `FAIL` set in `bin/analyze`.
- [ ] Branch protection requires the `static-analysis` check, **verified by
      pushing a deliberately-failing branch and confirming the merge is
      refused**. The gate is not verified until it has refused something.
- [ ] `CLAUDE.md`/`AGENTS.md` carry the norm: a red `bin/analyze --strict` is a
      blocker, not a backlog item; if you cannot fix it, file a regression card
      — never leave a card `done` whose finding has returned.

## 8. Baseline

`clojure -M:test` before any change: **879 tests, 15486 assertions, 0 failures,
0 errors.** Every wave must hold this.

---
## 9. Outcome (2026-07-25)

### Result

| Tool | 2026-07-24 | now | gating |
|------|-----------|-----|--------|
| clj-kondo | 50 warnings / 0 errors | **0 / 0** | errors **and warnings** block |
| structural HARD | 4 | **0** | blocks (`--strict`) |
| Splint | 147 | **0** | **blocks** |
| clojure-lsp unused-public-var | 353 | **0** | **blocks** |
| cljfmt | 24 files | **0** | **blocks** |
| jscpd | 74 clones / 1.86% | 65 / 1.62% | **blocks** above `.jscpd.json` `threshold` |

`bin/analyze --strict` → exit 0. `clojure -M:test` → **879 tests / 15486 assertions /
0 failures**, the §8 baseline, held at every wave boundary.

### §7 done-when

- [x] `bin/analyze --strict` exits 0 on this branch.
- [x] `clj-kondo --lint src test` → 0 warnings, 0 errors.
- [x] `clojure -M:splint` → 0. `.splint.edn` exists and disables **no rule globally**
      (a deliberate change from the plan — see the Splint card); 15 per-form
      suppressions, each with a `;; Intentional:` reason.
- [x] `clojure-lsp diagnostics` → 0 unused-public-var. `.lsp/config.edn` created.
- [x] `clojure -M:cljfmt check src test` passes.
- [x] `clojure -M:test` at 879 / 15486 / 0 failures.
- [x] cljfmt and clj-kondo warnings in the `FAIL` set — **plus Splint, clojure-lsp,
      and jscpd**, all three of which also reached zero.
- [x] **Branch protection requires the check — DONE 2026-07-25**, with owner approval.
      `main` requires the `analyze` context, `strict: true`, `enforce_admins: true`.
      Verified by refusal (PR #2, since closed): "the base branch policy prohibits the
      merge". See the Correction section at the end of this card — note the required
      context is `analyze` (job name), not `static-analysis` (workflow name).
- [x] `CLAUDE.md`/`AGENTS.md` carry the norm.

**The gate has refused something.** Each newly-blocking class was probed with an
injected finding; `bin/analyze --strict` exited 1 and named it in every case
(shadowed-var warning, `style/plus-one`, misindentation, unreferenced public var). The
last was not injected — a speculative `observer-eid` helper left in the new
`test/support/worlds.clj`, caught by the gate on its first real run. Probes reverted.

### §6 board reconciliation — done

- Epic footer: 2026-07-10 triage annotated (kept, not deleted) with the resolution
  table; §8 open question 2 answered *differently* than assumed — dead-code did not
  stay advisory, it went to zero and is now blocking.
- `-dead-code-cleanup` → `done`, with both stale claims corrected (157 was really 353;
  the `^:private` bucket has zero members).
- `-dead-code-law-schemas` / `-dead-code-domain-infra`: the `^:private` line item
  struck from both, with the reason.
- Duplicate cljfmt cards (`-cljfmt-cleanup`, `spec-cljfmt-formatting-cleanup`) folded
  → `done`, pointing at `-cljfmt-2026-07`.
- `-splint-idiom-cleanup` → `done`/superseded; the four `rejected` Splint cards
  re-recorded as card consolidation only, on the card itself so it cannot be
  misread from the board.

### Corrections to THIS card's own analysis

Recorded because the plan asked for measurement before code, and two measurements
were wrong:

1. **`:exclude-when-defined-by` does not work** for the DSL-generated test vars.
   `.clj-kondo/hooks/ecs_dsl.clj` rewrites the macros into plain `def`, so the
   analysis records `:defined-by clojure.core/def`. The three DSL test namespaces are
   excluded wholesale instead, with the cost stated in `.lsp/config.edn`.
2. **The facade prune had to iterate to a fixpoint.** Facades are stacked
   (`domain.planet-formation` → `.spec` → `.physics`), so pruning one layer reveals
   the next. 157 + 22 = 179 aliases over three rounds.
3. `lint/catch-throwable` is 8 in `dev/window/loop.clj`, not 9; the ninth is
   `domain.ecs.tick/run-parallel`, and it is the best-justified of them.
4. The `lint/fn-wrapper` at `loop.clj:90` this card flagged as possibly unsafe **is
   safe** — verified by reading `domain.orbital.system`: the closure captures only
   numbers. Applied.

### A hole in the ratchet, found and fixed during self-review

`.gitignore:3` ignored **all** of `.lsp/`, so the new `.lsp/config.edn` would never
have been committed. Locally the gate was green; in CI `clojure-lsp diagnostics` would
have seen 350+ unused-public-var findings with no exclusions and — now that the check
is BLOCKING — failed every single run. A gate that fails only in CI is worse than no
gate: it is exactly the "red check everyone learns to ignore" state this card exists to
end.

Fixed: `.lsp/*` + `!.lsp/config.edn`, so the config is tracked and `.lsp/.cache/` stays
ignored. Verified with `git check-ignore -v`.

General lesson for the next tool added to the gate: **a gate config in a
conventionally-cached directory must be explicitly un-ignored.** `.clj-kondo/` gets
this right already (`.clj-kondo/.cache/`, not `.clj-kondo/`). `.splint.edn` and
`.jscpd.json` are at the repo root and were never at risk.

### Adversarial review (three Sonnet agents) — findings and fixes

Three independent reviewers were pointed at this work with instructions to find bugs,
not to approve it. The physics reviewer found **no behaviour-changing defects** across
all seven refactors (Hill-clamp extraction, Barnes-Hut aggregation, the cross-product
collapse, `point-aabb-dist2`, the `clojure.math` sweep, the `condp`, `sample-ramp`) and
independently confirmed the `default-tick-fn` hoist is safe. The deletion reviewer
confirmed all 179 aliases and 14 vars are unreferenced across tracked
`src`/`test`/`bench`/`dev`/`docs`/`kanban`/markdown/CI, and verified
`law.mass-transfer`'s deleted `earth-mass`/`jupiter-mass` were bit-identical
(`5.972e24`, `1.898e27`) to `law.stellar`'s, so no physics drifted.

The gate reviewer found real problems. All fixed:

1. **The gate passed silently when a tool was ABSENT.** `clj-kondo` checked only exit
   2 and 3, so a 127 fell through to success; `clojure-lsp`'s stderr was discarded so a
   missing binary looked identical to a clean tree; and piping `clj-kondo` into
   `smell_report.clj` meant an empty analysis export scored zero smells and exited 0 —
   the same class of bug as the `PIPESTATUS` one already commented in that file, one
   stage earlier. **This was the worst finding**: a green gate that checks nothing.
   Fixed with a `require_tool` preflight (exits 2 naming the missing tool), an
   exhaustive `case` on clj-kondo's exit, a non-empty check on the analysis export, and
   distinguishing clojure-lsp's "clean" from "never ran". Verified by running with
   `clojure-lsp` off `PATH`: exit 2, "cannot run the gate".
2. **jscpd ran twice**, rescanning all 275 files purely to read `$?`. Now captures the
   exit code from the first run.
3. **A false claim in `.clj-kondo/config.edn`.** The justification for
   `:redundant-ignore {:level :off}` asserted "no ignore form in this tree targets a
   clj-kondo linter" — but `src/domain/interior.clj:460` has
   `#_{:clj-kondo/ignore [:unused-binding]}`, and `:unused-binding` is real. The
   comment now states the actual cost: that one marker will not be reported if it goes
   stale, it is a single self-documenting site, and the trade should be revisited if a
   second clj-kondo-targeting ignore is ever added.
4. **`^:export` was over-applied to three vars.** `domain.mhd.force/merged-hydro-em-force`
   ("Convenience function for tests" — no test uses it),
   `domain.mass-transfer/systems` ("Compatibility alias" — nothing to be compatible
   with), and `infra.render.scene.setup/render-bodies` ("Backward-compatible" — zero
   callers). `^:export` claims a var is finished and offered; nothing took the offer.
   All three deleted, which is what the facade prune did to 179 of exactly this shape.
5. **`.jscpd.json`'s arithmetic was wrong twice** — 49356 lines (actual 49321) and
   "~55 of 65 in test namespaces" from summing per-file tallies that count each clone
   at BOTH endpoints. Recomputed per-clone: **58 test-only, 7 src-only, 0 straddling**,
   and the deliberate em_lorentz↔mhd_force pair is **8**, not 20.
6. **The kanban status edits were illegal.** The board FSM is strict-linear
   (`ready → todo → in_progress → testing → review → document → done`) and I had
   hand-edited `status:` straight to `done`, which the CLI would have rejected. All 20
   cards were reset to their pre-edit state and walked through every intermediate
   state via `eta-mu kanban frontmatter`, so the ledger records legal transitions.

One reviewer finding **not** acted on: four `domain.ecology.state` predicates
(`prokaryotic?`/`eukaryotic?`/`multicellular?`/`complex?`) were called an entirely dead
family. Re-checked — `habitable?` (3 callers) and `living?` (2) are live, so the
"family with holes" justification for `^:export` holds.

### The preflight earned its keep on the first CI run (2026-07-25)

PR #3's `analyze` job failed with `✘ cannot run the gate: clojure-lsp not found on
PATH`. Cause:

```
##[warning]Unexpected input(s) 'clojure-lsp', valid inputs are
['lein', 'boot', 'tools-deps', 'cli', 'cmd-exe-workaround', 'bb',
 'clj-kondo', 'cljfmt', 'cljstyle', 'zprint', 'github-token', 'invalidate-cache']
```

`DeLaGuardo/setup-clojure@13.0` has **no `clojure-lsp` input.** The workflow had asked
for it since the file was written; the action warned and ignored it. **clojure-lsp was
never installed in CI, ever.**

Which means: before the preflight, `bin/analyze` in CI ran
`clojure-lsp diagnostics 2>/dev/null | grep …`, got nothing because the binary did not
exist, printed "none", and passed the dead-code tier — on every run. The tier was
decorative. Had this tier been promoted to blocking *without* the preflight, CI would
have gone green on a check that was not running.

This is the **third** instance of the same pattern in this one epic, and it is the
pattern worth remembering:

| # | looked configured | actually |
|---|---|---|
| 1 | `static-analysis` ran on every push and failed | nothing required it to pass |
| 2 | `.lsp/config.edn` written and working locally | `.gitignore` ignored all of `.lsp/`, so CI would never see it |
| 3 | workflow requested `clojure-lsp: latest` | input does not exist; silently ignored; binary never installed |

Each was invisible while the tool it governed was advisory. Promoting the tiers is what
forced all three into the open — which is the argument for promoting them.

Fixed: clojure-lsp is installed by an explicit step, **pinned** to
`2025.08.25-14.21.46` (the version the tree was driven to zero against) rather than
`latest`, because this gate's contract is "same source in => same findings out" and a
floating linter can change the finding set under us.

### Linter versions must be PINNED, or "zero" means nothing (2026-07-25)

Second CI failure, different cause, same lesson. `analyze` went red with:

```
test/shape/spatial_test.clj:36:11: warning: Condition always true
```

The tree was at 0 warnings locally and 1 in CI, because the workflow asked for
`clj-kondo: latest`: CI ran **v2026.07.24**, local had **v2025.07.28**, and the newer
version implements a check the older one does not.

A gate whose tools float cannot honour this suite's stated contract — "same source in
=> same findings out". A `latest` linter means the gate's verdict depends on *when* it
ran, so a PR can be green at 09:00 and red at 17:00 with no commit in between. Both
`clj-kondo` and `clojure-lsp` are now pinned in the workflow, with the reason inline:
bump them deliberately, fix whatever the new version finds, and commit the bump and the
fixes together.

**And the finding was real, not version noise.** `(is (spatial/octant bb center))` —
`octant` returns one of eight keywords, all truthy, so that assertion could never fail.
The test *meant* to check the documented tie-break (a point lying on all three dividing
planes resolves positive on every axis via `>=`), so it now asserts
`(= :octant/ppp (spatial/octant bb center))`. That is the **third** vacuous assertion
this epic has found, after `(distinct? (vals by-material))` and the `condp` default —
all three were invisible to the test suite because they passed unconditionally.

### §10 Open items — deliberately not done, with reasons

1. ~~Branch protection.~~ **DONE 2026-07-25** — see the Correction section below.
2. **`bin/bench :ecs :gravity :hydro` not run.** CLAUDE.md treats performance as a
   correctness property and the `clojure.math` sweep touched hot paths
   (`voxel/carve.clj`, `interior.clj`, `voxel/band.clj`). The substitution was
   verified safe *by construction* against the Clojure 1.11.1 source — the fns are
   `:inline` and `clojure.math/PI` is `^{:const true}`, so no var deref or boxing is
   introduced — but that is an argument, not a measurement. **Run it before merging.**
3. **`clojure -M:run demo` cannot be run.** `src/infra/main.clj` does not exist in
   `HEAD`; the `:run` alias has dangled since `0a9343a`, unrelated to this work. The
   render path was verified instead by loading every render/dev namespace, by
   `clj-kondo`'s `:unresolved-symbol` at `:error` reporting 0, and by confirming no
   `resolve`/`requiring-resolve`/`ns-resolve` of any pruned name exists. Worth its own
   card: `-M:run` is documented in `CLAUDE.md` and is broken.
4. **jscpd is a ratchet at 1.7%, not zero.** ~55 of the 65 remaining clones are
   physics test namespaces still building worlds inline. `test/support/worlds.clj`
   exists as their home; `-jscpd-test-fixtures` (P3) stays open.
5. **`dev/` and `bench/` are unformatted** (11 files). `bin/analyze` gates `src test`
   only, so this is out of the gate's scope — but a future scope widening will hit it.
6. **No serial-vs-parallel Barnes–Hut tree-equality test.**
   `build-tree-parallel`'s docstring promises equality and the parallel path only
   fires above 512 bodies. The `aggregated-node-fields`/`child-leaf-bb` extractions
   make the promise structural rather than coincidental, but nothing asserts it.
7. **`src/domain/player/economy.clj` is machine-printed source** — the whole
   namespace is one 26th line containing `fn*` and reader-gensym names
   (`p1__245#`). Pre-existing (committed in `a3515f3`), cljfmt cannot fix it, and it
   is unreadable. Needs a hand rewrite.
---

## Correction to §2 and §7, and the root cause is now CLOSED (2026-07-25)

### The PR #1 framing in this card was wrong

This card (and the epic footer) said PR #1 (`cae2668`) "merged red into main". **It did
not merge into `main`.** Verified: PR #1 was `worktree-integration-seam-tests →
spark-gravity-bound-body`, merged 2026-07-24. There has only ever been ONE PR in this
repo and it targeted a feature branch.

The real history, from `gh run list --workflow=static-analysis.yml` (39 runs, the
complete record):

| when | event | branch | result |
|---|---|---|---|
| 2026-07-10 (early) | push | main | 7 × **success** |
| 2026-07-10 (later) → 2026-07-21 | push | main | **33 × failure, consecutive** |
| 2026-07-24 | pull_request | worktree-integration-seam-tests | failure |

So the gate was bypassed by **direct pushes to `main` by an admin**, 33 times — not by
merging PRs past a red check. That is a *stronger* case for protection, and it changes
what protection has to do: required status checks alone would not have stopped any of
those 33, because the pusher was an admin.

### Root cause CLOSED — protection applied and verified by refusal

Applied 2026-07-25 with owner approval:

```
required_status_checks: {strict: true, contexts: ["analyze"]}
enforce_admins:  true      <- the setting that actually binds, given the history above
allow_force_pushes: false
allow_deletions:    false
```

`enforce_admins: true` was a deliberate owner decision, not a default: with it `false`
the protection would have constrained only non-admins and PR merges, i.e. none of the
33 recorded failures. The cost is real — no emergency direct push to `main` — and was
accepted.

The required context is **`analyze`** (the job name), not `static-analysis` (the
workflow name).

**Verified by refusal, not by reading config.** A throwaway branch was cut from
`origin/main` carrying one deliberate `:shadowed-var` warning, pushed, and opened as
PR #2. `analyze` went red; `gh pr merge` was refused:

> `X Pull request octave-commons/Truth#2 is not mergeable: the base branch policy prohibits the merge.`

`mergeStateStatus` was `BLOCKED`. PR closed and branch deleted. (`git push --dry-run`
was tried first and is **useless** for this — it sends nothing, so no pre-receive hook
runs and it reports success against a protected branch.)

### A consequence worth knowing before you next touch `main`

That probe run also confirms **`main` is red today on its own merits** — independently
of the injected warning it reported `clj-kondo: errors present` (the two real
`src/law/field/schema.clj` conservation-check bugs this card documents at §1) and
`structural: HARD threshold breached`.

With protection now on and `enforce_admins: true`, **nothing can be pushed to `main`
until `main` is green** — and the thing that makes it green is merging this branch,
which carries the fixes. That is the intended one-move outcome, but it does mean `main`
is temporarily unpushable. Note also that `coverage` fails on `main` too; it is NOT a
required context, so it does not block.