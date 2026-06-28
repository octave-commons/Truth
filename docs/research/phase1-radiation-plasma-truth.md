# Phase 1: Radiation and Plasma Architecture for Truth

![Stellar SED comparison](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/5f1c832f.png)

![Condensation sequence](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/61a3be9e.png)

![Streaming instability threshold](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/ba304928.png)

![Impact regimes](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/abb66b48.png)

![Planet formation timeline](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/0b1056f9.png)

![Tectonic regime](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/b79d2d1d.png)

![LOD architecture](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/2fda5e7f.png)

![ECS dependency graph](https://ppl-ai-code-interpreter-files.s3.amazonaws.com/web/direct-files/7287952251b8a4a4a83ea4099f5ca130/06bcd773-d75d-44a7-8711-76e296ff7ce8/e93d2144.png)

## Overview

This report focuses on Phase 1 of the Gates of Truth simulation: stellar radiation and plasma, with implementation-first mappings into the existing Clojure codebase in `domain.stellar`, `domain.em`, and `law.stellar`.[cite:47][cite:48][cite:49] The goal is to replace the current scalar luminosity and neutral gas winds with an observer-centric, panchromatic, plasma-based model that is faithful to modern stellar physics yet resolves details only when they matter for player-visible phenomena.


## 1. Truth Phase 0: Existing Stellar and EM Architecture

### 1.1 Stellar collapse, classification, and winds

`domain.stellar` already implements:
- Jeans instability and promotion of SPH gas parcels to resolved bodies via `jeans-collapse-system`, `classifier-system`, and `accretion-zone-system`.[cite:47]
- Kelvin–Helmholtz contraction of protostars in `collapse-system` and `structure-system`, with density and radius evolution driving virial temperature and ideal-gas pressure via `temperature-system` and `eos-system`.[cite:47]
- Fusion thresholds using `law.stellar/fusion-possible?` and mass-based destiny via `classify-next-state`, `fusion-system`, and `fusion-promotion-system`.[cite:47][cite:49]
- Radiation heating and cooling of debris and planets via `radiation-heating-delta`, `radiative-cooling-delta`, and `temperature-system`.[cite:47]
- Stellar winds and flares modeled as mass loss in neutral `:nebula` parcels in `stellar-wind-system` and `stellar-flare-system`, using a radiation-limit scaling Ṁ ∝ L/(v_esc c) and directional ejection along `wind-direction` and `rotation-axis`.[cite:47]

Key limitations for Phase 1:
- `star-luminosity` returns a bolometric scalar tuned for gameplay rather than physically motivated SEDs by band.[cite:47]
- Wind parcels are neutral gas with temperature set by virial(M,R), lacking ionization state, ram-pressure field, and magnetically constrained flow.[cite:47]

### 1.2 EM layer and magnetic support

`domain.em` provides a per-body MHD-lite substrate:
- Flux freezing under compression, B ∝ ρ^{2/3}, with resistive decay via `flux-freeze` and `resistive-decay`, and a global field system `field-system` that owns `c/b-field` and `c/frozen-flux`.[cite:48]
- Lorentz forces via SPH-like curl estimates `curl-estimate` and `lorentz-acceleration`, feeding `c/accel-lorentz` and `c/hydro-accel`.[cite:48]
- Magnetic braking via `magnetic-braking-torque`, with a sim-time-paced braking fraction that modifies `c/angular-momentum` and `c/spin`.[cite:48]
- Dipole superposition for global fields using `field-sources`, `dipole-field-at`, and `net-field-at`.[cite:48]
- Magnetic support criterion `magnetically-supported?` that compares magnetic pressure P_B to self-gravity.[cite:48]

This EM layer is upstream of stellar physics and can be leveraged to express winds as magnetized plasma outflows whose ram pressure and field topology couple to planetary magnetospheres.


## 2. Panchromatic Stellar SEDs

### 2.1 Observed stellar SEDs across spectral types

The spectral energy distributions (SEDs) of stars differ strongly with effective temperature and surface gravity, even when approximated as blackbodies.[cite:65] O-type stars (O5, T_eff ≈ 40,000 K) peak in the far-UV; solar-type G2 stars (T_eff ≈ 5,800 K) peak in the visible; late M dwarfs (M5, T_eff ≈ 3,000 K) peak in the near-infrared.[cite:65][cite:67] Real SEDs depart from pure blackbodies due to line blanketing, molecular bands, and non-LTE effects in different atmospheric layers.[cite:69]

High-fidelity atmosphere grids such as ATLAS, PHOENIX, and MARCS tabulate emergent flux F_λ(T_eff, log g, [Fe/H]) across the full EM spectrum from X-ray to radio, enabling synthetic photometry and band-integrated luminosities.[cite:16][cite:69] Conroy’s panchromatic SED modeling work shows that stellar populations can be represented by basis SEDs parameterized by T_eff, log g, and metallicity, then integrated through filter curves to obtain band luminosities.[cite:50][cite:54]


### 2.2 Parameterizing SEDs from (T_eff, log g, metallicity, age)

For Gates of Truth, the following reduced parameterization is sufficient:
- T_eff from virial temperature and radius via `virial-temperature` and `structure-system`.[cite:47]
- log g = log10(G M / R²) using `law.stellar/G` and radius from `c/radius`.[cite:49]
- Metallicity Z or [Fe/H] as a composition scalar derived from `c/composition` (e.g., :metals mass fraction).[cite:47][cite:49]
- Age or evolutionary phase inferred from `stellar-system` age and matter-state transitions.[cite:49]

SED basis:
- Use a small grid of pre-tabulated SED templates per spectral type (O, B, A, F, G, K, M, white dwarf), each expressed as fractional band luminosities in a fixed set of broad bands (gamma, X, EUV, FUV, NUV, vis, NIR, MIR, FIR, radio).[cite:65][cite:54]
- Map (T_eff, log g, Z) to nearest template and scale by total bolometric luminosity L_bol from `star-luminosity`, preserving ratios across bands.[cite:47]

Implementation mapping:
- Namespace: `domain.plasma.sed` (new) for pure SED helpers.
- Law schemas: `law.stellar` gains `sed-band-schema` and `sed-profile-schema` to define band lists and per-band luminosities.
- ECS component: `c/sed-bands` in `domain.ecs.components` to store a map like {:gamma L_γ :xray L_X :euv L_EUV :uv L_UV :vis L_vis :nir L_NIR :mir L_MIR :fir L_FIR :radio L_radio}.
- System: `stellar-sed-system` in `domain.stellar` that reads `c/mass`, `c/radius`, `c/composition`, and `c/matter-state`, computes T_eff and log g, selects a template, and writes `c/sed-bands` and a revised `c/luminosity` (L_bol = sum bands).

Proposed Malli schemas (law):
```clojure
(ns law.sed
  (:require [law.contract :as contract]))

(def sed-band-schema
  "Single SED band: name and luminosity in Watts."
  {:band-name keyword?    ;; :gamma :xray :euv :uv :vis :nir :mir :fir :radio
   :luminosity number?})

(def sed-profile-schema
  "Panchromatic SED profile for a star: per-band luminosities and metadata."
  {:id         uuid?
   :star-id    uuid?
   :teff       number?    ;; effective temperature (K)
   :logg       number?    ;; surface gravity log10(cm/s^2) or SI
   :metallicity number?   ;; mass fraction Z
   :bands      (sequential? sed-band-schema)})

(def sed-profile-contract
  (contract/->contract
   {:id       ::sed-profile
    :shape-id ::star-sed
    :kind     :type
    :schema   sed-profile-schema
    :name     "Stellar SED Profile"
    :description "Panchromatic spectral energy distribution for a single star."}))
```

ECS component stub:
```clojure
(defrecord SedBands [bands]
  ;; bands: map from band keyword to luminosty (W)
  )
```

System signature:
```clojure
(defn stellar-sed-system
  "Compute panchromatic SED bands for each star from its physical state and
   write c/sed-bands plus updated bolometric c/luminosity."
  [world]
  ;; implementation to be added
  )
```

Failing test (kaocha, new test namespace `test/domain/stellar_sed_test.clj`):
```clojure
(deftest stellar-sed-system-computes-bands
  (let [world   (-> (ecs/empty-world)
                    (seed-test-star {:mass law.stellar/solar-mass
                                     :radius law.stellar/solar-radius
                                     :composition {:H 0.70 :He 0.28 :metals 0.02}}))
        world'  (stellar-sed-system world)
        eid     (first (ecs/entities-with world' c/matter-state))
        sed     (ecs/get-component world' eid c/sed-bands)
        L-bol   (ecs/get-component world' eid c/luminosity)]
    (is (some? sed) "Star should have SED bands")
    (is (> (reduce + (vals (:bands sed))) 0.0) "Band luminosities positive")
    (is (== (reduce + (vals (:bands sed))) L-bol) "Bolometric luminosity equals sum of bands")))
```


### 2.3 Chart: SED comparison for O5, G2, M5

A blackbody-approximated chart of log10 flux versus wavelength for O5 (40,000 K), G2 (5,800 K), and M5 (3,000 K) stars has been generated as `sed_comparison.png` using Planck’s law to illustrate relative SED shapes.[cite:78] In the full game, this chart underlies band-integrated luminosities rather than direct flux sampling.


## 3. Stellar Atmosphere Structure

### 3.1 Atmospheric layers and state variables

Real stars have stratified atmospheres:[cite:65][cite:74][cite:77]
- Photosphere: optical depth τ ≈ 1, continuum and line formation; T ≈ T_eff; density and pressure sufficient for LTE approximation.[cite:65]
- Chromosphere: above the photosphere with rising temperature, strong emission lines (e.g., Hα, Ca II), and partial ionization.[cite:75][cite:77]
- Transition region: steep temperature gradient from ≈10⁴ K to ≈10⁶ K over small height scales; high ionization and strong EUV emission.[cite:71][cite:74]
- Corona: hot (10⁶–10⁷ K), low-density plasma dominated by magnetic structures and emitting X-rays.[cite:74][cite:76]

Key per-layer state variables:
- Temperature T_layer.
- Electron density n_e.
- Ionization fraction x_i (e.g., H⁺ versus H⁰).
- Magnetic field strength B and topology (open vs closed field lines).[cite:65][cite:71]

Implementation mapping:
- Namespace: `domain.plasma.atmosphere` (new) for pure layered atmosphere state.
- ECS components: `c/atmosphere-shells` storing a vector of layer maps {:layer/id :photosphere|:chromosphere|:transition|:corona :T :n_e :x_i :B :height}.
- Law schema in `law.stellar`: `atmosphere-shell-schema` and `atmosphere-profile-schema` defining allowed keys and ranges.

Schema:
```clojure
(def atmosphere-shell-schema
  {:layer/id     keyword?         ;; :photosphere :chromosphere :transition :corona
   :temperature  number?          ;; K
   :electron-density number?      ;; m^-3
   :ionization-fraction number?   ;; 0..1
   :b-field      vector?          ;; [Bx By Bz] Tesla
   :height       number?})        ;; m above photosphere

(def atmosphere-profile-schema
  {:id        uuid?
   :star-id   uuid?
   :shells    (sequential? atmosphere-shell-schema)})
```

ECS component:
```clojure
(defrecord AtmosphereShells [shells])
```

System stub:
```clojure
(defn atmosphere-shells-system
  "Derive a four-layer atmosphere profile for each star from its SED, surface
   gravity, and magnetic field, storing shells in c/atmosphere-shells."
  [world]
  ;; implementation to be added
  )
```

Failing test:
```clojure
(deftest atmosphere-shells-system-creates-layers
  (let [world  (seed-test-star-with-b-field)
        world' (atmosphere-shells-system world)
        eid    (first (ecs/entities-with world' c/matter-state))
        shells (ecs/get-component world' eid c/atmosphere-shells)]
    (is (= 4 (count (:shells shells))) "Star should have four atmosphere shells")
    (is (every? #(contains? % :temperature) (:shells shells)) "Each shell has temperature")))
```


## 4. Stellar Winds as Ionized Plasma

### 4.1 Parker solar wind and mass loss

Parker’s solar wind model treats the solar wind as a hot, radially expanding, transonic plasma driven by gas pressure gradients and gravity.[cite:51][cite:55] The supersonic outflow solution yields characteristic mass-loss rates Ṁ and velocity profiles v(r) for given coronal temperature T_c and base density n_0.[cite:51][cite:55] Observations show that solar-type stars exhibit mass-loss rates scaling weakly with rotation and activity, while massive O/B stars have radiatively driven winds with higher Ṁ and terminal velocities reaching thousands of km/s.[cite:51][cite:55]

Ram pressure of the wind at a distance r is:
- P_ram = ρ_w(r) v_w(r)², where ρ_w = Ṁ / (4π r² v_w).[cite:51]

This ram pressure, combined with magnetic field topology, defines the Alfvén radius r_A where the wind transitions from magnetically dominated to freely streaming, and sets the stand-off distance for planetary magnetospheres.[cite:59]

### 4.2 XUV-driven atmospheric escape regimes

Close-in planets experience atmospheric escape driven by stellar XUV irradiation (X-ray + EUV):
- Energy-limited escape: mass loss rate proportional to incident XUV power divided by the planet’s gravitational binding energy; appropriate at low XUV flux.[cite:68][cite:60][cite:72]
- Radiation/recombination-limited escape: at high XUV flux, cooling and recombination limit the outflow, decoupling escape rate from incident energy.[cite:70][cite:68]

Recent hydrodynamic escape studies show that these regimes depend on planet gravity, XUV luminosity evolution, and atmospheric composition.[cite:68][cite:72][cite:64]

Implementation linkage:
- `domain.stellar/radiation-heating-delta` already computes temperature rises from bolometric luminosity; Phase 1 extends this to band-specific XUV flux via `c/sed-bands`.[cite:47]
- A new `domain.atmosphere.escape-system` can read planetary `c/climate-state` and `c/atmosphere` and apply energy-limited or recombination-limited escape using XUV band luminosity and distance.[cite:68][cite:72]

### 4.3 Flare activity and space weather

Stellar flares and coronal mass ejections (CMEs) inject transient XUV and particle fluxes:[cite:52][cite:57]
- Flare energy distributions follow power laws, with frequent small events and rare large events that can dominate cumulative XUV doses.[cite:52]
- CMEs carry magnetized plasma clouds that can compress planetary magnetospheres, trigger geomagnetic storms, and enhance atmospheric escape.[cite:59][cite:57]

Truth already has a flare mechanism (`stellar-flare-system`) that ejects massive, hot `:nebula` parcels along spin axes.[cite:47] Phase 1 can reinterpret these as CME analogues with associated transient XUV luminosity boosts added to the SED for a limited duration.


## 5. Implementation: Plasma Winds and SED-Aware Heating

### 5.1 ECS components for winds and SEDs

New components in `domain.ecs.components`:
```clojure
(defrecord WindProfile [base-speed mass-loss-rate alfven-radius]
  ;; base-speed: characteristic wind speed at large radii (m/s)
  ;; mass-loss-rate: Ṁ (kg/s)
  ;; alfven-radius: r_A where magnetic control ends (m)
  )

(defrecord AtmosphereEscape [regime xuv-flux mass-loss-rate]
  ;; regime: :energy-limited or :recombination-limited
  ;; xuv-flux: incident XUV flux at the planet (W/m^2)
  ;; mass-loss-rate: atmospheric escape (kg/s)
  )
```

These components complement `SedBands` and `AtmosphereShells` from earlier sections.

### 5.2 Revised stellar wind system

Instead of spawning neutral gas parcels, winds should launch ionized plasma parcels tagged with a `c/plasma-state` and carry a per-band luminosity contribution for scattered and recombined radiation.

New Malli schema in `law.stellar`:
```clojure
(def plasma-wind-schema
  "Schema for a stellar wind parcel treated as ionized plasma."
  {:id          uuid?
   :origin-star uuid?
   :position    vector?
   :velocity    vector?
   :mass        pos?
   :radius      pos?
   :density     pos?
   :temperature pos?
   :ionization-fraction number?
   :b-field     vector?
   :ram-pressure number?})

(def plasma-wind-contract
  (contract/->contract
   {:id       ::plasma-wind
    :shape-id ::wind-parcel
    :kind     :type
    :schema   plasma-wind-schema
    :name     "Plasma Wind Parcel"
    :description "Ionized plasma parcel in stellar wind carrying ram pressure and magnetic field."}))
```

System signature in `domain.stellar` (replacing `stellar-wind-system`’s neutral parcel spawn with plasma-aware behavior):
```clojure
(defn stellar-wind-system
  "Serial barrier system: stars shed mass as ionized plasma with a Parker-like
   velocity profile and ram-pressure field. Mass loss rate and base speed are
   derived from coronal temperature and SED XUV/EUV bands rather than scalar
   luminosity. Plasma parcels carry ionization state and b-field and couple to
   domain.em for Lorentz forces and magnetospheric compression."
  [world]
  ;; implementation to be added
  )
```

Key differences:
- Use coronal temperature from `AtmosphereShells`’ corona layer as T_c to set wind speed v_∞ and mass-loss rate Ṁ via analytic Parker solutions or calibrated scaling laws.[cite:51][cite:55]
- Compute ram pressure P_ram at parcel launch radius R_* and attach to `plasma-wind-schema`.
- Tag wind parcels with high ionization fraction and the star’s magnetic field vector at the launch point via `domain.em/net-field-at`.[cite:48]

Failing test (`test/domain/stellar_wind_plasma_test.clj`):
```clojure
(deftest stellar-wind-system-produces-plasma-parcels
  (let [world  (seed-test-star-with-atmosphere-and-sed)
        world' (stellar-wind-system world)
        parcels (ecs/entities-with world' c/matter-state c/plasma-state)]
    (is (seq parcels) "At least one plasma wind parcel spawned")
    (let [eid (first parcels)
          ion (ecs/get-component world' eid c/ionization-fraction)
          ram (ecs/get-component world' eid c/ram-pressure)]
      (is (> ion 0.5) "Wind parcel is highly ionized")
      (is (> ram 0.0) "Wind parcel carries positive ram pressure"))))
```

### 5.3 SED-aware planetary heating and escape

Extend `radiation-heating-delta` to include band-dependent effects:
- For climate: use vis + NIR bands for equilibrium temperature and greenhouse forcing.[cite:65]
- For atmospheric escape: use XUV (X-ray + EUV) bands to compute energy-limited or recombination-limited mass loss.[cite:68][cite:72]

New system in `domain.atmosphere`:
```clojure
(defn xuv-atmospheric-escape-system
  "Update planetary AtmosphereEscape component from incident stellar XUV flux
   and planet gravity, choosing between energy-limited and recombination-limited
   escape regimes."
  [world]
  ;; implementation to be added
  )
```

Failing test:
```clojure
(deftest xuv-atmospheric-escape-system-selects-regime
  (let [world   (seed-star-planet-system-with-sed)
        world'  (xuv-atmospheric-escape-system world)
        planet  (first (ecs/entities-with world' c/ocean-state))
        esc     (ecs/get-component world' planet c/atmosphere-escape)]
    (is (some #{:energy-limited :recombination-limited} [(:regime esc)])
        "Escape regime chosen")
    (is (> (:mass-loss-rate esc) 0.0) "Non-zero atmospheric escape computed")))
```


## 6. Space Weather and Magnetosphere Coupling

### 6.1 CME analogues and transient XUV enhancements

Flares in Truth (`stellar-flare-system`) can be tied to transient boosts in XUV band luminosity:[cite:52][cite:57]
- On a flare, compute an XUV enhancement factor f_XUV(t) that decays over a few hours of sim time.[cite:52]
- Add CME parcels with high density and B-field that propagate outward, coupling to planetary magnetospheres via ram pressure and Lorentz forces as computed in `domain.em`.[cite:48][cite:59]

Implementation mapping:
- Extend `stellar-flare-system` to write into `c/sed-bands` for XUV bands during flare ticks.
- Add `c/event-source` for CME parcels in `domain.ecs.components` and a simple `cme-propagation-system` in `domain.plasma` that advances CME positions and attenuates density.

Component stub:
```clojure
(defrecord EventSource [kind payload]
  ;; kind: :flare :cme :grb :supernova etc.
  ;; payload: map of parameters
  )
```

Failing test:
```clojure
(deftest stellar-flare-boosts-xuv-band
  (let [world   (seed-flaring-star-with-sed)
        world'  (stellar-flare-system world)
        eid     (first (ecs/entities-with world' c/matter-state))
        sed     (ecs/get-component world' eid c/sed-bands)]
    (is (> (get (:bands sed) :xuv 0.0) (get (:bands sed) :xuv 0.0))
        "XUV band luminosity increases during flare")))
```


## 7. Observer-Centric LOD for Radiation and Plasma

### 7.1 LOD fidelity layers

Radiation and plasma should be resolved at different fidelities depending on the observer’s context:
- Galaxy-scale: approximate stars as point sources with coarse SED templates; ignore individual CMEs and winds.[cite:50]
- System-scale: resolve band luminosities and steady winds; approximate CMEs as rare events.
- Local body-scale: resolve detailed atmosphere shells, XUV escape, magnetospheric compression, and CME shocks.

The LOD architecture diagram `lod_architecture.png` outlines layers from voxel-scale bodies through regime and system fields up to galaxy and meta-universe scheduler.[cite:81]

Implementation mapping:
- Add LOD tags to components: `c/lod-level` per entity.
- Implement `lod-scheduler` in `domain.phase0` that decides which systems run per entity based on proximity to the player camera and phase.

System stub:
```clojure
(defn lod-scheduler
  "Observer-centric LOD scheduler that enables or disables radiation/plasma
   systems (stellar-sed-system, stellar-wind-system, atmosphere-shells-system,
   xuv-atmospheric-escape-system) based on player focus and zoom level."
  [world]
  ;; implementation to be added
  )
```

Failing test:
```clojure
(deftest lod-scheduler-disables-distant-radiation-detail
  (let [world   (seed-multi-system-world-with-player)
        world'  (lod-scheduler world)
        distant (first (ecs/entities-with world' c/matter-state :lod-level :galaxy))]
    (is (not (lod-runs? world' distant :stellar-sed-system))
        "Distant stars use coarse SED only")))
```


## 8. Crater Regime Diagram Link (Preview)

Although impact physics belongs to a later phase, crater scaling laws depend on impact velocity and mass regimes and will couple back to radiation and plasma via impact-generated hot plumes and transient emission.[cite:11][cite:26][cite:18] The schematic impact regime chart `impact_regimes.png` partitions cratering, disruption, and merging zones in (v, M) space for future use in `collision-system`.[cite:82]


## 9. Cross-Topic Dependencies for Phase 1

### 9.1 Must-have dependencies

- `structure-system` and `temperature-system` must be in place to provide reliable radius and virial temperature for T_eff and atmospheric shell construction.[cite:47]
- `field-system` and dipole sources must be stable to support flux freezing and magnetically coupled winds.[cite:48]
- `fusion-system` and `fusion-promotion-system` must correctly classify stars versus brown dwarfs and debris to ensure proper wind activation thresholds.[cite:47][cite:49]

### 9.2 Recommended implementation order

1. **SED bands and atmosphere shells**: implement `SedBands`, `stellar-sed-system`, and `AtmosphereShells` so stars have panchromatic luminosities and layered atmospheres.
2. **Plasma winds and CME refactor**: replace neutral `stellar-wind-system` parcel spawn with `plasma-wind-schema` and tie `stellar-flare-system` into XUV band boosts.
3. **XUV escape and planetary coupling**: implement `xuv-atmospheric-escape-system` downstream of band SEDs and winds.
4. **LOD scheduler hooks**: integrate radiation/plasma systems into an observer-centric LOD pipeline.


## 10. Implementation Roadmap (Phase 1)

### 10.1 New Malli schemas (src/law/)

- `law.sed/sed-band-schema`, `law.sed/sed-profile-schema`, contracts for SED profiles.
- `law.stellar/plasma-wind-schema` and `plasma-wind-contract`.
- `law.stellar/atmosphere-shell-schema` and `atmosphere-profile-schema`.

### 10.2 New ECS components (src/domain/)

- `SedBands` with `:bands` map.
- `AtmosphereShells` with `:shells` vector.
- `WindProfile` with base-speed, mass-loss-rate, alfven-radius.
- `AtmosphereEscape` for planetary escape.
- `EventSource` for flares/CMEs.

### 10.3 New systems (src/domain/)

- `stellar-sed-system` in `domain.stellar`.
- `atmosphere-shells-system` in `domain.plasma.atmosphere`.
- Refactored `stellar-wind-system` in `domain.stellar` using plasma schema.
- `xuv-atmospheric-escape-system` in `domain.atmosphere`.
- `lod-scheduler` in `domain.phase0`.

### 10.4 Existing namespaces to modify

- `src/domain/stellar.clj`: replace `star-luminosity`, extend `stellar-wind-system`, integrate `stellar-sed-system` with `fusion-system`.[cite:47]
- `src/domain/em.clj`: ensure `field-system` and `lorentz-acceleration-system` expose helpers for wind plasma coupling.[cite:48]
- `src/law/stellar.clj`: host new schemas for atmosphere shells and plasma winds.[cite:49]

### 10.5 Failing tests to write first

- `stellar-sed-system-computes-bands` (SED bands sum to L_bol).
- `atmosphere-shells-system-creates-layers` (four shells with temperatures).
- `stellar-wind-system-produces-plasma-parcels` (ionized, ram-pressure carrying parcels).
- `xuv-atmospheric-escape-system-selects-regime` (escape regimes assigned).
- `stellar-flare-boosts-xuv-band` (transient XUV boost from flares).
- `lod-scheduler-disables-distant-radiation-detail` (coarse LOD for distant stars).


## 11. Pitfalls and Anti-Patterns for Phase 1

### 11.1 Neutral cold gas winds

Modeling stellar winds as neutral cold gas parcels ignores ionization, coronal heating, and ram pressure, underestimating their impact on planetary magnetospheres and atmospheric escape.[cite:51][cite:59] Truth’s current `stellar-wind-system` partially mitigates this by using virial temperature for parcel temperature, but the matter-state and field coupling remain neutral.[cite:47] Phase 1 must treat winds as ionized plasma tied to coronal temperature and magnetic topology.

### 11.2 Scalar brightness instead of SED

Using a single scalar `c/luminosity` obscures critical band-dependent effects: XUV drives atmospheric escape, FUV/NUV affect photochemistry, and IR regulates climate.[cite:65][cite:50] Truth’s `radiation-equilibrium-temperature` uses bolometric flux and an assumed albedo, which is adequate for rough climate but not for escape regimes.[cite:47] A panchromatic SED via `c/sed-bands` avoids this pitfall.

### 11.3 Ignoring atmosphere layering

Treating stars as single-temperature spheres ignores chromospheric and coronal XUV sources, leading to incorrect escape and flare modeling.[cite:71][cite:74] The four-layer atmosphere model ensures that XUV originates from hot, low-density coronal layers rather than the photosphere.


## 12. Minimum Viable Physically Grounded Phase 1

A minimal but scientifically plausible implementation of Phase 1 should include:
- Blackbody-based SED bands per star, scaled by `star-luminosity` and parameterized by T_eff and log g.[cite:65][cite:50]
- Four-layer atmosphere shells per star with approximate temperatures and densities tied to T_eff and activity level.[cite:71][cite:74]
- Parker-like plasma winds with mass loss and ram pressure scaling from coronal temperature and bolometric luminosity.[cite:51][cite:55]
- Simple energy-limited XUV escape model for close-in planets using XUV band luminosity and planetary gravity.[cite:68][cite:72]

These features can be resolved at high fidelity only near the player’s focus, with coarse SEDs and winds elsewhere, preserving performance while retaining physical plausibility.
