---
category: "specs"
labels: ["specs", "static-analysis", "tooling"]
write-id: "1784985241494-0.uh5uwwpfb6oidk4xc2"
source: "kanban/tasks/static-analysis-smell-metric-code-lines.md"
title: "smell_report counts docstrings as code — measure code lines, not documentation"
priority: "P1"
status: "done"
estimate: "2"
uuid: "static-analysis-smell-metric-code-lines"
created_at: "2026-07-24T00:00:00Z"
---

# Fix the measurer before the code

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`

`dev/smell_report.clj:110` computes function loc as `(inc (- end-row row))`, and
`:god-namespaces` uses raw `file-loc` (`:89`, `count` of `split-lines`).
Docstrings and design-note comment blocks therefore **count as code**, and the
metric penalises exactly what `CLAUDE.md` mandates ("Docstrings mandatory on
public vars", plus the project's heavy use of design-note comment headers).

`derive-edits` is 116 lines of which 32 are its docstring. `voxel-focus-system`
is 86 of which 8 are docstring and 20 are inline design-note comments.

## Scope

- Count **code lines only** — exclude blank lines, comment-only lines, and the
  docstring span — for both `:long-functions` (`:110`) and the `file-loc` feeding
  `:god-namespaces` (`:89`).
- The clj-kondo analysis already carries `:doc` on var-definitions (the
  `ANALYSIS_CFG` in `bin/analyze` sets `:var-definitions {:meta true}`), so the
  docstring span is derivable without re-parsing.
- **Report raw loc alongside code loc** in the rendered report. This must read as
  an honest measurement change, not a threshold weakening smuggled in as a
  refactor.
- Thresholds themselves are unchanged. Do not touch `thresholds` (`:23-28`).

## Grounded integration (cite file:line)

- `dev/smell_report.clj:89` — `file-loc` slurps and counts every line.
- `dev/smell_report.clj:110` — `(assoc d :loc (inc (- (:end-row d) (:row d))))`.
- `dev/smell_report.clj:141-142` — `hard?`/`tag` compare against `thresholds`;
  unchanged.
- `bin/analyze:44` — passes `:var-definitions {:meta true} :arglists true`.

## Measured effect — IMPLEMENTED 2026-07-24

Implementation: `code-line-flags` in `dev/smell_report.clj` scans each file
character-by-character tracking string and comment state. A line is code when it
holds a non-whitespace character outside a comment and outside a string body.
This handles docstrings and any other multi-line string with one rule rather
than special-casing the docstring position; `\\` outside a string is treated as
a char literal so `\\"` and `\\;` do not open strings or comments. Results are
memoized per file. **Thresholds untouched.**

| Function | Raw | Code | Tier |
|---|---|---|---|
| `voxel-focus-system` (`src/domain/voxel/focus.clj:238`) | 86 | **58** | HARD → warn ✅ |
| `derive-edits` (`src/domain/voxel/carve.clj:406`) | 116 | **80** | **still HARD** |

**Correction to the original estimate:** `derive-edits` was estimated at 78; it
measures **80**, landing exactly on the hard gate. It does **not** clear.
`static-analysis-decompose-derive-edits.md` is therefore mandatory, not
optional — which is the stronger form of what that card already argued.

HARD breaches: 4 → **3**.

Neither god-namespace HARD breach is affected, as predicted:
`domain.stellar.classifier` (62 vars, 967 raw → 534 code) and `law.stellar`
(69 vars, 91 raw → 74 code) breach on **var count**, not loc.

### Verification: exactly one tier change, and nothing moved up

Full before/after diff of the structural report. **One** entry changed tier:

- `voxel-focus-system` — HARD → warn (86 raw → 58 code). Legitimate: 8
  docstring lines plus 20 lines of inline design-note comment inside the body.

No entry was newly listed, and **nothing moved up a tier** — the change can only
reduce, never mask a new breach.

Everything else that moved fell below the advisory *warn* line (which does not
gate): 5 namespaces (`domain.ecs.registry` 618 raw, `domain.integrator.kinematics`
615, `domain.interior` 595, `domain.voxel.carve` 747, `domain.voxel.sculpt` 567
— all under 500 code lines and under 30 vars) and 24 functions, all previously
`warn` at 41-66 raw. Spot-checked as honest: `band-target`
(`src/domain/voxel/band.clj:182`) is 66 raw with a **31-line docstring**
(`:183-213`) and ~35 lines of body.

Cross-check on `derive-edits`, two independent methods agreeing: the scanner
reports 80; `116 raw − 32 docstring lines − 4 blank/comment lines = 80`.

## Done when

- [x] `bin/analyze --strict` structural section prints both code loc and raw loc.
- [x] The full before/after report diff is recorded above, naming **every**
      entry that changed tier. Exactly one did (`voxel-focus-system`, HARD →
      warn); nothing moved up; nothing was newly listed.
- [x] `derive-edits` is still decomposed afterwards
      (`static-analysis-decompose-derive-edits.md`) — it measures 80 and remains
      a HARD breach, so the metric fix does not retire that card.
- [x] `clojure -M:test` still 879 tests / 0 failures.

---
Implemented 2026-07-24. `dev/smell_report.clj` gains `code-line-flags` /
`file-code-loc` / `span-code-loc`; `:god-namespaces` and `:long-functions` now
carry both `:loc` (code) and `:raw-loc`. HARD breaches 4 → 3.
---