# HOPS-315 and the Nebula-to-Life FSM Architecture

**Topic:** Observational case study of HOPS-315 and the derived coupled-FSM architecture for classifying worlds from nebula to life.  
**Source:** `Tell me what we've learned studying HOPS315 with j` conversation chunks (10 files).  
**Status:** Conceptual architecture complete; no formal `docs/research/` notebook yet; implementation spec for handoff projection exists in `docs/specs/`.

## HOPS-315 Case Study

HOPS-315 is a ~0.1–0.2 Myr old protostar in the Orion B molecular cloud, 1,300–1,400 light-years away. JWST + ALMA observations (Nature, July 2025) detected the earliest stages of rocky-planet-building material condensing in the inner disk:

- **SiO gas** at ~200°C in the inner disk, within ~2.2 AU of the protostar.
- **Crystalline silicates** (forsterite) condensing at 600–1000°C, concentrated within ~1 AU.
- Analogous to **calcium-aluminum-rich inclusions (CAIs)**, the oldest solids in our Solar System.

This is an empirical anchor for the "time zero" of rocky planet formation: gas → refractory solids → dust → pebbles → planetesimals → planets.

## Key Physical Implications for Truth

- **Sub-grid grains:** Individual grains are too numerous to resolve. They must be treated as a dust fraction and grain-size distribution carried by SPH parcels.
- **Condensation thresholds:** Temperature-dependent phase transitions (e.g., forsterite at 600–1000°C, SiO at ~200°C) are concrete state transitions that can be pure functions.
- **Super-particles:** When local dust-to-gas ratio or Stokes number crosses a threshold, promote a fraction of dust mass into a small number of planetesimal super-particles, rather than spawning 10¹⁶ discrete bodies.
- **Inner-disk zoning:** 1–2 AU refractory zone maps cleanly onto radial bands in the geodesic grid.

## The Five Coupled FSMs

Rather than one giant state label, the world is classified by a stack of orthogonal state machines, each owned by a single domain system:

| FSM | Owns | Question | Example States |
|---|---|---|---|
| **Matter** | `domain.stellar` + `domain.planet-formation` | What physically is this thing? | `:matter/nebula`, `:matter/protostar`, `:matter/planet`, `:matter/dwarf-planet`, `:matter/planetesimal` |
| **Role** | `domain.orbital` | What is its dynamical relationship? | `:role/orbit-clearer`, `:role/satellite`, `:role/belt-member`, `:role/scattered-body` |
| **Environment** | `domain.environment` | What regime is its surface/interior in? | `:env/magma-ocean`, `:env/temperate-habitable`, `:env/arid-thin-atmosphere`, `:env/subsurface-ocean` |
| **Atmosphere / EM** | `domain.atmosphere` + `domain.em` | Can it hold/protect an atmosphere? | `:atm/stable-secondary`, `:atm/actively-stripped`, `:mag/stable-dynamo`, `:mag/collapsed` |
| **Biosphere** | `domain.biology` | What level of life exists? | `:bio/none`, `:bio/prebiotic`, `:bio/microbial`, `:bio/complex` |

## Transition Modifiers

Some factors are not states but modifiers that bias transition rates:

- `:mod/has-large-moon`
- `:mod/giant-planet-shielding` / `:mod/giant-planet-scattering`
- `:mod/heavy-bombardment-era`
- `:mod/strong-tidal-heating`
- `:mod/stellar-youth-xuv`
- `:mod/interior-dynamo-decline`
- `:mod/late-volatile-delivery`

## Phase 0 → Phase 1 Handoff Projection

The rich internal FSM state is compressed into a compact `:planet-candidate` record. The handoff spec already defines the target fields:

- `:material-class` — projection of `matter-state` + composition
- `:thermal-band` — projection of star luminosity + semi-major axis
- `:atmosphere-class` — compression of `atmosphere-state`
- `:retained-species` — volatiles retained by gravity/temperature
- `:orbit-stable?` — analytic stability proxy
- `:core-dynamo?` — projection of `magnetosphere-state`
- `:magnetic-field` — surface dipole estimate
- `:formation-events` — causal history ledger

## Canonical Examples

| Body | Matter | Role | Environment | Atmosphere/EM | Biosphere |
|---|---|---|---|---|---|
| Earth | `:matter/planet` | `:role/orbit-clearer` | `:env/temperate-habitable` | `:mag/stable-dynamo`, `:atm/stable-secondary` | `:bio/technological` |
| Mars | `:matter/planet` | `:role/orbit-clearer` | `:env/arid-thin-atmosphere` or `:env/post-habitable` | `:mag/collapsed`, `:atm/collapsing` | `:bio/none` or `:bio/extinct` |
| Moon | `:matter/rocky-body` | `:role/satellite` | `:env/airless-inert` | `:mag/no-dynamo`, `:atm/none` | `:bio/none` |
| Pluto | `:matter/dwarf-planet` | `:role/belt-member` | `:env/icy-volatile-world` | `:mag/no-dynamo`, `:atm/frozen` | `:bio/none` |
| Europa | `:matter/moon` | `:role/satellite` | `:env/subsurface-ocean` | `:mag/induced-weak` | `:bio/prebiotic` or `:bio/microbial` |

## Connections to Other Topics

- `phase0-nebula` provides the physical processes that the Matter FSM classifies.
- `protoplanetary-disks-planet-formation` (existing research) grounds the dust → pebble → planetesimal transition in streaming instability and coagulation literature.
- `ecs-physics-substrate` provides the single-world constraint that makes the FSM stack possible.
- `deep-research-brief` Section 2 asks for the disk microphysics that HOPS-315 exemplifies.
- `stellar-mergers-accretion` affects the `:Role` and `:Atmosphere/EM` states through binary-star context.

## Open Questions

- What is the minimum set of observables needed to classify each FSM without over-fitting?
- How do we represent the dust/grain size distribution as an ECS component?
- What is the precise streaming-instability threshold for promoting super-particles?
- Should the handoff record include `:candidate-kind :moon` for habitable satellites?
- How do we guard against FSM oscillation when multiple conditions are true simultaneously?
