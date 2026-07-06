# Protoplanetary Disk and Planet Formation Spec

**Status:** ready for implementation
**Milestone:** M3 in `docs/specs/epic-phase0-physics-honesty.md`
**Scope:** the **disk-side** contract — disk-regime classification, GI fragmentation rules, and the seeder's wiring. The **planet-growth physics** (accretion-derived mass, sub-grid planetesimals, condensation-sequence composition, Gammie α(β)) is `docs/specs/core-accretion-physics-realspec.md`. The **radial profile** that would replace the single-annulus regime is deferred: `docs/specs/radial-disk-structure-realspec.md` (D1).
**Depends on:** `docs/research/physics/protoplanetary-disks-planet-formation.md`

---

## 1. Goal

The current `disk-evolution-system` spawns `:planetesimal`, `:gas-giant`, and `:brown-dwarf` bodies directly from disk mass whenever `M_disk/M_star > 0.1`. This is physically wrong: disks fragment into gas-giant embryos via gravitational instability only under strict cooling conditions, and terrestrial/ice planets form by core accretion far below our mass resolution.

This spec:
- restricts direct disk fragmentation to `:gas-giant` embryos;
- requires both `Toomre Q < 1` and `t_cool Ω < 3` (Gammie 2001) for fragmentation;
- keeps `:planet` seeding as the only source of planets, driven by solid surface density and the snow line;
- adds disk-regime and solid-surface-density tracking so the UI and tests can verify behavior.

---

## 2. Invariants

1. `:planet` entities are created **only** by `domain.planet-formation/planet-seeds`.
2. Direct disk fragmentation produces **only** `:gas-giant` embryos, and only when the disk is both Toomre-unstable and fast-cooling.
3. Streaming instability and core accretion are **sub-grid**: they appear as solid surface density, core-accretion timescale, and the planet seeder.
4. Disk mass, angular momentum, and composition are conserved through every spawn.
5. A disk cannot fragment into embryos and seed planets on the same tick for the same star.

---

## 3. Component changes

### 3.1 New component: `c/disk-regime`

```clojure
(def disk-regime :component/disk.regime)
```

Value shape per annulus or globally:

```clojure
{:toomre-q double
 :cooling-beta double
 :regime [:stable-disc :gravito-turbulent :fragmenting :core-accretion-zone]
 :solid-surface-density kg/m^2
 :snow-line m}
```

### 3.2 New component: `c/disk-fragments-spawned`

Counter of direct GI fragments already spawned by a disk, used to cap repetition.

### 3.3 Existing components unchanged

- `c/disk-mass`, `c/disk-angular-mom`, `c/spawn-request-disk`, `c/spawn-request-planet`, `c/planets-seeded`.

---

## 4. Disk regime classification

For a star of mass `M` with disk mass `m` at radius `r` and temperature `T`:

```clojure
(let [Q (toomre-q M m r T)
      beta (cooling-time-ratio M m r T)]
  (cond
    (> Q 1.0)                  :stable-disc
    (and (<= Q 1.0) (< beta 3.0)) :fragmenting
    :else                      :gravito-turbulent))
```

A disk annulus is a `:core-accretion-zone` when `Q > 1.5` and `sigma-solid > 0`.

---

## 5. Fragmentation rules

### 5.1 Allowed fragmentation

A fragment is spawned only if all of the following hold:

1. `M_disk / M_star > disk-fragment-threshold` (0.1).
2. The outer disk annulus is `:fragmenting` by the regime classifier.
3. The embryo mass computed from disk mass is above `opacity-limit-mass` but below `hydrogen-burning-mass` (so it becomes `:gas-giant`, not `:protostar`).
4. `c/disk-fragments-spawned` is below a per-star cap (default 3).

### 5.2 Fragment spec

```clojure
{:matter-state :gas-giant
 :composition  star-envelope-composition
 :temperature  300.0
 :mass         embryo-m}
```

The fragment radius uses `planet-material-density`.

### 5.3 Binary formation

For `M_disk/M_star > binary-fragment-threshold` (0.5), the fragment is `:protostar` and represents a stellar companion, not a planet.

### 5.4 Fragment mass cap

Direct GI fragments are capped at `0.5 * law/deuterium-burning-mass` so they always classify as `:gas-giant`. Stellar companions use the binary threshold and spawn `:protostar`.

---

## 6. Planet seeding rules

`domain.planet-formation/planet-seeds` remains the **one-shot** sub-grid seeder. The disk-side contract here requires:

1. Use `c/disk-regime` `:solid-surface-density` rather than recomputing it from disc bodies.
2. Debit disk mass and angular momentum from the star's disk components.

The seeder's *growth physics* — accretion-derived mass (critical core mass + viscous-supply-limited runaway gas), sub-grid planetesimal-formation timescale, and composition drawn from `c/comp-condensed` at the formation radius — is specified in `core-accretion-physics-realspec.md`. That spec is what turns the current `min(0.3·annulus-mass …)` heuristic and the static `planet-composition` lookup into emergent outcomes.

**Also in M3 (disk-side cleanups, task cards):**
- **Gammie α(β):** derive `disk-viscous-alpha` (`stellar.clj:1419`) from the cooling β instead of a `^:const` (`α = 1/((9/4)γ(γ−1)β)`). Card `gammie-alpha-beta-coupling`.
- **Regime-tag unification:** `disk-regime-map` (`stellar.clj:1568`) and `disc-regime` (`stellar.clj:1544`) emit two divergent tag vocabularies, neither fully covered by `law/field` closed sets (`field.clj:70,75`); `:streaming-zone` is never emitted. Unify to one tag set and add it to the schema. Card `disk-regime-tag-unification`.

---

## 7. System responsibilities

### 7.1 `domain.stellar/disk-evolution-system`

- Compute `c/disk-regime` for each disk-holding star.
- Apply viscous accretion as now.
- Spawn GI fragments only under the rules in §5.
- No longer spawn `:planetesimal` or `:brown-dwarf` from disk mass.

### 7.2 `domain.planet-formation/planet-seeds`

- Read `c/disk-regime` for solid surface density and snow line.
- Produce `:planet` entities with explicit element compositions from `domain.chemistry`.

---

## 8. Tests

1. `massive-cool-disk-fragments-into-gas-giant` — assert Q < 1, beta < 3, spawn state is `:gas-giant`.
2. `massive-hot-disk-does-not-fragment` — assert Q < 1 but beta > 3, no spawn.
3. `disk-never-spawns-planetesimal` — sweep parameters, verify no `:planetesimal` spawn request from disk.
4. `planet-seeds-are-only-planet-source` — verify `c/spawn-request-planet` is the only path to `:planet`.
5. `snow-line-jumps-solid-surface-density` — assert factor ~3.5 beyond snow line.
6. `disk-mass-and-angmom-conserved-through-fragment-spawn`.

---

## 9. Promotion path

| File | Change |
|------|--------|
| `src/domain/ecs/components.clj` | Add `disk-regime`, `disk-fragments-spawned`. |
| `src/law/planet_formation.clj` | Add regime schema, solid-surface-density schema, snow-line constants. |
| `src/domain/stellar.clj` | Update `disk-evolution-pass` to compute regime and restrict fragments. |
| `src/domain/planet_formation.clj` | Consume `c/disk-regime`, emit explicit compositions. |
| `src/domain/integrator.clj` | Ensure disk mass/angmom conservation. |
| `test/domain/disk_evolution_test.clj` | Add fragmentation and conservation tests. |
| `test/domain/planet_formation_test.clj` | Add snow-line and composition tests. |

---

## 10. Decisions

1. **Disk regime is one scalar per star** in `c/disk-regime`. Per-annulus regimes are a future radial-disk upgrade.
2. **Fragment mass cap:** direct GI fragments are capped below the deuterium-burning limit to guarantee `:gas-giant` classification.
3. **Planet seeder remains one-shot** per star+disk; repeated seeding is deferred until disk dissipation and migration are modeled.

## 11. Resolved questions (were open)

1. **GI fragment cap** → default **3** per star (§5.1), then the disk must settle to a binary (above the 0.5 mass ratio) or a stable state. Revisit only if runs show unphysical fragment swarms.
2. **Migration after seeding, or static?** → **Static now; migration deferred (D2)**, gated on the radial disk profile (D1) — the torque prescription needs `Σ(r)`, `T(r)`. Seeded planets hold their initial orbits until then.
3. **Annulus-resolved surface density (radial profile)** → **Deferred (D1)**, `radial-disk-structure-realspec.md`, with a concrete trigger (scalar Q misclassifies a radially-unstable disk).
4. **Pebble vs planetesimal accretion** → planetesimal accretion now (M3); **pebble deferred (D6)**, gated on D1 + a demonstrated failure to grow giants in time.
5. **Composition fidelity (inherit disk C/O, ice fraction)** → **built in M3** via `c/comp-condensed` at the formation radius (core-accretion-physics-realspec §6); CO/CO₂ lines are D8.
6. **GI fragment survival / rapid migration into star** → folds into migration (**D2**).
7. **Multi-star disks** → **Deferred (D5)**, gated on a persistent bound stellar binary sharing disk mass.
