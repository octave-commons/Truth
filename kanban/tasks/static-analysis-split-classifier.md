---
category: "specs"
labels: ["specs", "static-analysis", "domain", "ecs", "structural"]
write-id: "1784985243853-0.gb40var7yp95mqv0gee"
source: "kanban/tasks/static-analysis-split-classifier.md"
title: "domain.stellar.classifier: 62 vars — split on the three ECS system boundaries"
priority: "P1"
status: "done"
estimate: "8"
uuid: "static-analysis-split-classifier"
created_at: "2026-07-24T00:00:00Z"
---

# Split domain.stellar.classifier

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`
> Regressed: `kanban/tasks/static-analysis-split-stellar-core.md` (`done`)

`src/domain/stellar/classifier.clj` carries **62 var definitions** (19 public
`defn`, 30 `defn-`, 13 `def`) against a HARD gate of 60. It grew +651 lines on
this branch across the M5 handoff phases: `c1b88c5` (Phases 1-2), `a73b483`
(Phase 3), `e9f52c2` (Phase 4), all 2026-07-22.

Note the breach is **var count, not loc** — 967 raw loc is well under the 1200
loc hard gate. Shaving comments will not fix this; the namespace genuinely
carries three responsibilities.

## The split falls out of the file's own structure

The file already has explicit design-note section markers, and the **three ECS
systems it registers map cleanly onto three var clusters**:

| New namespace | Sections (current lines) | Vars | System |
|---|---|---|---|
| `domain.stellar.classifier.state` | complexity + `classify-system` (1-77), matter-state machine (78-221), condense tick + `classifier-system` (222-323) | 22 | `:classifier` |
| `domain.stellar.classifier.planet` | M5 P1 material/thermal (324-499), P2 orbit stability (500-530), P3 atmosphere + `classification-system` (531-729) | 24 | `:classification` |
| `domain.stellar.classifier.candidate` | M5 P4 candidate record + `handoff-system` (730-970) | 16 | `:handoff` |

Each lands under the 30-var warn line.

## Grounded integration (cite file:line)

- Three registry entries declare `:ns 'domain.stellar.classifier` —
  `src/domain/ecs/registry.clj:91`, `:113`, `:129`. All three must be updated.
- **Verified low-risk:** `:ns` is documentation and error-reporting only. The
  only non-declaration read is `registry.clj:592`
  (`{:id (or id (:ns entry)) :problems problems}`), and `registry-writes`
  (`:541`) keys off `:id`. Nothing resolves a system by namespace, so the split
  cannot break the fan-out.
- `domain.genesis.systems` requires the namespace and wires the emitters —
  update its requires.
- External call sites: ~24 distinct symbol references, dominated by
  `classify-next-state` (12), `handoff-system` (11), `core-condensation-density`
  (8), `classifier-system` (6), `material-class` (4), `classification-system`
  (4), `atmosphere-class` (4).
- Cross-section dependency to watch: `.candidate` reads `material-class` /
  `thermal-band` / `orbit-stable?` / `atmosphere-class` **off the frozen
  snapshot**, not by calling into `.planet` (see the Phase 4 design note at
  `classifier.clj:730-756`). So the dependency is `.candidate` → `.planet` for a
  handful of pure helpers only, not a cycle.

## Constraints

- **Leave no facade behind.** A thin `domain.stellar.classifier` re-export
  namespace is exactly what `static-analysis-dissolve-law-stellar-facade.md` is
  unwinding. Repoint the ~24 call sites.
- `test/architecture_test.clj` — `single-writer-ownership-holds` and
  `system-registry-well-formed` must stay green **without being edited**. Each
  system remains the sole writer of its component set; the split changes where
  the emitter is defined, not what it owns.
- `domain/` must not import `infra/` (`domain-never-imports-infra`).
- Performance is a correctness property (`CLAUDE.md`): `classification-system`
  and `handoff-system` run every tick.

## Done when

- [x] Three namespaces exist, each ≤ 30 vars; `domain.stellar.classifier` is gone.
- [x] `registry.clj:91,113,129` name the correct new namespaces.
- [x] `bin/analyze --strict` no longer lists any classifier namespace at all —
      all three fell off the god-namespace list entirely.
- [x] `clojure -M:test -n architecture-test` green (6 tests / 23 assertions),
      file unmodified.
- [x] `clojure -M:test` still 879 tests / 15486 assertions / 0 failures.
- [ ] `bin/bench :ecs` — deferred to the end of Wave 3, run once for all the
      structural work rather than per card.

---
Implemented 2026-07-24. HARD breaches 2 → 1 (only `derive-edits` remains).

**No cycle, contrary to the first reading.** A dependency scan flagged
`planet` as referencing `eligible-candidate?` and `build-candidate-record` from
`candidate`, which would have forced a facade. Both turned out to be inside the
`material-albedo` **docstring** (`classifier.clj:369`), not code. Real
dependency is one-way: `candidate` → `planet` (8 symbols), `state` standalone.

Final shape:

| Namespace | Vars | System | Requires |
|---|---|---|---|
| `domain.stellar.classifier.state` | 22 | `:classifier` | math, law.stellar, thermo, collapse, sink, ecs, components, profile |
| `domain.stellar.classifier.planet` | 24 | `:classification` | math, law.stellar, law.atmosphere, chemistry, ecs, components, stability, spatial |
| `domain.stellar.classifier.candidate` | 16 | `:handoff` | law.stellar, chemistry, ecs, components, stability, **planet**, spatial |

13 consumer files were rewritten (4 src, 7 test, 2 bench), each getting only the
namespaces it actually uses — `genesis/systems.clj` needs all three,
`seeder.clj` only `state`, `orbital_stability_test.clj` only `planet`. Aliases
`cls-state` / `cls-planet` / `cls-cand`.

**A blind symbol-qualification pass was reverted.** Mechanically prefixing the 8
cross-namespace symbols in `candidate` also rewrote keyword literals
(`:material-class` → `:planet/material-class` — those are
`:planet-candidate` **contract keys**, so it would have silently broken the
handoff record), parameter names, and docstring prose. Redone against the 5
genuine call sites only. `material-class` at `candidate.clj:129,137` is a local
parameter, not a var reference, and was correctly left alone.

All 15 stale `domain.stellar.classifier/…` docstring cross-references across
`law/`, `domain/` and `test/` were repointed to the owning namespace, including
three that wrap across lines. Zero dangling references remain.
---