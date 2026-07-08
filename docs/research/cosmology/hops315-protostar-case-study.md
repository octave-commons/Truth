# HOPS-315: A Protostar Case Study for Planet Formation in Gates of Truth

**Domain:** cosmology / planet formation | **Phase:** 0 → 1 (nebula collapse → disk → planet seeding)  
**Date:** 2026-07-07 | **Author:** truth-research-cosmology actor  
**Status:** draft  
**Primary source:** McClure et al. (2025), *Nature* 643, 649–653, DOI:10.1038/s41586-025-09163-z

---

## 1. Research Question

Phase 0 of Gates of Truth collapses a molecular cloud into a central star plus a rotationally supported protoplanetary disk. The disk is the nursery for planets, but the transition from hot gas to refractory solids to planetesimals spans ~12 orders of magnitude in mass and multiple unresolved physical channels. The HOPS-315 observations provide the first empirical snapshot of “time zero” for rocky planet formation: a young sun-like protostar whose inner disk is actively condensing the silicate minerals that become terrestrial planets.

This notebook asks:

1. What are the observed system parameters of HOPS-315 and how do they constrain the inner disk thermochemistry?
2. How does the gas-to-solids transition observed in HOPS-315 map onto Truth’s gas ladder and condensation sequence?
3. What is the radial/temperature structure of the HOPS-315 disk, and how does it map onto radial bands in Truth’s geodesic grid?
4. How can the HOPS-315 case study drive the design of Truth’s finite-state-machine (FSM) stack for formation, environment, and Phase 0 → Phase 1 handoff?

This notebook is a companion to `docs/research/physics/protoplanetary-disks-planet-formation.md`, which grounds the core-accretion, gravitational-instability, and streaming-instability channels in the literature. Here we use HOPS-315 as an observational anchor for the *earliest* solid-condensation step that precedes those channels.

---

## 2. Observational Summary of HOPS-315

### 2.1 System identification and age

HOPS-315 is a low-mass protostar in the Orion B / LDN 1641 star-forming region, also known as the Orion Nebula South field. JWST+MIRI spectroscopy and ALMA Band 7 imaging were obtained in 2023, and the discovery paper was published in *Nature* on 16 July 2025 (McClure et al. 2025).

| Parameter | Value | Source |
| :-- | :-- | :-- |
| Distance | ~1,300–1,400 light-years (~400 pc) | McClure et al. (2025); ESO press release |
| Age | ~0.1–0.2 Myr (100,000–200,000 yr) | McClure et al. (2025); ScienceNews |
| Final expected mass | ~solar mass ($M_* \sim M_\odot$) | Sky & Telescope; McClure et al. (2025) |
| Disk outer radius | ~35 AU (from ALMA imagery) | Sky & Telescope |
| Disk mass | comparable to solar nebula at similar age | McClure et al. (2025) |
| Disk inclination | favorable gap through envelope/jets | McClure et al. (2025); ESO press release |

The young age makes HOPS-315 a Class I/flat-spectrum protostar: it still retains a substantial envelope and is actively accreting.

### 2.2 Detected chemistry and spatial zones

The combined JWST (infrared) and ALMA (millimeter) observations resolve two distinct silicon-monoxide reservoirs and a crystalline silicate population in the inner disk.

| Zone | Radius | Temperature | Material | Interpretation |
| :-- | :-- | :-- | :-- | :-- |
| CAI / high-T refractory zone | $\lesssim 1$ AU | $T \gtrsim 1200$ K (midplane) | Crystalline silicates, forsterite precursor conditions | High-T condensation analogous to calcium-aluminum-rich inclusions (CAIs) in the Solar System |
| Warm SiO disk atmosphere | within $\sim 2.2$ AU | $\sim 200$ °C ($\sim 470$ K) | Gaseous SiO | Cooled gas from which silicates have condensed or are condensing |
| Millimeter SiO jet | collimated outflow, much larger scale | shock-heated | SiO in jet, $\sim 10\times$ faster than disk SiO | Outflow, physically isolated from the inner disk reservoir |

The key observational result is that the warm SiO gas in the disk atmosphere is **physically isolated** from the faster SiO jet, and the SiO velocity in the disk is an order of magnitude lower than the jet velocity. This proves the disk reservoir is local and bound, not outflow material (McClure et al. 2025).

### 2.3 Condensation sequence and mineralogy

The detected crystalline silicates include **forsterite** (magnesium-rich olivine), which condenses from a hot, cooling gas at equilibrium temperatures of roughly **600–1000 °C** (873–1273 K). The 200 °C SiO gas is interpreted as having cooled from the higher temperatures at which the crystalline silicates form. This sequence is directly analogous to the condensation sequence inferred for the Solar Nebula (Lodders 2003), where refractory minerals condense first and then feed the dust that builds planetesimals.

The authors compare the HOPS-315 detections to models of rapid grain growth and disk structure, concluding that the refractory solids are forming on timescales comparable to those inferred for the Solar System (McClure et al. 2025). The silicon depletion in the jet (~98% of expected Si relative to C is missing) is also consistent with Si being locked into solids that are hidden in the optically thick disk midplane or already incorporated into larger bodies.

### 2.4 Citation

McClure, M. K., van’t Hoff, M., Francis, L., Bergin, E., Rocha, W. R. M., Sturm, J. A., Harsono, D., van Dishoeck, E. F., Black, J. H., Noble, J. A., Qasim, D., & Dartois, E. (2025). “Refractory solid condensation detected in an embedded protoplanetary disk.” *Nature*, 643, 649–653. DOI:10.1038/s41586-025-09163-z

---

## 3. Physical Scales and the Gas Ladder

### 3.1 From nebula to star + disk: the gas ladder

HOPS-315 sits at the transition between the large-scale gas ladder and the disk/solids ladder. The gas ladder, derived from the Truth formation notes, is:

| State | Physical meaning | Entry condition | Exit condition |
| :-- | :-- | :-- | :-- |
| `:matter/nebula` | Diffuse molecular cloud / SPH parcels | Default diffuse state | Jeans-unstable region |
| `:matter/collapsing-gas` | Self-gravitating convergence | Jeans-like instability | Core condensation density |
| `:matter/condensed-core` | Bound non-diffuse core | Core density reached | Mass tier sets fate |
| `:matter/protostar` | Core above H-burning limit, not yet stable | Mass $\gtrsim 0.08 M_\odot$ | Fusion becomes self-sustaining |
| `:matter/star` | Stable hydrogen burning | Fusion self-sustaining | Fusion exhausted |
| `:matter/stellar-remnant` | Post-fusion bound object | Fusion ends | Terminal/cooling |

HOPS-315 is currently in the `:matter/protostar` state: a collapsed core above the hydrogen-burning mass threshold that is still accreting from its envelope and disk. The system’s disk is in the `:matter/dust-field` and `:matter/pebble-field` states in the inner regions, and is evolving toward the solids ladder described below.

### 3.2 Governing equation: Jeans instability and core formation

The collapse step is triggered when self-gravity overcomes pressure and turbulence support. For an isothermal uniform cloud of density $\rho$ and sound speed $c_s$, the Jeans mass is

$$
M_J = \frac{\pi^{5/2} c_s^3}{6 \sqrt{G^3 \rho}}.
$$

A parcel becomes unstable when its mass exceeds $M_J$ (or, equivalently, when its radius exceeds the Jeans length $\lambda_J = c_s \sqrt{\pi / (G \rho)}$). In Truth this is handled by the existing collapse logic; HOPS-315 is the empirical endpoint of a successful collapse that has already formed a protostellar core and a surrounding disk.

### 3.3 Accretion and disk assembly

The protostar is still accreting. The observed CO wind is butterfly-shaped and the SiO jet is collimated, both signatures of active accretion and disk winds. The disk mass is said to be comparable to the Solar Nebula at the same age, implying a disk-to-star mass ratio of order $10^{-2}$ to $10^{-1}$. For comparison, Andrews & Williams (2007) find a median disk-to-star mass ratio of a few $\times 10^{-3}$ in Ophiuchus/Taurus, with a tail reaching $\sim 0.1$. HOPS-315 therefore sits in the upper part of the observed disk-mass distribution, consistent with its youth and ongoing envelope infall.

---

## 4. Disk Structure: Radial and Thermal Zoning

### 4.1 The observed HOPS-315 disk zones

The HOPS-315 disk shows three radial/thermal zones that are directly relevant to the condensation sequence and planet-formation zoning:

```
    HOPS-315 protostar (~0.1–0.2 Myr, ~M☉ final mass)
    │
    ├─ r ≲ 1 AU : T > ~1200 K (midplane) → CAI / refractory zone
    │              forsterite and crystalline silicates condensing
    │
    ├─ r ≲ 2.2 AU : T ~ 470 K (atmosphere) → warm SiO gas reservoir
    │                physically isolated from jet; precursor to silicate dust
    │
    └─ ~2.2 AU → ~35 AU : cooler disk midplane and outer disk
                         water-ice snow line likely lies in this range
                         (T ≈ 150–170 K at ~2–3 AU for solar luminosity)
```

The inner disk is hot enough to vaporize silicates; the disk atmosphere at ~2.2 AU is cool enough that SiO gas is stable but solid forsterite is already condensing closer in. This is an empirical template for the **temperature-dependent phase transitions** that Truth’s condensation module must encode.

### 4.2 Snow line estimate for a solar-luminosity protostar

For a blackbody disk in radiative equilibrium with a star of luminosity $L_*$, the equilibrium temperature is

$$
T_{\rm eq}(r) = \left( \frac{(1-A) L_*}{16 \pi \sigma_{\rm SB} r^2} \right)^{1/4},
$$

where $A$ is the albedo. Setting $T_{\rm eq} = T_{\rm snow}$ gives the water-ice snow-line radius

$$
r_{\rm snow} = \sqrt{\frac{(1-A) L_*}{16 \pi \sigma_{\rm SB} T_{\rm snow}^4}}.
$$

For $L_* \sim L_\odot$, $A \sim 0.5$, and $T_{\rm snow} \sim 170$ K, this gives $r_{\rm snow} \sim 2$–$3$ AU. HOPS-315 is not yet at solar luminosity, but its final mass is solar, so the snow line in the mature system is expected to lie near this radius. During the embedded protostellar phase, the luminosity is higher (accretion luminosity), so the snow line may be pushed outward temporarily.

### 4.3 Solid surface density jump

The solid surface density increases by a factor of $f_{\rm ice} \sim 3$–$5$ beyond the snow line because water ice becomes available. This jump is the same snow-line physics discussed in `protoplanetary-disks-planet-formation.md` and is written as

$$
\Sigma_{\rm solid}(r) = Z \, \Sigma_{\rm gas}(r) \times
\begin{cases}
1, & r < r_{\rm snow}, \\
f_{\rm ice}, & r \ge r_{\rm snow},
\end{cases}
$$

with $Z \approx 0.015$ the protosolar metallicity. In HOPS-315, the inner disk is currently producing *refractory* solids (silicates, forsterite) at radii well inside the eventual snow line. This is the first step of the gas ladder: hot gas → refractory dust → pebbles → planetesimals.

### 4.4 Disk stability and fragmentation channels

Because the disk mass is comparable to the solar nebula, the outer disk may be massive enough to approach gravitational instability. The Toomre parameter is

$$
Q = \frac{c_s \Omega_K}{\pi G \Sigma_{\rm gas}},
$$

where $\Omega_K = \sqrt{G M_* / r^3}$ is the Keplerian angular frequency. For HOPS-315, the inner disk is hot and stable ($Q \gg 1$), while the outer disk may approach $Q \sim 1$ if the mass is high enough. The fragmentation outcome depends on the cooling-time ratio $\beta = t_{\rm cool} \Omega_K$ (Gammie 2001): fragmentation requires both $Q \lesssim 1$ and $\beta \lesssim 3$.

This is the same diagnostic used in `protoplanetary-disks-planet-formation.md`. The HOPS-315 case does not directly test the outer-disk fragmentation threshold, but it provides the inner-disk boundary condition that must be matched by any complete disk model.

---

## 5. From HOPS-315 to Truth’s FSM Stack

### 5.1 Why a stack of FSMs?

The Truth architecture avoids a single giant “planet state” label. Instead, each entity carries a stack of orthogonal state machines, each owned by one domain system and answering one question. The HOPS-315 observations motivate the earliest transitions in this stack, especially the matter FSM for solids and the environment/atmosphere FSMs for the inner disk thermochemistry.

The five coupled FSMs are:

| FSM | Owns | Question | Example states for HOPS-315 |
| :-- | :-- | :-- | :-- |
| **Matter** | `domain.stellar` + `domain.planet-formation` | What physically is this thing? | `:matter/protostar`, `:matter/dust-field`, `:matter/pebble-field`, `:matter/planetesimal` |
| **Role** | `domain.orbital` | What is its dynamical relationship? | `:role/disk-embedded` (inner disk solids), `:role/free-body` (planetesimal seeds) |
| **Environment** | `domain.environment` | What regime is its surface/interior in? | `:env/magma-ocean` (hot inner disk), `:env/crusted-volcanic` (condensed solids) |
| **Atmosphere / EM** | `domain.atmosphere` + `domain.em` | Can it hold/protect an atmosphere? | `:atm/transient-outgassed` (disk atmosphere), `:atm/none` (solids) |
| **Biosphere** | `domain.biology` | What level of life exists? | `:bio/none` at HOPS-315 age |

### 5.2 HOPS-315 as a state-transition map

The HOPS-315 observations give us empirical guards for the earliest matter transitions:

| Observed feature | Truth state transition | Guard | Owner |
| :-- | :-- | :-- | :-- |
| Hot inner disk midplane $T > 1200$ K | `:matter/nebula` → `:matter/dust-field` (vapor phase) | $T > T_{\rm silicate\,vaporize}$ | `domain.planet-formation` |
| Forsterite condensing at 600–1000 °C | `:matter/dust-field` → `:matter/pebble-field` | $T_{\rm condense} < T < T_{\rm vaporize}$ and cooling time short enough | `domain.planet-formation` |
| Warm SiO gas at ~200 °C within 2.2 AU | `:matter/dust-field` / `:matter/pebble-field` coexistence | $T \sim 470$ K, SiO gas still present | `domain.planet-formation` |
| Silicate depletion in jet | `:matter/pebble-field` → planetesimal seeds (hidden) | Dust-to-gas enhanced; solids decoupled from gas | `domain.planet-formation` / `domain.orbital` |

### 5.3 The solids ladder: dust → pebbles → planetesimals

The HOPS-315 observations confirm the first step of the solids ladder. The full ladder used in Truth is:

| State | Entry guard | Exit guard | Notes |
| :-- | :-- | :-- | :-- |
| `:matter/dust-field` | Solids present as sub-resolved grains | Grain growth/coagulation or vaporization | HOPS-315: silicates condensing |
| `:matter/pebble-field` | Stokes number $\tau_s \sim 0.01$–$0.1$, aerodynamic drift active | Streaming instability or loss/accretion | HOPS-315: inferred growth to fingernail-sized clusters |
| `:matter/planetesimal` | Bound 100–1000 km body | Collisional growth or disruption | HOPS-315: hinted by Si depletion |
| `:matter/protoplanet` | Growing body dominating local collisions | Orbit clearing / satellite capture / gas accretion | — |
| `:matter/planet` / `:matter/dwarf-planet` | Round; dynamical role sets final label | Disruption or engulfment | — |

The transition from `:matter/pebble-field` to `:matter/planetesimal` is the streaming-instability step. It requires the local dust-to-gas ratio to exceed unity and the Stokes number to be $\tau_s \sim 0.1$–$1$ (Youdin & Goodman 2005; Johansen et al. 2007, 2014). This is detailed in `protoplanetary-disks-planet-formation.md`, which gives the mass scale of streaming-instability planetesimals as $10^{18}$–$10^{21}$ kg (radii 100–500 km).

### 5.4 Environment FSM for the hot inner disk

Although the inner disk is not a solid surface, the environment FSM can be applied to the *condensed solids* as a proxy for their thermal regime:

| State | Guard | HOPS-315 mapping |
| :-- | :-- | :-- |
| `:env/magma-ocean` | Global/near-global melt | Inner disk midplane $T > 1200$ K: silicates are vaporized or molten |
| `:env/crusted-volcanic` | Stable crust but active interior | Forsterite condensing at 600–1000 °C: first solid “crust” forming |
| `:env/impact-reset` | Repeated bombardment reheating | Early planetesimal collisions re-melt or reset solids |
| `:env/ocean-world` | Persistent liquid inventory | Not applicable until volatiles are delivered and retained |

The HOPS-315 case study therefore gives us the **high-temperature anchor** for the environment FSM: before a rocky world can become habitable, it must pass through magma-ocean and crusted-volcanic states, just as the inner disk must pass through vapor → refractory solid → pebble stages.

---

## 6. Implementation Sketch (Clojure Pseudocode)

The following pseudocode maps the HOPS-315 observations onto Truth’s ECS patterns. It is consistent with the existing `protoplanetary-disks-planet-formation.md` notebook and the handoff spec.

### 6.1 Condensation thresholds as pure functions

```clojure
(ns law.hops315
  "Condensation thresholds derived from HOPS-315 and Lodders (2003).")

(def silicate-vaporization-temperature 1500.0) ; K, approximate
(def forsterite-condensation-low 873.0)         ; 600 °C in K
(def forsterite-condensation-high 1273.0)       ; 1000 °C in K
(def sio-gas-stable-temperature 470.0)            ; ~200 °C in K
(def water-snow-line-temperature 170.0)          ; K
(def protosolar-metal-frac 0.015)
(def ice-enhancement-factor 3.5)

(def solid-phase-schema
  [:enum :phase/vapor :phase/sio-gas :phase/refractory-dust
         :phase/pebble :phase/planetesimal])
```

### 6.2 Inner-disk phase classifier

```clojure
(ns domain.planet-formation.hops315
  "Classify the solid phase of an inner-disk parcel using HOPS-315
   condensation thresholds.
   Operates on SPH parcels that carry temperature and dust fraction.")

(defn classify-inner-disk-phase
  "Return the solid phase of a parcel at radius r and temperature T."
  [T dust-to-gas]
  (cond
    (> T silicate-vaporization-temperature)
    :phase/vapor

    (and (> T forsterite-condensation-high)
         (<= T silicate-vaporization-temperature))
    :phase/sio-gas

    (and (> T sio-gas-stable-temperature)
         (<= T forsterite-condensation-high))
    :phase/refractory-dust

    (and (> T water-snow-line-temperature)
         (<= T sio-gas-stable-temperature))
    :phase/pebble

    :else
    :phase/planetesimal))
```

### 6.3 HOPS-315-style disk profile

```clojure
(ns domain.stellar.hops315
  "Toy disk profile anchored to HOPS-315 observations.
   Not a full simulation; a reference profile for tests.")

(defn hops315-disk-temperature
  "Power-law temperature profile for the inner disk.
   Reference: T ~ 470 K at 2.2 AU (atmosphere), hotter midplane inside."
  [r-au]
  (* 470.0 (Math/pow (/ r-au 2.2) -0.5)))

(defn hops315-snow-line-radius
  "Water snow line for a solar-luminosity protostar.
   Reference: ~2.5 AU for L = L_sun, A = 0.5, T_snow = 170 K."
  [luminosity albedo]
  (let [sigma 5.670374419e-8 ; Stefan-Boltzmann constant
        T 170.0]
    (Math/sqrt (/ (* (- 1.0 albedo) luminosity)
                  (* 16.0 Math/PI sigma T T T T)))))
```

### 6.4 New ECS components

```clojure
;; in domain.ecs.components
(def solid-phase :component/solid-phase)
(def dust-to-gas-ratio :component/dust-to-gas-ratio)
(def grain-size-proxy :component/grain-size-proxy)
(def disk-zone :component/disk-zone) ; :inner-refractory / :warm-sio / :outer-icy
(def planet-formation-history :component/planet-formation-history)
```

---

## 7. Mapping to the Phase 0 → Phase 1 Handoff

### 7.1 Handoff projection layer

The HOPS-315 FSM stack is rich internally, but the Phase 0 handoff should export a compact `:planet-candidate` record. The handoff spec already defines the target fields: `:material-class`, `:thermal-band`, `:atmosphere-class`, `:retained-species`, `:orbit-stable?`, `:core-dynamo?`, `:magnetic-field`, `:formation-events`.

For a HOPS-315-like system, the handoff would occur millions of years after the observed epoch, once the disk has evolved and planet candidates have formed. The projection from the FSM stack would look like:

| Internal state | Projected handoff field | Example value |
| :-- | :-- | :-- |
| `matter-state = :matter/planet` | `:material-class` | `:rocky` |
| `role-state = :role/orbit-clearer` | `:orbit-stable?` | `true` |
| `environment-state = :env/temperate-habitable` | `:thermal-band` | `:temperate` |
| `atmosphere-state = :atm/stable-secondary` | `:atmosphere-class` | `:thin` or `:substantial` |
| `atmosphere-state` + composition | `:retained-species` | `#{:n2 :o2 :ar :h2o}` |
| `magnetosphere-state = :mag/stable-dynamo` | `:core-dynamo?` | `true` |
| `magnetosphere-state` | `:magnetic-field` | surface dipole estimate in Tesla |
| `state-modifiers` + transitions | `:formation-events` | ledger of condensation, streaming, collisions |

### 7.2 Pure projection functions

```clojure
(ns domain.genesis.hops315-handoff
  "Project the rich HOPS-315 FSM state into the compact Phase 0 handoff record.")

(defn material-class [matter-state composition]
  (case matter-state
    (:matter/planet :matter/dwarf-planet :matter/protoplanet)
    (cond
      (> (:volatile-mass composition) 0.5) :mixed
      (> (:rocky-mass composition) 0.5)      :rocky
      (> (:icy-mass composition) 0.5)        :icy
      :else                                :rocky)
    (:matter/gas-giant :matter/ice-giant) :gaseous
    :else                                   :unknown))

(defn thermal-band [equilibrium-temperature]
  (cond
    (< equilibrium-temperature 200.0) :frozen
    (< equilibrium-temperature 273.0) :cold
    (< equilibrium-temperature 373.0) :temperate
    (< equilibrium-temperature 500.0) :warm
    :else                             :hot))

(defn atmosphere-class [atm-state]
  (case atm-state
    (:atm/none :atm/frozen) :none
    (:atm/transient-outgassed :atm/collapsing :atm/actively-stripped) :thin
    (:atm/stable-secondary :atm/substantial) :substantial
    (:atm/thick :atm/xuv-energy-limited :atm/xuv-recombination-limited) :thick
    :else :none))
```

### 7.3 Candidate gating

A HOPS-315-like planet candidate would be eligible for handoff only when:

- `matter-state` is planet-like (`:matter/planet`, `:matter/dwarf-planet`, `:matter/gas-giant`, `:matter/ice-giant`).
- `role-state` is `:role/orbit-clearer` or stable `:role/free-body`.
- `orbit-stable?` is true.
- Equilibrium temperature is in the candidate band (e.g., 150–400 K for liquid-water possibility).

---

## 8. Cross-Reference to Existing Research

This notebook is a companion to `docs/research/physics/protoplanetary-disks-planet-formation.md`. That document covers the dominant planet-formation channels: core accretion, gravitational instability, and streaming instability. HOPS-315 provides the **earliest observational anchor** for the solids ladder that feeds into all three channels:

- **Core accretion** begins with refractory dust and ice grains. HOPS-315 shows the dust condensing in the inner disk within 2.2 AU.
- **Gravitational instability** operates in massive, rapidly cooling outer disks. HOPS-315’s disk mass is comparable to the solar nebula, so the outer disk may be a candidate for GI if $Q \lesssim 1$ and $\beta \lesssim 3$.
- **Streaming instability** requires a dust-to-gas ratio $\gtrsim 1$ and Stokes numbers $\tau_s \sim 0.1$–$1$. HOPS-315’s Si depletion hints that solids may already be concentrating in the inner disk.

Other related research notebooks in this repo:

- `docs/research/physics/protoplanetary-disks-planet-formation.md` — disk channels, Toomre $Q$, Gammie $\beta$, snow line, and solid surface density.
- `docs/research/physics/nebular-chemistry-metal-enrichment.md` — metal/ice condensation and chemical inheritance.
- `docs/research/physics/stellar-nebula-mass-hierarchy.md` — mass hierarchy from cloud to planets.
- `docs/research/cosmology/primordial-nucleosynthesis-yields.md` — initial H/He composition that sets the disk chemistry.

---

## 9. Open Questions and Validation Checklist

### Open questions

1. What is the precise luminosity and mass accretion rate of HOPS-315 during the embedded phase? The snow-line radius depends on the instantaneous luminosity, which is higher than the main-sequence value due to accretion.
2. How much solid mass is currently hidden in the optically thick midplane? The Si-depleted jet suggests a large reservoir, but the mass is not directly constrained.
3. Is the HOPS-315 outer disk gravitationally unstable? ALMA imaging shows a 35 AU disk, but the surface-density profile is not yet published in detail.
4. How do the HOPS-315 condensation temperatures compare to the full Lodders (2003) sequence for other refractories (e.g., corundum, melilite, CAI minerals)?
5. What is the right grain-size distribution proxy to attach to each SPH parcel so that the streaming-instability threshold can be evaluated without resolving individual grains?

### Validation checklist

- [x] Citation for HOPS-315 discovery paper (McClure et al. 2025, *Nature*).
- [x] System parameters (distance, age, disk radius, chemistry) sourced from paper and press releases.
- [x] Condensation temperatures for forsterite and SiO from the paper and Lodders (2003).
- [x] Snow-line formula consistent with `protoplanetary-disks-planet-formation.md`.
- [x] FSM stack mapping consistent with Truth FSM architecture notes.
- [ ] Implement `law.hops315` Malli schemas for condensation thresholds.
- [ ] Add tests that the inner-disk phase classifier returns `:phase/refractory-dust` for $T = 800$ K and `:phase/sio-gas` for $T = 500$ K.
- [ ] Verify that HOPS-315 toy profile gives a snow-line radius near 2.5 AU for $L = L_\odot$.
- [ ] Add a regression test that the handoff projection preserves the required `:planet-candidate` keys.

---

## 10. References

1. Andrews, S. M., & Williams, J. P. (2007). “A Submillimeter View of Circumstellar Dust Disks in ρ Ophiuchus.” *ApJ*, 671, 1800–1808. DOI:10.1086/523081
2. ESO (2025). “For the first time, astronomers witness the dawn of a new solar system.” Press release, 16 July 2025. https://www.eso.org/public/news/eso2512/
3. Gammie, C. F. (2001). “Nonlinear Outcome of Gravitational Instability in Cooling, Gaseous Disks.” *ApJ*, 553, 174–183. arXiv:astro-ph/0101501. DOI:10.1086/320631
4. Johansen, A., Oishi, J. S., Mac Low, M.-M., et al. (2007). “Rapid planetesimal formation in turbulent circumstellar discs.” *Nature*, 448, 1022–1025. arXiv:0708.3890. DOI:10.1038/nature06086
5. Johansen, A., Blum, J., Tanaka, H., et al. (2014). “The multifaceted planetesimal formation process.” *Protostars and Planets VI*, 547–570. arXiv:1402.1344. DOI:10.2458/azu_uapress_9780816531240-ch024
6. Lodders, K. (2003). “Solar System Abundances and Condensation Temperatures of the Elements.” *ApJ*, 591, 1220–1247. DOI:10.1086/375492
7. McClure, M. K., van’t Hoff, M., Francis, L., Bergin, E., Rocha, W. R. M., Sturm, J. A., Harsono, D., van Dishoeck, E. F., Black, J. H., Noble, J. A., Qasim, D., & Dartois, E. (2025). “Refractory solid condensation detected in an embedded protoplanetary disk.” *Nature*, 643, 649–653. DOI:10.1038/s41586-025-09163-z
8. Prillaman, M. (2025). “This star offers the earliest peek at the birth of a planetary system like ours.” *Science News*, 16 July 2025. https://www.sciencenews.org/article/star-earliest-birth-planet-solar-system
9. Science News (2025). “Astronomers See Planet Formation ‘Time Zero’ in an Alien Solar System.” *Scientific American*, 16 July 2025. https://www.scientificamerican.com/article/astronomers-see-planet-formation-time-zero-in-an-alien-solar-system/
10. Youdin, A. N., & Goodman, J. (2005). “Streaming Instabilities in Protoplanetary Disks.” *ApJ*, 620, 459–469. DOI:10.1086/426895

---

*This notebook was produced by the truth-research-cosmology actor as a non-destructive research artifact for `docs/research/cosmology/`.*
