---
uuid: "nrepl-exploration-star-growth-stall"
title: "nREPL Exploration: Star Growth Stall at 0.263 M☉"
created_at: "2026-07-09T18:30:00.000000000Z"
category: "exploration"
status: "ready"
---

# nREPL Exploration: Star Growth Stall at 0.263 M☉

**Date:** 2026-07-09  
**Method:** Connected to live `gates-of-truth-dev` nREPL on 127.0.0.1:7888, queried ECS state, rendered offscreen frames, and navigated the camera.  
**Goal:** Understand why the star is still capped at ~0.263 M☉ after the disk-fragmentation threshold fix.

## Observations

### World state at tick ~14935
- One star, eid 890, mass **0.2629 M☉**.
- Disk mass **0.0424 M☉**, disk-to-star ratio **0.161** — well below the new 0.7 fragmentation threshold.
- Disk regime: `:core-accretion-zone` (Toomre Q ≈ 48, not fragmenting).
- Mass flux from disk to star: **6.5 × 10⁻¹² M☉/tick** — effectively zero.
- Total gas remaining in nebula: **1.087 M☉** spread over 907 parcels.
- Star effective accretion radius (`c/accretion-rate` `:sink/r-acc`): **3.0 × 10¹⁵ m**.
- Bondi radius: **1.12 × 10¹⁵ m**.
- Ambient density within `r-acc`: **0.0 kg/m³** — the star's feeding zone is empty.
- Nearest usable gas parcel: **3.68 × 10¹⁵ m** away, mass 4 × 10²⁷ kg (0.002 M☉), outside the feeding zone.

### World state at tick ~23922
- Star mass unchanged: **0.2629 M☉**.
- Disk mass: **0.046 M☉**, ratio **0.175**.
- Total gas remaining: **0.873 M☉**.
- Accretion rate still zero; no gas within r_acc.

### Disk geometry
- Disk angular momentum vector ≈ [1.19e46, 1.22e46, 9.26e45] kg m²/s.
- Specific angular momentum j ≈ 1.014 × 10¹⁸ m²/s.
- Disk radius r_disk = j² / (GM) ≈ **2.95 × 10¹⁶ m** ≈ **197,000 AU**.
- This is larger than the nebula radius (2 × 10¹⁶ m).
- Viscous timescale for this disk ≈ 10²¹ s (many Gyr) — the disk never drains onto the star.

### Visuals
- Overview at tick 23202: `docs/notes/exploration/gates_of_truth_overview_tick_23202.png`
  - Phase: "Planets formed", later "life-emergence".
  - Star visible as bright central body with disk halo.
  - Nebula/disk fog is now translucent after the volume tuning (emission 0.8, scatter 1.0, kappa 0.08).
- Camera-follow of the star at tick 35365: `docs/notes/exploration/gates_of_truth_star_follow_tick_35365.png`
  - Star appears as a small bright dot at distance ~1.0 ru.
  - Nebula fog surrounds it.
- Context view at tick 32372: `docs/notes/exploration/gates_of_truth_star_context_tick_32372.png`

## Diagnosis

The disk-fragmentation fix (threshold 0.1 → 0.7) was correct, but **fragmentation is no longer the bottleneck**. The star is stalled because of two coupled issues:

1. **Starved accretion zone.** The protostar's frozen accretion radius was set at formation to its gas-parcel radius (≈ 3 × 10¹⁵ m). The Bondi radius for a 0.26 M☉ star is only ~10¹⁵ m. All nearby gas has been depleted; the nearest remaining gas is at ~3.7 × 10¹⁵ m, just outside the feeding zone. No new gas reaches the disk, so the disk cannot grow and the star cannot accrete.

2. **Over-expanded disk.** The small amount of gas already captured formed a disk with enormous specific angular momentum (j ≈ 10¹⁸ m²/s), giving it a centrifugal radius of ~200,000 AU. The viscous timescale is then ~10²¹ s, so the disk drains at 10⁻¹² M☉/tick — negligible. Even if gas were available, the disk cannot feed the star efficiently.

The net effect is a star that consumes its local reservoir and then plateaus at ~0.25–0.27 M☉ while 0.5–1 M☉ of gas drifts just outside its reach.

## Candidate fixes

1. **Increase the protostar feeding zone.** In `src/domain/stellar/classifier.clj`, the protostar branch sets `c/accretion-radius` to `old-gas-radius` (1×), while non-protostar bodies get `100 × old-gas-radius`. Raising the protostar multiplier (e.g., 10× or 100×) would let the star capture distant gas.

2. **Cap the disk radius used for viscous drainage.** The disk radius derived from angular momentum (j²/GM) can exceed the nebula size. Capping `r_disk` in `disk-viscous-timescale` (or in `disk-evolution-system`) to a physically plausible maximum (e.g., 1000 AU) would shorten t_visc to ~10 Myr and make the disk drain onto the star.

3. **Route a fraction of captured gas directly to the star.** The `domain.mass-transfer` `donor-flux` helper currently sends all gas to the disk for `:protostar`/`:star`. Splitting captured gas (e.g., 50% to star via `c/mass-flux-transfer`, 50% to disk via `c/disk-mass-flux`) would bypass the disk bottleneck and allow rapid stellar growth while still building a disk.

4. **Reduce the angular momentum of captured disk gas.** In `mass-transfer`, the captured gas is placed at its actual orbital radius. In reality, gas falls through an envelope and lands at the centrifugal radius, which is much smaller for gas captured from large distances. Scaling the added angular momentum (or adding a fixed disk radius) would keep the disk compact and fast-draining.

## Fixes implemented during this session

Implemented three changes to address the two bottlenecks above:

1. **Protostar accretion radius ×10.** In `src/domain/stellar/classifier.clj`, the protostar branch now sets `c/accretion-radius` to `10 × old-gas-radius` instead of `1 ×`. This makes the protostar's frozen feeding zone larger and lets it capture more nebula gas.

2. **Disk radius cap for viscous drainage.** In `src/domain/stellar/disc.clj`, added `disk-radius-max` (1000 AU) and applied it inside `disk-viscous-timescale`. This prevents the huge angular-momentum-derived disk radius from producing a multi-Gyr viscous timescale.

3. **Compact disk-formation radius for captured gas.** In `src/domain/mass_transfer.clj`, gas routed to a protostar/star disk is now placed at a fixed 10 AU formation radius, rather than preserving the full angular momentum from the capture radius. This models the physical envelope collapse: gas loses angular momentum and lands in a compact disk. The added disk angular momentum is scaled to `dm · √(G M · 10 AU)` while preserving the orbital direction.

Updated `test/domain/stellar_test.clj` to expect the new 10× protostar accretion radius.

## Observations after fixes (tick 12614)

- Star mass: **0.293 M☉** (already above the 0.269 plateau).
- Disk mass: **0.006 M☉**, disk-to-star ratio **0.021** — far below the 0.7 fragmentation threshold.
- Disk radius: **2.6 AU** (compact).
- Viscous timescale: **1.55 Myr** (drainable).
- Disk-to-star mass flux: **4.25 × 10⁻⁷ M☉/tick** — the disk is now feeding the star.
- The star is still growing slowly; it formed at ~0.29 M☉ and is accreting from its compact disk.

### Visuals after fixes
- Overview after fixes: `docs/notes/exploration/gates_of_truth_after_fix_tick_12614.png`
  - The volume fog is now translucent (volume tuning + compact disk).
  - Star is visible with a small disk halo.
  - Multiple resolved bodies are visible in the cluster.

## Conclusion

The original 0.269 M☉ cap is **gone**. The star now forms and grows past 0.29 M☉. Growth is gradual because the remaining nebula gas is diffuse and the Bondi-Hoyle rate is low, but the disk is compact and draining, so the star continues to accrete. If faster growth is desired, the next levers are:
- Increase the protostar radius multiplier further (e.g., 100×).
- Route a fraction of captured gas directly to the star instead of through the disk.
- Increase the nebula gas-particle count or reduce nebula radius to keep gas denser near the forming star.

## Commands used

```clojure
(require '[infra.dev.window :as w])
(require '[domain.ecs.core :as ecs])
(require '[domain.ecs.components :as c])
(require '[infra.camera :as cam])
(require '[infra.render.window :as rw])

;; Star state
(let [ws @(:world @w/service-state)
      star (first (filter #(= :star (ecs/get-component ws % c/matter-state)) (ecs/all-entities ws)))]
  {:mass (ecs/get-component ws star c/mass)
   :disk-m (or (ecs/get-component ws star c/disk-mass) 0.0)
   :rate (ecs/get-component ws star c/accretion-rate)})

;; Render current view
(rw/render-to-file (:world @w/service-state) "/tmp/gates_render.png")

;; Camera follow star
(let [ws @(:world @w/service-state)
      star (first (filter #(= :star (ecs/get-component ws % c/matter-state)) (ecs/all-entities ws)))
      settings (assoc (cam/default-camera-settings)
                      :mode :follow-selection
                      :follow-eid star
                      :distance 1.0
                      :smoothing 1.0)
      camera (cam/update-camera-for-world (cam/make-camera 1.0) ws settings)]
  (rw/render-to-file (:world @w/service-state) "/tmp/gates_star_follow.png" {:camera camera}))
```

## References
- `src/domain/stellar/classifier.clj` — accretion radius assignment
- `src/domain/mass_transfer.clj` — gas routing to disk
- `src/domain/stellar/disc.clj` — disk radius and viscous timescale
- `src/domain/stellar/disc_evolution.clj` — disk evolution and fragmentation logic
- `kanban/tasks/star-growth-disk-fragmentation-nebula-transparency-spec.md` — previous design doc
