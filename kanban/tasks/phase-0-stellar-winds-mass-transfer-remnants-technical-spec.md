---
uuid: "phase-0-stellar-winds-mass-transfer-remnants-technical-spec"
title: "Phase 0 — Stellar Winds, Mass Transfer & Remnants: Technical Spec"
status: "breakdown"
priority: "P1"
labels: ["specs", "phase0", "em"]
created_at: "2026-07-02T19:35:28.972299950Z"
source: "kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md"
category: "specs"
---

# Phase 0 — Stellar Winds, Mass Transfer & Remnants: Technical Spec

**Status:** ready for implementation (phase A is MVP)
**Date:** 2026-06-27
**Companion docs:** `stage2-sink-formation.md` (gas→sink accretion, the reabsorption
half of this loop), `phase0-jeans-driven-formation.md` (the up-ladder),
`phase0-coupled-physics-and-regime-classifier.md` (matter-state machine).

***

## Goal

Let resolved bodies **shed mass as gas** (stellar wind, flares, and — later —
tidal/Roche stripping), and let that gas be **reabsorbed** by the deepest nearby
gravity well. This makes stars *breathe* — continuously emitting and recapturing
fog — and makes a dominant star emerge **visibly, as gas flow**, instead of
through discrete merges. Mass moves through the medium; nothing "poofs."

It also forces a decision the current model has dodged: **if a body can lose
mass, it can cross a classification threshold downward.** This spec answers that
with a hard invariant (§2) so a shedding star degrades into a *remnant*, never
dribbles back into diffuse gas.

***

## 1. Physics basis

Mass loss is real but spans ~9 orders of magnitude in rate, so it is a **dial**,
not a constant:

| Channel | Real rate | Character | Phase |
|---|---|---|---|
| Coronal/thermal wind (Sun-like) | ~1e-14 M☉/yr | steady, faint | A |
| Radiation-driven wind (O/WR) | ~1e-6 M☉/yr | steady, strong | A |
| Flares / CMEs | ~1e12–1e13 kg, bursty | punctuated, bright | B |
| Roche-lobe overflow (close binary) | ~1e-7–1e-4 M☉/yr | directed stream | C |

Order-of-magnitude wind law (single-scattering radiation limit), good enough to
scale with the body's own state:

```
Ṁ = k_wind · L / (v_esc · c)
v_esc = sqrt(2 G M / R)
```

`k_wind` is the intensity dial. At `k_wind ≈ 1` a Sun-like star loses a
negligible fraction over its life (demotion never happens); cranking it makes
luminous stars drive visible winds and close pairs trade mass.

***

## 2. The load-bearing invariant: collapse is irreversible

> **A body that has gravitationally collapsed into a bound, resolved state
> (`:debris`, `:planet`, `:protostar`, `:brown-dwarf`, `:star`) NEVER returns to
> `:nebula`.** Diffuse gas is only ever *created* by shedding (a wind parcel) —
> never by a bound core re-dissolving.

This is the same principle whose violation caused the disappearing-star bug (a
star volume-summed back to cloud size). Mass loss must respect it. So the feared
chain *star → protostar → gaseous core → nothing* is **disallowed**. Instead,
mass loss walks a body **down a remnant ladder**, and the shed material — not the
core — is what becomes gas:

```
:star        --M < hydrogen-burning-mass-->   :stellar-remnant   (degenerate; was fusing)
:protostar   --M < deuterium-burning-mass-->  :stellar-remnant   (stripped pre-fusion core)
:brown-dwarf --M < deuterium-burning-mass-->  :stellar-remnant
any bound    --M → ~0 (fully ablated)----->   DESPAWN            (all mass transferred; conserved)
any bound    ----------------------------->   NEVER :nebula
```

`:stellar-remnant` is a new matter-state: compact (degenerate density floor),
inert (no contraction, no fusion), faintly luminous and cooling (white-dwarf-
like). It is terminal. Under physical `k_wind` it is essentially never reached;
it exists so the **cinematic** and **binary** regimes degrade *correctly* rather
than re-introducing the resolved→gas bug.

Conservation note: a star's mass goes to **wind parcels + its remnant**. Only
total ablation (Roche overflow stripping a donor to nothing — the real
"black-widow" outcome) despawns the entity, and by then its mass already lives in
the companion and the shed parcels. Mass is conserved at every step.

***

## 3. The wind / mass-loss system (phase A — MVP)

A **serial barrier system** (it spawns and despawns entities and mutates mass, so
it cannot be a parallel single-writer; it runs at the barrier with
`collision-detection`, `fusion-promotion`, `sink-formation`). Tick placement in
`step-physics`:

```
run-parallel → collision-detection → fusion-promotion → sink-formation
            → stellar-wind-system → recenter        ;; ← NEW, before recenter
```

It runs *after* `sink-formation` so a freshly-shed parcel drifts at least one tick
before it can be reabsorbed (no same-tick emit→absorb flicker).

### 3a. Accumulate, then emit discrete parcels

Per-tick emission of micro-parcels would explode entity count. Instead each
shedding body carries a **wind reservoir** (new scalar component
`c/wind-reservoir`, default 0). Each tick, for every body with
`matter-state ∈ {:star :protostar :brown-dwarf}`:

```clojure
Ṁ      = k_wind · L / (v_esc · c)          ; L from star-luminosity / current state
Δm     = min(Ṁ · dt, mass · max-loss-frac) ; per-tick cap for stability
reservoir' = reservoir + Δm
mass'      = mass - Δm
```

When `reservoir' ≥ wind-parcel-mass` (≈ `gas-particle-mass`), **emit one
`:nebula` parcel** of that mass and subtract it from the reservoir. This bounds
new entities to ~1 per body per `wind-parcel-mass` shed.

### 3b. The emitted parcel

Spawned at the body's surface, launched radially outward:

```
position = body-pos + r̂ · radius
velocity = body-vel + r̂ · v_wind            ; SEE CAP BELOW — not v_esc!
mass     = wind-parcel-mass
matter-state = :nebula
composition  = body composition             ; optionally fusion-enriched (He, metals)
temperature  = photosphere (virial at surface)  ; :nebula T is not overwritten → glows, then mixes
b-field, etc = nebula defaults
```

`r̂` is a quasi-random outward unit vector (vary by entity id + tick so it differs
per emission without `Math/random`, which is banned in this codebase).

**Velocity cap (load-bearing numerics).** A physical wind speed (`v_wind ≈ v_esc
≈ 500 km/s`) over a Myr-scale `dt` (~1e11 s) is a displacement of ~3 *nebula
radii per tick* — the parcels ballistically blast the whole system apart (verified:
extent 1.6e16 → 4e18 m, speeds → 5.6e5 m/s in 80 ticks). Realistic wind speed is
simply incompatible with the timestep. So we model the **observable shed envelope**,
not the km/s wind: cap the drift to a fraction of the feeding zone per tick,

```
v_wind = min(v_esc, wind-speed-factor · accretion-radius / dt)
```

with `wind-speed-factor` (default 0.15) = the fraction of the feeding zone the
parcel drifts each tick. The parcel then lingers near the star as visible fog and
stays reabsorbable, instead of escaping. With the cap the system is as stable as
winds-off (speeds ~120 m/s, extent steady) while mass is still shed/reabsorbed.

### 3c. Conservation on emission

- **Momentum:** recoil the body — `v_body' = v_body − (m_p /(M−m_p)) · v_wind · r̂`.
- **Mass:** already subtracted (§3a).
- **Angular momentum:** radial ejection carries ~zero orbital L about the body;
  leave body spin unchanged (good enough for MVP; revisit for fast rotators).

### 3d. Reabsorption is already built

`sink-formation-system` (now generalized to all sinks) absorbs `:nebula` parcels
within a sink's accretion radius. So shed gas drifts and is recaptured by whatever
well is deepest — the donor itself (fallback) or a neighbor. **This is where
competitive accretion emerges**: the largest body has the largest Bondi capture
radius, so it nets the most of everyone's wind plus the natal cloud, and runs
away in mass — a dominant star, grown visibly through the gas, with no special
"competitive accretion" code.

***

## 4. Classifier changes (down-ladder)

`matter-state` stays single-writer (`classifier-system`). Add the downward,
mass-gated transitions to `classify-next-state`, preserving the §2 invariant:

```clojure
:star        (if (>= m hydrogen-burning-mass) :star :stellar-remnant)
:protostar   (cond ... existing up-cases ...
                   (< m deuterium-burning-mass) :stellar-remnant
                   :else :protostar)
:brown-dwarf (if (>= m deuterium-burning-mass) :brown-dwarf :stellar-remnant)
:stellar-remnant :stellar-remnant            ; terminal
```

The barrier wind-system only mutates mass; the classifier picks up the demotion
on the next tick (one-tick lag, negligible for a slow process — same pattern as
`fusion-promotion`). No bound state ever targets `:nebula`.

Despawn-on-ablation: the wind-system (or `sink-formation` when a companion takes
the last of it) despawns any body whose `mass ≤ ablation-floor`.

***

## 5. New matter-state: `:stellar-remnant`

Touch points (mirror how `:brown-dwarf` is wired):
- **structure** (`resolved-shape`): degenerate floor radius (reuse
  `main-sequence-radius` floor or a fixed white-dwarf scale); fixed high density;
  no contraction.
- **temperature**: radiative cooling toward CMB (like `:debris`/`:planet`), not
  virial — a remnant cools, it doesn't re-heat by contraction.
- **luminosity**: small, decaying (cooling-track), or zero.
- **regime / render**: a small, faint, hot-white point; no corona, no wind.
- **collision/accretion**: still a sink (keeps `accretion-radius`); can still
  accrete gas and merge on literal collision.

***

## 6. Phasing

- **Phase A (MVP):** §3 wind reservoir + emission + recoil; §4 down-ladder; §5
  remnant as a minimal reuse of `:debris` rendering if a full remnant state is
  too much for v1. Goal: watch a luminous star drive a visible wind and a
  neighbor net-gain from it; confirm conservation and a dominant star emerging.
- **Phase B (DONE):** flares/CMEs — `stellar-flare-system`. Episodic (every
  `flare-period` ticks per star, default 30), bigger (`flare-mass-factor`×) and
  hotter (`flare-temp-factor`× virial) blobs ejected **bipolar along the spin
  axis**, mass debited directly from the star (conserved). Drift is velocity-
  capped like the wind (`flare-speed-factor`, default 0.4), so flares stay
  stable. A flare never fires if it would pull the star below ½ the H-burning
  mass. NOTE: with the large feeding zone, fresh flares are reabsorbed within
  ~2 ticks (a bright flicker at the star rather than a lasting CME); making them
  escape as durable ejecta is a tuning/Phase-C concern.
- **Phase C:** Roche/tidal stripping — when two resolved bodies are within
  `~k·(r_a+r_b)` but not colliding and the mass ratio is large, strip a parcel
  from the smaller aimed at the larger (a directed stream / accretion bridge).
  This is the dramatic "atmosphere pulled across" picture and the only channel
  that realistically drives full demotion/ablation.

***

## 7. Tunables (`create-world` opts / world keys)

| Key | Meaning | Default (physical) |
|---|---|---|
| `:phase0/wind-rate-scale` (`k_wind`) | global wind intensity | ~1.0 (subtle); raise for cinematic |
| `:phase0/wind-parcel-mass` | shed-gas granularity | ≈ `gas-particle-mass` |
| `:phase0/wind-speed-factor` | `v_wind / v_esc` | ~1.0 |
| `:phase0/wind-max-loss-frac` | per-tick mass-loss cap | ~0.01 |
| `:phase0/ablation-floor` | despawn mass threshold | small (e.g. 1e-3 · deuterium limit) |

***

## 8. Invariants & tests

- **I1 (irreversibility):** no transition produces `:nebula` from a bound state.
  Test: drive a star to total ablation; assert it passes through
  `:stellar-remnant` / despawn and **never** `:nebula`.
- **I2 (conservation):** total mass (bodies + parcels) is conserved across a
  wind emission and across a full shed-then-reabsorb cycle (within fp tol).
- **I3 (no flicker):** a parcel emitted this tick is not reabsorbed this tick.
- **I4 (subtle by default):** at `k_wind = 1`, a 1 M☉ star over the test budget
  loses < 0.1% mass and never demotes.
- **I5 (competitive accretion):** two unequal sinks in a gas bath → the larger's
  mass fraction increases over time (it nets more of the shed + cloud gas).
- **I6 (bug-guard):** a remnant/star never re-expands to gas-scale radius
  (re-assert the merge-bloat guard under wind-driven mass changes).

***

## 9. Open questions

1. Full `:stellar-remnant` state now, or reuse `:debris` for v1 and add the
   white-dwarf cooling track later?
2. Default wind regime — **physical-subtle** (correct; demotion ~never) or
   **cinematic-lively** (stars visibly breathe; demotion possible)?
3. Fusion enrichment of wind composition (He/metals) now or later — cheap and a
   nice touch for downstream chemistry.

---
Triage 2026-07-10 (ready→accepted): PARTIAL: winds/ablation/flares + BHL/Roche mass-transfer shipped+wired+tested; UNBUILT = §2/§4/§5 remnant ladder (:stellar-remnant state, down-ladder mass-loss demotion, irreversibility invariant). Split a focused remnant-ladder residual card.

Triage 2026-07-10: phases A/B shipped; only remnant-ladder (stellar-remnant state, down-ladder demotion) remains. Moved to breakdown to split a focused residual card.

Triage 2026-07-10: residual remnant-ladder work split into child card  (ready, P1, 5pt). Parent stays in breakdown as umbrella until child is done.
---
