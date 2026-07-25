---
category: "specs"
labels: ["specs", "static-analysis", "process", "convention"]
write-id: "1784985260281-0.bcwilhngmq7g66hz4d"
source: "kanban/tasks/static-analysis-unused-pending-convention.md"
title: "Formalize UNUSED-PENDING: the marker that separates incomplete-but-intended from rot"
priority: "P2"
status: "done"
estimate: "3"
uuid: "static-analysis-unused-pending-convention"
created_at: "2026-07-24T00:00:00Z"
---

# Formalize the UNUSED-PENDING convention

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`

`CLAUDE.md` warns: "Several features are coded but never ticked (inter-body EM
field coupling, chemistry evolution paths, some player mechanics) — they look
dead but are incomplete, not abandoned."

That warning currently lives only in prose. In the code, incomplete-but-intended
vars are indistinguishable from rot — which is why a dead-code sweep is
dangerous here, and why the same triage has to be redone every time.

The repo has already invented the marker organically: `UNUSED-PENDING` appears
at `src/law/crater.clj:77,111,116` and `src/domain/voxel/carve.clj:53,423`.
Make it the documented convention.

## The convention

An unconsumed var that is intended, not abandoned, carries:

```clojure
;; UNUSED-PENDING <kanban/tasks/some-card.md> — one line on what completes it.
```

Both parts are required. A marker with no card reference is not a marker, it is
a promise nobody can audit.

Document it in `AGENTS.md` and `docs/STATIC-ANALYSIS.md` alongside the
`;; Intentional:` suppression convention.

## Apply to the 21 incomplete-not-abandoned vars

**The 7 `domain.em` coupling vars** — `src/domain/em.clj:26` `mu0-over-4pi`,
`:30` `dipole-moment`, `:42` `external-field-at`, `:55` `self-gravity-pressure`,
`:63` `min-flux-retention`, `:98` `magnetic-torque`, `:102`
`braking-fraction-per-time`.

This is `CLAUDE.md`'s first named case and it is worse than the prose suggests:
these are facade aliases whose **underlying implementations in `domain.em.field`
/ `domain.em.lorentz` are also unconsumed**. Contrast the siblings in the same
facade — `net-field-at`, `field-sources`, `flux-freeze` — which have live callers
at `src/domain/stellar/wind.clj:243,277`,
`src/infra/render/scene/particles.clj:116`,
`src/domain/stellar/collapse.clj:117`. So the field **substrate** is wired and
the **coupling** is not, exactly as documented. Spec:
`kanban/tasks/phase-0-protoplanetary-disc-implementation-spec.md:160` specifies
`magnetic-torque: τ = r × f`.

Note the ordering dependency: `static-analysis-facade-prune.md` deletes
zero-caller facade aliases. These seven must be **carved out** of that prune —
marker, not delete.

**The 7 chemistry-evolution vars** (`CLAUDE.md`'s second named case) —
`src/domain/chemistry.clj:83` `enrich-composition`, `:191` `material-phase`,
`:207` `can-form-molecules?`, `:238` `bulk-composition-category`, `:277`
`potential-atmosphere`, `:502` `fusion-products`, `:514` `supernova-enrichment`.
Cards: `nebular-chemistry-and-composition-spec.md`,
`metal-enrichment-and-seeding-spec.md`,
`roadmap-phase-0-physics-honesty-chemistry-disks-plasma-inspection.md`.

**The remaining 7**, each with a named card or existing marker:
- `src/domain/mass_transfer.clj:395` `systems` → `gradual-mass-transfer-spec.md`
- `src/domain/mhd/force.clj:227` `merged-hydro-em-force` →
  `spec-merged-hydro-em-pair-loop-with-shared-neighbor-cache.md`
- `src/domain/voxel/carve.clj:101` `coupling-parameter` — docstring says
  "Debug/logging quantity"; `:53` and `:423` already carry the marker
- `src/domain/stellar/disc_evolution.clj:16` `disk-formation-threshold` →
  `exposed-tunables-and-settings-menu-spec.md`
- `src/domain/stellar/thermodynamics.clj:193` `luminosity-from-fusion` → Phase 1
- `src/infra/camera/navigation/input.clj:161` `min-approach-distance` →
  `focus-zoom-lod-ui-spec.md`
- `src/infra/render/units.clj:74` `render->phys-radius` →
  `phase-0-render-units-coordinate-transform-spec.md`

Plus, from the kondo sweep: `src/domain/interior.clj:463` unused binding `kind`
— the caller at `:507` computes it and `law.voxel/resource-cell-schema`
(`src/law/voxel.clj:121-131`) has no `:kind` key;
`src/domain/voxel/band.clj:80-86` documents the consequence. Marker with a
pointer to the slice-1 schema gap, **not** an underscore prefix.

## Open item — verify before asserting

`clojure-lsp`'s `unused-public-var` suppresses by **var name, regex, or defining
macro** (`:exclude`, `:exclude-regex`, `:exclude-when-defined-by`). It does
**not** read comment markers.

So determine, and record the answer here:
1. Whether var metadata (`^:api`, or a custom key like `^{:unused-pending "card"}`)
   can drive suppression, possibly via a generated `:exclude` list.
2. If not, the marker stays a **human** convention and the exclusion list is
   maintained explicitly — which is honest but must be stated, not glossed.

Do not claim the linter honours the convention until it has been demonstrated.

## Done when

- [ ] `AGENTS.md` and `docs/STATIC-ANALYSIS.md` document the marker + card-ref
      requirement.
- [ ] All 21 vars carry a marker with a resolvable `kanban/tasks/…` reference.
- [ ] The seven `domain.em` vars are explicitly carved out of
      `static-analysis-facade-prune.md`.
- [ ] The linter-suppression question above is answered in writing, with
      evidence, in this card.
- [ ] `clojure -M:test` still 879 tests / 0 failures.

---
## Outcome (2026-07-25)

### The open item is RESOLVED — the marker can be machine-enforced

This card asked whether clojure-lsp can key off the convention rather than a
hand-maintained exclusion list, and said not to assert a mechanism works before
verifying it. Verified, and two mechanisms work:

| form | works? |
|---|---|
| `^:export` var metadata | **yes** |
| `#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}` discard form | **yes** |
| `^{:clj-kondo/ignore [...]}` inline var metadata | no |
| `:exclude-when-defined-by #{domain.ecs.dsl/defcomponent}` | **no** |

So the convention is now enforced per-site with its reason at the site — no
generated exclude list, no hand-maintained inventory to drift.

`:exclude-when-defined-by` fails for a specific and worth-recording reason:
`.clj-kondo/hooks/ecs_dsl.clj` **rewrites** `defcomponent`/`defevent` into plain
`def`/`defn` forms, so clj-kondo's analysis records `:defined-by clojure.core/def`
and the defining macro's identity is gone before clojure-lsp ever sees it. Excluding
`clojure.core/def` would exempt the whole codebase. The three DSL test namespaces
are therefore excluded wholesale in `.lsp/config.edn`, with that cost stated in the
file.

### Two markers, not one

The single `UNUSED-PENDING` marker this card imagined split in two, because two
genuinely different things were being conflated:

- **`^:export`** — a var deliberately public with no internal consumer, and
  *finished*: `law/` schemas/contracts/constants (88 vars), debug/tooling accessors,
  compat aliases, and predicate families whose siblings are live (a family with holes
  reads worse than a complete one). Zero extra lines.
- **`UNUSED-PENDING`** — incomplete but intended, exactly CLAUDE.md's "coded but
  never ticked" class. 33 vars. Requires the token, one line on what is missing, and
  a `kanban/tasks/…` or `docs/…` cross-reference. Deliberately verbose: pending work
  should read as pending.

Cheapness is the risk with `^:export`, and `docs/STATIC-ANALYSIS.md` says so: it is a
claim the var is finished and offered, not a way to quiet a var you haven't thought
about.

### The 33 UNUSED-PENDING vars

| n | where | waiting on |
|---|---|---|
| 7 | `domain.em` | inter-body EM coupling — substrate wired, coupling not |
| 10 | `domain.chemistry` | chemistry-evolution paths; no tick calls them |
| 6 | `domain.stellar.{disc,disc-evolution,temperature,thermodynamics}` | disc/structure physics ahead of its emitter |
| 2 | `domain.planet-formation.physics` | seeder wiring |
| 8 | `infra.{camera,inspect,menu.widgets,camera.navigation.input}` | `docs/designs/ux-architecture.md` |

The `domain.em` seven confirm this card's reasoning: their impls in
`domain.em.field`/`domain.em.lorentz` are *also* unconsumed, while siblings
(`net-field-at`, `field-sources`, `flux-freeze`) have live callers. The field
substrate is wired; the coupling is not. Not facade indirection hiding a live var.

Also applied: `src/domain/interior.clj`'s `resource-cell` `kind` parameter, with the
`law.voxel/resource-cell-schema` gap and `domain.voxel.band/cell-material`'s
element-content inference documented at the site.

Documented in `docs/STATIC-ANALYSIS.md` › Suppression conventions, `CLAUDE.md`, and
`AGENTS.md`.
---