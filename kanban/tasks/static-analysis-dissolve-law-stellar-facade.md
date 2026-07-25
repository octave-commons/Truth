---
category: "specs"
labels: ["specs", "static-analysis", "law", "structural"]
write-id: "1784985246221-0.a0ied9obr1oqvbrvxj4"
source: "kanban/tasks/static-analysis-dissolve-law-stellar-facade.md"
title: "law.stellar: 69 re-exports, 27 dead, stacked on a second pass-through facade"
priority: "P1"
status: "done"
estimate: "5"
uuid: "static-analysis-dissolve-law-stellar-facade"
created_at: "2026-07-24T00:00:00Z"
---

# Dissolve the law.stellar double facade

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`

`src/law/stellar.clj` is 82 lines of `(def x other/x)` and nothing else — 69
vars, zero logic — which trips the god-namespace HARD gate at 60 vars. It
delegates to `law.stellar.orbital`, which is **itself** a pass-through facade
over `law.stellar.orbital.constants` and `law.stellar.orbital.dynamics`.

Owner decision (2026-07-24): **dissolve, do not exempt.** `dev/smell_report.clj:40`
has a `facade-namespaces` var-count exemption (`domain.player`,
`domain.ecology`, `infra.render`) that `law.stellar` would fit — but adding it
there hides a real structural problem behind a category, and two stacked
facades is not a category, it is drift.

## Measured ground truth

- **65 files** reference `law.stellar`, under three aliases (`law`, `ls`,
  `law-stellar`).
- **42 of the 69 re-exports are live; 27 have no consumer anywhere in `src`,
  `test` or `bench`.**
- Of the 42 live: **30** originate in `law.stellar.orbital.constants`, **11** in
  `law.stellar.orbital.dynamics`, **1** (`matter-state-contract`) in
  `law.stellar.schema`.
- `law.stellar.orbital` is required by exactly **3 files**:
  `src/law/stellar.clj`, `src/domain/gravity/barnes_hut/force.clj`,
  `src/domain/physics/cache/soa.clj`.

The 27 dead re-exports:

```
accretion-radius-schema  angular-momentum-schema  atmosphere-class-schema
atmosphere-class?        brown-dwarf-desert-mass  compact-matter-states
debris-mass-threshold    feeding-zone-hill-factor fusion-sustain-temp-threshold
material-class-schema    material-class?          matter-state-schema
nebula-cloud-contract    nebula-cloud-schema      oblateness-schema
orbital-cleared?         planet-mass-threshold    planet?
retained-species-schema  retained-species?        rotation-axis-schema
spin-schema              star-mass-threshold      stellar-system-contract
stellar-system-schema    thermal-band-schema      thermal-band?
```

## Scope

1. **Delete the 27 dead re-exports.** This alone takes `law.stellar` from 69 to
   42 vars and clears the HARD gate with **zero consumer churn**. Land it first
   and independently — it is pure dead-code removal.
2. **Collapse the middle facade.** Point `law.stellar` directly at
   `law.stellar.orbital.constants`, `law.stellar.orbital.dynamics` and
   `law.stellar.schema`. Repoint the two non-facade consumers
   (`barnes_hut/force.clj`, `physics/cache/soa.clj`). Delete
   `src/law/stellar/orbital.clj`.
3. `law.stellar` survives as a **single** facade at 42 vars — under the 60 hard
   gate, above the 30 warn line. That warn is accurate and is accepted.
4. Do **not** add `law.stellar` to `facade-namespaces` in `dev/smell_report.clj`.
   If a later change pushes it back over 60, that is a signal, not a nuisance.

## Risks

- `law/G`, `law/solar-mass` and `law/au` are referenced 105, 108 and 76 times
  respectively. Step 1 does not touch them; step 2 does not change their
  resolved value, only the delegation chain.
- The alias `law` is used for *other* namespaces in other files (e.g. `law.crater`
  in `voxel/carve.clj`). Any rewrite must resolve aliases **per file**, never by
  a repo-wide `law/` text substitution.

## Done when

- [x] `src/law/stellar.clj` is 42 vars (74 raw / 47 code loc) and requires only
      `law.stellar.orbital.constants`, `law.stellar.orbital.dynamics` and
      `law.stellar.schema`.
- [x] `src/law/stellar/orbital.clj` is deleted; nothing requires it.
- [x] `bin/analyze --strict` lists `law.stellar` at **warn**, not HARD.
- [x] `clojure -M:test` still 879 tests / 15486 assertions / 0 failures.
- [x] `clojure -M:test -n architecture-test` green.

---
Implemented 2026-07-24. 69 → 42 vars; HARD breaches 3 → 2.

**A third consumer was found that the initial audit missed.**
`src/domain/spatial/index.clj:28` requires the namespace with padded
whitespace — `[law.stellar.orbital      :as law-orbital]` — so a
single-space `\[law\.stellar\.orbital :as` grep did not see it. This is exactly
the failure mode `.agents/skills/whitespace-tolerant-require-audits/` exists
for; the audit was redone with `[[:space:]]+` and confirmed the true consumer
counts: `law.stellar` 65 files, `law.stellar.orbital` 3 (now 0),
`.orbital.constants` 4, `.orbital.dynamics` 4 (now 7), `law.stellar.schema` 3.

All three former `law.stellar.orbital` consumers used only `dynamics` symbols
(`body-softening`, `softening-cutoff-fraction`) and were repointed at
`law.stellar.orbital.dynamics` with alias `law-dyn`; the docstring references to
`law.stellar.orbital/body-softening` in `barnes_hut/force.clj:7,230`,
`physics/cache/soa.clj:35,149` and `spatial/index.clj:60` were updated too, so
no doc reference dangles.

The 42 live symbols resolve as 30 `constants` + 11 `dynamics` + 1 `schema`
(`matter-state-contract`). `law.stellar` was deliberately **not** added to
`facade-namespaces` in `dev/smell_report.clj` — if it grows back past 60 vars
that should register as a signal.
---