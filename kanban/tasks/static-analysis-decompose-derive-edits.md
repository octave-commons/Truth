---
category: "specs"
labels: ["specs", "static-analysis", "domain", "voxel", "structural"]
write-id: "1784985248551-0.55h2ya8opmcrtbbz9s"
source: "kanban/tasks/static-analysis-decompose-derive-edits.md"
title: "derive-edits: 78/80 code lines — one line of new physics re-breaches the gate"
priority: "P1"
status: "done"
estimate: "3"
uuid: "static-analysis-decompose-derive-edits"
created_at: "2026-07-24T00:00:00Z"
---

# Decompose derive-edits

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`
> Regressed: `kanban/tasks/static-analysis-decompose-mega-functions.md` (`done`)

`src/domain/voxel/carve.clj:406` — `derive-edits` is 116 raw lines, of which 32
are its (good, load-bearing) docstring. Once
`static-analysis-smell-metric-code-lines.md` lands, it measures **78 code lines
against a HARD gate of 80**.

That is not a pass, it is a coin flip. One added `cond` branch re-breaches it.
Decompose regardless of the metric fix — this card is *not* retired by that one.

## The seams are already there

The body is a single `let` running four distinct stages:

| Stage | Lines | Extract as |
|---|---|---|
| Offset-window generation — the `reach` box around the impact point | `:449-459` | `crater-offsets` |
| Cell classification — bowl paraboloid vs melt-floor band | `:460-477` | `classify-cell` (already a local `fn`; lift it) |
| Vapor/melt set selection — innermost `:v-vapor`, deepest `:v-melt` | `:478-494` | `vapor-and-melt-sets` |
| Edit assembly — `:after nil` / `:vapor` / `:melt` tagging | `:495-520` | `cells->edits` |

`classify-cell` is already written as a local `fn` at `:460`, so lifting it is
nearly free.

## Constraints

- Pure function, zero I/O. Covered by `test/domain/voxel_carve_test.clj`.
- The **emission order is load-bearing**: the fn ends
  `(mapv #(get edits %) (sort (keys edits)))` — sorted-offset order is the
  queue's replay-order discipline (see the docstring and
  `domain.voxel.queue`). Any extraction must preserve it.
- The sub-voxel no-op guard at `:440` (`d-tc` below
  `law/sub-voxel-diameter-m` → `[]`) must stay the first thing the function
  does — the card `collision-shock-voxel-carving` calls out "no rounded-up
  one-voxel poke" as a correctness property.
- Keep the docstring on `derive-edits`. The KNOWN-SIMPLIFICATION note about
  complex-crater relaxation (`:416-427`) is the honest record of what the
  carve does *not* do; it does not move to a helper.

## Follow-up, not scope

`voxel-focus-system` (`src/domain/voxel/focus.clj:238`) measures 86 raw / **57
code** lines and clears comfortably under the honest metric. Its three fold
stages (sculpt ops, carve plans, cooling jobs) are a clean extraction if the
function is touched for other reasons, but it is not carded work.

## Done when

- [x] `derive-edits` is under 40 code lines — it fell off the mega-function
      list entirely (was 80 code / 116 raw).
- [x] `bin/analyze --strict` lists neither `derive-edits` nor
      `voxel-focus-system` as HARD. **HARD breaches: 0.**
- [x] `clojure -M:test -n domain.voxel-carve-test` green (11 tests / 81 assertions).
- [x] `clojure -M:test` still 879 tests / 15486 assertions / 0 failures.

---
Implemented 2026-07-24. Four private helpers extracted ahead of `derive-edits`:

| Helper | Params | What |
|---|---|---|
| `crater-offsets` | 2 | the candidate offset box around the impact point |
| `crater-cells` | 2 | paraboloid/melt-floor classification, `geom` passed as a map |
| `vapor-melt-sets` | 3 | innermost-vapor / deepest-melt selection under the volume budget |
| `cells->edits` | 4 | the `{offset edit}` map |

`geom` is a map specifically to stay under the 5-param bloat line — passing
`anchor`/`R`/`r-crater`/`d-exc`/`t-melt` positionally would have traded a
mega-function warning for a parameter-bloat one. Confirmed: no new entries in
the PARAMETER BLOAT section.

Load-bearing properties preserved and re-checked: the sub-voxel `[]` guard is
still the first thing the function does; emission is still sorted-offset (now
with an explicit comment saying why); `group-by :kind` preserves within-group
order, so vapor/melt selection is unchanged. One incidental improvement — the
melt branch called `live-voxel` twice (`(and (contains? …) (some? (live-voxel …)))`
then `(let [v (live-voxel …)]`); it is now a single `when-let`.

### Also fixed here: `bin/analyze` was mis-attributing its own exit code

`bin/analyze --strict` still reported `structural: HARD threshold breached`
with **HARD breaches at 0**. Cause: the script sets `-o pipefail`, so `$?`
after `clj-kondo … | clojure -M dev/smell_report.clj` is the last *non-zero*
status in the pipeline — and clj-kondo exits **2** whenever it has warnings.
`smell=$?` therefore captured clj-kondo's warning status, not `smell_report`'s.

The structural gate has effectively been "fails whenever kondo has any
warning", reported under the wrong tool's name. That is its own small lesson
about why the gate got ignored: it was telling people something untrue about
which check was failing. Fixed to `smell=${PIPESTATUS[1]}` with a comment.
---