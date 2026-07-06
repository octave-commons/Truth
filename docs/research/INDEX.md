# Deep Research Index

**Last updated:** 2026-07-06 (stellar-wind-plasma-state notebook added)
**Maintained by:** truth-research-coordinator actor

This index catalogs all research notebooks produced by the deep research actor family.
Each notebook follows the `deep-research` skill format with LaTeX, Clojure pseudocode,
charts, and a promotion path to domain code.

## How to Use

- **Finding research:** Browse by domain or search for specific topics.
- **Adding research:** Domain actors append new entries. The coordinator updates cross-references.
- **Status:** `draft` → `validated` → `spec-derivation` → `promoted`
  - **draft:** Initial research, may have gaps
  - **validated:** Reviewed by coordinator, cross-checked against literature
  - **spec-derivation:** Malli schemas and ECS components derived in `src/law/`
  - **promoted:** Implementation code exists in `src/domain/`

## Derived Specs

Research notebooks have spawned the following spec files in `src/law/`:

| Spec File | Source Notebook | Contents |
|-----------|---------------|----------|
| `law/composition.clj` | cosmology/primordial-nucleosynthesis-yields.md | BBN primordial composition, metallicity, composition schema |
| `law/sed.clj` | phase1-radiation-plasma-truth.md §2-3 | SED bands, profiles, atmosphere shells, band helpers |
| `law/plasma.clj` | phase1-radiation-plasma-truth.md §4-6 | Wind profiles, plasma wind parcels, atmospheric escape, space-weather events |

New ECS component keywords added to `domain.ecs.components`:
`sed-bands`, `atmosphere-shells`, `wind-profile`, `atmosphere-escape`, `event-source`, `lod-level`

## Cosmology

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| primordial-nucleosynthesis-yields.md | spec-derivation | 0 | Y_p=0.247, D/H=2.53e-5, Li7 problem (3× gap). Primordial comp: H=0.753 He=0.247 | PDG 2025, Yeh+2026 |
| bbn_yields.ipynb | validated | 0 | Clojure BBN calculator with ASCII charts, 4/4 validation PASS | PDG 2025 |
| stellar-sed-template-grid.md | validated | 1 | 12 minimum templates, key band ratios 10²–10⁴× variation | Pickles 1998, CK04, Husser+ 2013 |
| stellar-sed-template-grid.md | draft | 0 | 12-template minimum SED grid for band-integrated luminosities (gamma→radio). 450-point full grid with interpolation. Clojure EDN format. | Pickles 1998, CK04, Husser 2013, Bohlin 2017 |

**Actor:** truth-research-cosmology
**Schedule:** Every 48h
**Topics:** Stellar physics, galaxy formation, primordial nucleosynthesis, CMB, dark matter/energy, IMF, stellar populations

## Geology

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-geology
**Schedule:** Every 48h
**Topics:** Plate tectonics, mantle convection, volcanism, cratering, erosion, mineralogy, planetary differentiation

## Biology

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-biology
**Schedule:** Every 48h
**Topics:** Ecology, evolution, astrobiology, Lotka-Volterra, nutrient cycles, abiogenesis, biosignatures

## Atmosphere

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| xuv-escape-regime-transition.md | validated | 1 | R = t_rec/t_flow controls transition; F_crit ~ 10⁴ erg/cm²/s | Murray-Clay+2009, Owen & Alvarez 2016, Lampón+2021 |

**Actor:** truth-research-atmosphere
**Schedule:** Every 48h
**Topics:** Radiative transfer, climate, Hadley cell, greenhouse effect, atmospheric escape, cloud microphysics

## Physics

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| mhd-em-lorentz-optimization.md | draft | 0 | Threshold-gated MHD-lite: reuse hydro neighbor/gradient cache, full curl only where β or M_A demands it; pressure-only is not a generic proxy. | Price & Monaghan 2004a,b, 2005; Price 2012; Tricco 2012, 2016, 2023; Wurster et al. 2014, 2021; Mellon & Li 2008, 2009 |
| phase0-tick-loop-optimization.md | draft | 0 | 60 Hz budget feasible for N=500 with Barnes–Hut + SoA cache; avoid per-system futures | Springel 2005, Quinn+1997, Bagwell 2001 |
| stellar-wind-plasma-state.md | spec-derivation | 1 | Keep stellar ejecta as :nebula + ionization-fraction continuum; Parker wind speed from corona T; ram pressure/photoionization track wind–nebula coupling | Parker 1958, 1960; Cranmer 2009; Asplund+2009; Castor+1975; Weaver+1977; Krumholz+2006; Henney+2009; Murray-Clay+2009; Owen & Alvarez 2016 |
| protoplanetary-disks-planet-formation.md | draft | 0 → 1 | Core accretion, GI, and streaming-instability channels grounded; GI fragments resolvable at parcel mass, planetesimals/core-accretion sub-grid; add Toomre+Q/cooling gate. | Pollack+1996, Boss 1997, Gammie 2001, Johansen+2007/2014, Youdin & Goodman 2005, Lodders 2003, Oberg+2011, Andrews & Williams 2007 |
| phase1-radiation-plasma-truth.md | spec-derivation | 1 | Panchromatic SEDs, 4-layer atmospheres, Parker winds, XUV escape | PDG 2025, Parker 1958 |
| stellar-sed-template-grid.md | validated | 1 | 12 minimum templates, key band ratios 10²–10⁴× variation | Pickles 1998, CK04, Husser+ 2013 |
| barnes-hut-gravity-optimization.md | validated | 0 | 500-particle BH target ~5 ms achievable; θ=0.5 gives ~1% RMS error; promotion path to `domain.gravity`/`domain.orbital` | Barnes & Hut 1986, Salmon & Warren 1994, Dehnen 2002, Springel 2005, OpenJDK JEP 448 |
| sph-neighbor-kernel-optimization.md | validated | 0 | Uniform grid for radius queries + octree for nearest neighbor; cubic-spline kernel; r²-first cutoff; Verlet layer optional | Price 2010, Springel 2010/2005, Yao 2004 |
| stellar-nebula-mass-hierarchy.md | draft | 0 | Replace coarse `:debris` with mass ladder grounded in opacity limit, deuterium/hydrogen limits, brown-dwarf desert; promotion path to classifier + player economy | Krumholz 2014, Hennebelle & Chabrier 2008/2009, Spiegel+2010, Johansen+2014, Pearson & McCaughrean 2023, Cui+2026, Krumholz+2016, Whitworth 2018, De Furio+2024 |
| nebular-chemistry-metal-enrichment.md | draft | 0 | Metal origins, primordial vs. Population I composition, element/molecule/bulk tracking, dust condensation sequence; promotion path to chemistry/stellar/integrator/law | Fields & Sarkar 2025, Yeh+2026, Asplund+2009, Lodders 2003, Nomoto+2013, Woosley & Weaver 1995, Johansen+2014 |
| rate-limited-accretion-mass-transfer.md | draft | 0 | BHL sink accretion + Roche-lobe overflow + sink-particle gradual debit schemes; caps and conservation for ECS | Bondi 1952, Edgar 2004, Federrath+2010, Krumholz+2004, Hubber+2013, Eggleton 1983, Ritter 1988, Kolb & Ritter 1990 |

**Actor:** truth-research-physics
**Schedule:** Every 48h
**Topics:** SPH methods, N-body gravity, MHD, orbital mechanics, numerical methods, error analysis

## Culture

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-culture
**Schedule:** Every 48h
**Topics:** Agent-based social models, settlement patterns, cultural evolution, mythogenesis, language evolution, collapse dynamics

## Cross-Domain

| Notebook | Status | Domains | Key Finding | Sources |
|----------|--------|---------|-------------|---------|
| phase1-radiation-plasma-truth.md | spec-derivation | physics, atmosphere, cosmology | SED→winds→escape→LOD pipeline; replaces scalar luminosity with panchromatic bands | Parker 1958, PDG 2025 |
| stellar-wind-plasma-state.md | spec-derivation | physics, atmosphere | Plasma ejecta as :nebula+ionization continuum; wind–nebula ram pressure and photoionization | Parker 1958, 1960; Cranmer 2009; Weaver+1977; Henney+2009 |

**Actor:** truth-research-coordinator
**Schedule:** Every 72h

---

## Research Coverage by Simulation Phase

| Phase | Description | Primary Domains | Coverage |
|-------|-------------|-----------------|----------|
| 0 | Stellar Nebula → Solar System | cosmology, physics | BBN composition specs derived |
| 1 | Radiation & Plasma | atmosphere, physics | SED/atmosphere/wind/escape specs derived |
| 2 | Rocky Body Formation | geology, physics | — |
| 3 | Tectonics & Atmosphere | geology, atmosphere | — |
| 4 | Ocean & Climate | atmosphere, biology | — |
| 5 | Biosphere | biology, atmosphere | — |
| 6 | Civilization & Myth | culture, biology | — |

## Actor Status

```bash
# Check all research actor status
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-cosmology
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-geology
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-biology
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-atmosphere
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-physics
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-culture
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-coordinator
```
