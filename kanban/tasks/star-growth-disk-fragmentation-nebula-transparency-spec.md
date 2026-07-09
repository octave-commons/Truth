---
uuid: "star-growth-disk-fragmentation-nebula-transparency-spec"
title: "Star Growth, Disk Fragmentation, and Nebula Transparency"
status: "done"
priority: "P1"
labels: ["specs", "phase0", "stellar", "disk", "render", "mass-transfer"]
created_at: "2026-07-09T18:00:00.000000000Z"
source: "kanban/tasks/star-growth-disk-fragmentation-nebula-transparency-spec.md"
category: "specs"
---

# Star Growth, Disk Fragmentation, and Nebula Transparency

**Status:** done
**Milestone:** M3 star-formation honesty pass, companion to `kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md`
**Companion docs:**
- `kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md` (disk-side fragmentation rules)
- `kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md` (mass-loss / wind loop)
- `kanban/tasks/gradual-mass-transfer-spec.md` (BHL sink accretion)
- `docs/research/physics/protoplanetary-disks-planet-formation.md`
- `docs/research/physics/protoplanetary-disks-extended.md`
- `docs/research/physics/stellar-nebula-mass-hierarchy.md`
- `docs/research/physics/stellar-mergers-accretion.md`
- `docs/research/physics/rate-limited-accretion-mass-transfer.md`
- `docs/notes/improve-planetary-disk-modeling-and-rendering-claude-session-005-now-let-me-verify-renderclj-still-compil.md` (disk rendered as volumetric fog, not sprites)

***

## 1. Goal

Fix two observed Phase 0 problems:

1. **Stellar growth stalls at ~0.25 M☉.** A single star forms, is surrounded by a bright disk/nebula, and does not grow further because its disk fragments into gas-giant embryos before the gas can accrete onto the star.
2. **Nebula / disk is visually too bright.** The volumetric fog is overexposed after the camera-relative render-origin shift, making the disk look like an opaque glowing cloud.

This spec changes the **disk-to-star mass ratio at which fragmentation occurs** and the **viscous accretion rate** so that the disk feeds the star before fragmenting, while keeping the disk visually present and still capable of spawning planets. It also tunes the **volume fog pass** for transparency.

***

## 2. Physics basis

### 2.1 Disk fragmentation is a high-mass-ratio event

The current code triggers direct gravitational-instability (GI) fragmentation when:

```clojure
M_disk / M_star > 0.1   ;; gas-giant embryo
M_disk / M_star > 0.5   ;; binary companion
```

These thresholds are appropriate for identifying **self-gravity** (`Q ≈ 1`) and recurrent spiral structure, but not for **fragmentation** of a disk around a low-mass star. The literature shows that for a **0.25 M☉** host, fragmentation requires the disk to be a substantial fraction of the stellar mass, and irradiation pushes the threshold even higher.

> **Key finding:** For a 0.25 M☉ star with stellar irradiation, disk-to-star mass ratios `q ≳ 1.4` are needed before the disk fragments; with a 10 K background, `q ≳ 0.7`. For higher-mass hosts the threshold drops, but it remains well above 0.1.

**Citation:** Mercer & Stamatellos (2020), *Fragmentation favoured in discs around higher mass stars*, arXiv:2001.06224.

> **Key finding:** Self-gravity becomes important at `M_disk/M_* ≳ 0.06`, recurrent spiral activity/fragmentation transitions at `≳ 0.1-0.2`, and strong fragmentation at `≳ 0.5-1`. Disk mass ratios above ~0.1 produce global spiral modes rather than local clumping; fragmentation is not guaranteed until the disk approaches the star's mass.

**Citation:** Kratter & Lodato (2016), *Gravitational Instabilities in Circumstellar Disks*, arXiv:1603.01280, §4.

### 2.2 Viscous accretion can be driven by gravitational instability

When the disk is self-gravitating, the effective Shakura-Sunyaev α can reach much higher values than the MRI baseline of ~0.01. Global spiral modes can transport angular momentum at α ≈ 1 when the disk mass approaches the star's mass; local GI-driven transport saturates around α ≈ 0.1.

> **Key finding:** An effective α derived from global spiral modes can reach ~1 when `M_disk → M_*`, and local gravitoturbulence saturates near α ≈ 0.1.

**Citation:** Kratter, Matzner, & Krumholz (2007), *Embedded, Accreting Disks in Massive Star Formation*, arXiv:0712.0853, §2.

This means the disk can drain onto the star faster than the current α = 0.01 assumption without introducing non-physical behavior.

### 2.3 Gas accretion onto protostars is disk-mediated in reality, but gameplay needs growth

In real star formation, most of the mass falls onto the protostar through a rotationally supported disk. The disk is mostly H/He gas; the solid component is only ~1% by mass. However, in the current simulation the **entire gas accretion stream** is routed to the disk, and the disk fragments before it can viscously accrete onto the star. The result is a star that plateaus and a disk that repeatedly sheds embryos.

The two physically defensible fixes are:

1. **Literature-grounded thresholds:** raise the fragmentation mass ratios and increase the viscous α so the disk feeds the star before fragmenting.
2. **Metal/solid disk budget:** route H/He gas directly to the star and only the metallic/solid mass to the disk. This matches the user's intuition that the disk should grow from non-volatile material, but it requires tracking gas vs. solid accretion separately.

This spec adopts **Option 1** as the minimal, research-backed change. Option 2 is listed as a follow-up if gameplay still demands a smaller disk.

### 2.4 Nebula brightness is a rendering-tuning issue

The volume ray-march pass uses:

```clojure
{:kappa 0.045
 :emission-scale 2.2
 :scatter-scale 2.5
 :jitter 1.0
 :visual-h-scale 10.0
 :visual-h-min 4.0
 :splat-gain 1.0}
```

and the shader samples density only when `dens > 0.0008`. After the camera-relative render-origin shift, the camera sits near the center of the disk, so the same optical depth now produces a much brighter image. The density cutoff and the emission/scatter scales are too generous for this viewpoint.

***

## 3. Invariants

1. **Gas conservation is preserved.** Any change to mass routing or fragmentation must keep total mass conserved (star + disk + fragments + wind + nebula).
2. **Disk fragmentation still produces only `:gas-giant` embryos and `:protostar` binary companions**, never `:planetesimal` or `:brown-dwarf` directly from disk mass (already enforced by the existing code and by `protoplanetary-disk-and-planet-formation-spec.md`).
3. **Planet seeding remains the only source of `:planet` entities** (per `protoplanetary-disk-and-planet-formation-spec.md`).
4. **The disk must remain visually distinct** after the transparency fix; it should not disappear entirely.
5. **Rendering changes are pure tuning**; they do not alter the physics state or the ECS.

***

## 4. Component changes

No new ECS components are required. The changes are constants and behavior in existing systems.

### 4.1 Modified constants in `src/domain/stellar/disc_evolution.clj`

| Constant | Current | Proposed | Rationale |
|----------|---------|----------|-----------|
| `disk-fragment-threshold` | `0.1` | `0.7` | Literature: fragmentation around a 0.25 M☉ star requires `q ≳ 0.7` (cold) to `≳ 1.4` (irradiated). 0.7 is a conservative compromise that still allows planets to form. |
| `binary-fragment-threshold` | `0.5` | `1.0` | Literature: binary/companion formation requires the disk to approach or exceed the star's mass. |
| `disk-viscous-alpha` | `0.01` | `0.05` | GI-driven transport can reach α ≈ 0.1; 0.05 lets the disk drain faster without vanishing in one tick. |

### 4.2 Modified constants in `src/infra/render/field.clj`

| Constant | Current | Proposed | Rationale |
|----------|---------|----------|-----------|
| `:emission-scale` | `2.2` | `0.8` | Lower intrinsic fog brightness so the disk reads as translucent. |
| `:scatter-scale` | `2.5` | `1.0` | Reduce in-scattering so stars tint the fog without bleaching it. |
| `:kappa` | `0.045` | `0.08` | Slightly higher absorption per density unit makes the fog more transparent. |

### 4.3 Optional shader threshold bump in `src/infra/render/shader.clj`

The volume fragment shader currently skips voxels with `dens <= 0.0008`. Raising this to `0.002`-`0.004` would suppress the faintest, most widespread fog and make the disk appear more structured. This is a visual tuning knob; it should be tested before deciding.

***

## 5. Fragmentation rules

After the change, the disk-evolution pass still computes `c/disk-regime` and applies viscous accretion as before. Fragmentation is gated by:

1. `M_disk / M_star > disk-fragment-threshold` (0.7).
2. The outer disk is in the `:fragmenting` regime (`Q < 1` and `t_cool Ω < 3`, per `protoplanetary-disk-and-planet-formation-spec.md`).
3. The embryo mass is above `opacity-limit-mass` and below `hydrogen-burning-mass` (so it classifies as `:gas-giant`).
4. `c/disk-fragments-spawned` is below the cap (3).

Binary formation is gated by `M_disk / M_star > binary-fragment-threshold` (1.0) and produces a `:protostar` companion.

The fragment mass cap remains `0.5 * law/deuterium-burning-mass` so direct GI fragments always classify as `:gas-giant`.

***

## 6. Viscous accretion rule

The viscous accretion rate remains:

```clojure
mdot-visc = M_disk / t_visc * dt
```

with `t_visc` computed from `disk-viscous-alpha`. The per-tick cap stays at 5% of the disk mass.

With α = 0.05, a 0.25 M☉ star with a 0.175 M☉ disk (the new 0.7 threshold) will drain the disk in roughly 14 ticks instead of 70, assuming the disk is not refilled by infall. Because infall is capped by BHL accretion (`default-accretion-fraction-cap 0.25` per `law.mass-transfer`), the disk can still persist while feeding the star.

***

## 7. Volume rendering rule

The volume config becomes:

```clojure
{:kappa 0.08
 :emission-scale 0.8
 :scatter-scale 1.0
 :jitter 1.0
 :visual-h-scale 10.0
 :visual-h-min 4.0
 :splat-gain 1.0}
```

The shader density threshold is optionally raised from `0.0008` to `0.002`.

These values are starting points; the final tuning should be verified visually.

***

## 8. System responsibilities

### 8.1 `domain.stellar.disc-evolution/disk-evolution-pass`

- Use the new thresholds (`disk-fragment-threshold 0.7`, `binary-fragment-threshold 1.0`).
- Use the new `disk-viscous-alpha 0.05` when computing `disk-viscous-timescale`.
- Keep all existing conservation, regime computation, and fragment caps unchanged.

### 8.2 `domain.mass-transfer`

- No change to gas routing. Gas continues to flow to the disk for `:protostar`/`:star`.
- The faster viscous drain means the disk spends less time near the fragment threshold, so the star grows.

### 8.3 `infra.render.volume`

- Read the new `default-volume-config` values.
- No change to the ray-march algorithm or the camera-relative origin handling.

### 8.4 `infra.render.shader`

- Optional one-line change to the density cutoff in the volume fragment shader.

***

## 9. Tests

1. `disk-fragmentation-threshold-raised` - verify that a disk with `M_disk/M_star = 0.5` does **not** spawn a gas-giant embryo.
2. `disk-fragments-at-new-threshold` - verify that a disk with `M_disk/M_star = 0.8` and `:fragmenting` regime does spawn a `:gas-giant`.
3. `binary-threshold-raised` - verify that a disk with `M_disk/M_star = 0.8` does **not** spawn a `:protostar` companion.
4. `viscous-accretion-faster-with-alpha` - verify that raising `disk-viscous-alpha` from 0.01 to 0.05 reduces the disk mass over a fixed number of ticks.
5. `star-grows-past-quarter-solar` - integration test: run a small cloud for ~200 ticks and assert at least one star exceeds 0.3 M☉.
6. `volume-config-tuning-does-not-break-render` - verify `infra.render.field/default-volume-config` is still a valid map and `infra.render.volume/frame-volume` accepts it.
7. `architecture-test` - run after any structural change.

***

## 10. Promotion path

| File | Change |
|------|--------|
| `src/domain/stellar/disc_evolution.clj` | Update `disk-fragment-threshold`, `binary-fragment-threshold`, and `disk-viscous-alpha`. |
| `src/domain/stellar/classifier.clj` | Set protostar `c/accretion-radius` to 10× old-gas-radius. |
| `src/domain/stellar/disc.clj` | Add `disk-radius-max` and cap `disk-viscous-timescale`. |
| `src/domain/mass_transfer.clj` | Place captured disk gas at 10 AU formation radius. |
| `test/domain/stellar_test.clj` | Update protostar accretion-radius test expectation. |
| `src/infra/render/field.clj` | Update `default-volume-config`. |
| `src/infra/render/shader.clj` | Optional density cutoff bump in the volume fragment shader. |
| `test/domain/disk_evolution_test.clj` | Update disk masses in fragmentation tests from 0.3 M☉ to 0.8 M☉ so they exceed the new 0.7 threshold. |
| `test/infra/render_test.clj` | Add volume config validation test. |
| `kanban/tasks/focus-zoom-lod-ui-spec.md` | Update §2 if the volume tuning is considered part of the close-up rendering pass. |

***

## 11. Decisions

1. **Adopt the literature-grounded fix (Option 1).** We raise the fragmentation thresholds and increase viscous α rather than rerouting H/He gas directly to the star. This preserves the existing disk-as-accretion-reservoir model and requires no new mass budget split.
2. **Fragmentation thresholds are global constants, not functions of stellar mass.** A mass-dependent threshold would be more accurate (higher-mass stars fragment at lower `q`), but the current scalar implementation is kept for simplicity. A future pass can make it mass-dependent if needed.
3. **Volume tuning is a single config change, not a per-body adaptive.** The camera-relative origin makes the disk appear closer, so the global fog opacity is reduced. Per-camera adaptive fog is deferred.
4. **Keep the disk visually present.** The disk should still appear as a flattened, rotating cloud of gas parcels (the current volumetric-fog rendering). It should not vanish because the higher α is capped at 5% per tick.

***

## 12. Open questions

1. **Mass-dependent thresholds:** should `disk-fragment-threshold` decrease with stellar mass (e.g., 1.0 at 0.25 M☉, 0.5 at 1 M☉, 0.3 at 2 M☉)?
2. **Option 2 revisit:** if the star still plateaus or the disk becomes too massive, should we route H/He gas directly to the star and track only the solid/metal budget in the disk?
3. **Volume color temperature:** does the `gas-temp-color` / `disk-temp-color` ramp need separate tuning, or is opacity the main problem?
4. **Fragment cap:** with the higher threshold, will the 3-fragment cap still be reached, or does the disk now drain before spawning multiple embryos?

***

## 14. Post-implementation findings and additional fixes

After the initial changes above were implemented, the live simulation was still observed to plateau at ~0.263 M☉. nREPL inspection revealed two additional bottlenecks not caused by disk fragmentation:

1. **Starved feeding zone:** The protostar's frozen `c/accretion-radius` was set to `1 × old-gas-radius`, leaving a 3 × 10¹⁵ m feeding zone. The nearest remaining gas was at ~3.7 × 10¹⁵ m, so the star captured no new material.
2. **Over-expanded disk:** Gas already captured had j ≈ 10¹⁸ m²/s, giving a disk radius of ~200,000 AU and a viscous timescale of ~10²¹ s, so the disk could not drain onto the star.

Two further changes were made:

### 14.1 Protostar accretion radius ×10

In `src/domain/stellar/classifier.clj`, the protostar branch now sets `c/accretion-radius` to `10 × old-gas-radius` instead of `1 ×`. This gives protostars a larger feeding zone consistent with the main accretors in the simulation.

### 14.2 Compact disk formation radius

In `src/domain/mass_transfer.clj`, gas routed to a protostar/star disk is now placed at a fixed 10 AU formation radius. The added disk angular momentum is scaled to `dm · √(G M · 10 AU)` while preserving the orbital direction. This models the real envelope-collapse process where gas loses angular momentum and lands in a compact disk, instead of preserving the full angular momentum from the Bondi capture radius.

### 14.3 Disk radius cap for viscous drainage

In `src/domain/stellar/disc.clj`, a `disk-radius-max` of 1000 AU is applied inside `disk-viscous-timescale`. This acts as a safety cap so that even if angular momentum is conserved in some capture path, the viscous timescale does not explode.

### 14.4 Result

After these fixes, the live simulation produced a star at **0.293 M☉** (tick 12614) with a compact disk of **0.006 M☉** draining at **4.25 × 10⁻⁷ M☉/tick**. The original 0.269 M☉ cap is gone. Further tuning (100× feeding zone, direct star routing, or different initial nebula parameters) can speed up growth if desired.

## 15. References

1. Kratter, K. M., & Lodato, G. (2016). *Gravitational Instabilities in Circumstellar Disks*. arXiv:1603.01280. https://arxiv.org/abs/1603.01280
2. Mercer, A., & Stamatellos, D. (2020). *Fragmentation favoured in discs around higher mass stars*. arXiv:2001.06224. https://arxiv.org/abs/2001.06224
3. Kratter, K. M., Matzner, C. D., & Krumholz, M. R. (2007). *Embedded, Accreting Disks in Massive Star Formation*. arXiv:0712.0853. https://arxiv.org/abs/0712.0853
4. Project research: `docs/research/physics/protoplanetary-disks-planet-formation.md`
5. Project research: `docs/research/physics/protoplanetary-disks-extended.md`
6. Project research: `docs/research/physics/stellar-nebula-mass-hierarchy.md`
7. Project research: `docs/research/physics/stellar-mergers-accretion.md`
8. Project research: `docs/research/physics/rate-limited-accretion-mass-transfer.md`
9. Project spec: `kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md`
10. Project spec: `kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md`
11. Project spec: `kanban/tasks/gradual-mass-transfer-spec.md`
12. Project notes: `docs/notes/improve-planetary-disk-modeling-and-rendering-claude-session-005-now-let-me-verify-renderclj-still-compil.md`
13. Project notes: `docs/notes/exploration/nrepl-exploration-star-growth-stall.md`


1. Kratter, K. M., & Lodato, G. (2016). *Gravitational Instabilities in Circumstellar Disks*. arXiv:1603.01280. https://arxiv.org/abs/1603.01280
2. Mercer, A., & Stamatellos, D. (2020). *Fragmentation favoured in discs around higher mass stars*. arXiv:2001.06224. https://arxiv.org/abs/2001.06224
3. Kratter, K. M., Matzner, C. D., & Krumholz, M. R. (2007). *Embedded, Accreting Disks in Massive Star Formation*. arXiv:0712.0853. https://arxiv.org/abs/0712.0853
4. Project research: `docs/research/physics/protoplanetary-disks-planet-formation.md`
5. Project research: `docs/research/physics/protoplanetary-disks-extended.md`
6. Project research: `docs/research/physics/stellar-nebula-mass-hierarchy.md`
7. Project research: `docs/research/physics/stellar-mergers-accretion.md`
8. Project research: `docs/research/physics/rate-limited-accretion-mass-transfer.md`
9. Project spec: `kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md`
10. Project spec: `kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md`
11. Project spec: `kanban/tasks/gradual-mass-transfer-spec.md`
12. Project notes: `docs/notes/improve-planetary-disk-modeling-and-rendering-claude-session-005-now-let-me-verify-renderclj-still-compil.md`
