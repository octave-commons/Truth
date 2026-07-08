---

# continue

Yes — the next useful artifact is the **smallest executable slice** of the architecture: pure projection functions plus `domain.genesis/handoff-system`, because the handoff spec already defines both the target record and the phased implementation order.[^21_1][^21_2]

## Projection functions

The handoff spec’s first three implementation phases already imply the first pure functions you should standardize: `material-class`, `thermal-band`, `orbit-stable?`, `atmosphere-class`, `retained-species`, and a simple dynamo / magnetic-field estimate.[^21_2][^21_1]
I would treat those as the public projection layer that reads richer internal state and observables, then writes the coarse components the handoff record needs.[^21_3][^21_1]

```clojure
(ns law.handoff)

(defn material-class [body]
  ;; reads: matter-state, bulk composition, envelope fraction
  ;; returns: :rocky | :icy | :gaseous | :mixed
  ...)

(defn thermal-band [star body]
  ;; reads: luminosity, semi-major axis
  ;; returns: :frozen | :cold | :temperate | :warm | :hot
  ...)

(defn orbit-stable? [star body siblings]
  ;; analytic proxy first: bound orbit, no plunge, no too-close Hill conflicts
  ...)

(defn atmosphere-class [body]
  ;; reads: atmosphere-state or retention estimate
  ;; returns: :none | :thin | :substantial | :thick
  ...)

(defn retained-species [body]
  ;; returns subset of volatile species retained by gravity/temperature
  ...)

(defn core-dynamo? [body]
  ;; simple convective + rotation proxy
  ...)

(defn magnetic-field [body]
  ;; surface dipole estimate
  ...)
```


## Classifier wiring

Phase 1 in the spec says to add pure `material-class` and `thermal-band` functions and write them as components, while later phases add `orbit-stable?`, `atmosphere-class`, and `retained-species`.[^21_1]
So the clean wiring is:

- `domain.stellar` or `domain.genesis.classify` writes `c/material-class` and `c/thermal-band`.[^21_1]
- `domain.orbital.stability` writes `c/orbit-stable?` using the cheap analytic proxy the spec recommends first.[^21_1]
- `domain.atmosphere` writes `c/atmosphere-class` and `c/retained-species`.[^21_1]
- `domain.em` or `domain.interior` writes `c/core-dynamo?` and `c/magnetic-field`, because the handoff record already expects both fields.[^21_2]

That keeps the deep FSMs private and lets the handoff system read only stable, compact summaries plus raw observables.[^21_3][^21_2]

## Handoff system

The spec is explicit that `domain.genesis/handoff-system` should run after classification and append a `:phase0-handoff` ledger event when the conditions for success are met.[^21_3][^21_1]
So the system should do four things only:

1. Find stable star(s).[^21_3]
2. Find eligible candidate bodies.[^21_3][^21_1]
3. Read summary components plus required raw observables.[^21_2]
4. Emit one `:planet-candidate` map per candidate and append the handoff event.[^21_2][^21_1]
```clojure
(ns domain.genesis.handoff)

(defn eligible-candidate? [world eid]
  (let [matter   (ecs/get world eid c/matter-state)
        stable?  (ecs/get world eid c/orbit-stable?)
        temp     (ecs/get world eid c/equilibrium-temperature)]
    (and stable?
         (contains? #{:matter/planet :matter/dwarf-planet
                      :matter/gas-giant :matter/ice-giant} matter)
         (<= 150.0 temp 400.0)))) ; initial candidate window from spec
```

```clojure
(defn planet-candidate-record [world star-id eid]
  {:planet-id                eid
   :star-id                  star-id
   :material-class           (ecs/get world eid c/material-class)
   :thermal-band             (ecs/get world eid c/thermal-band)
   :equilibrium-temperature  (ecs/get world eid c/equilibrium-temperature)
   :semi-major-axis          (ecs/get world eid c/semi-major-axis)
   :eccentricity             (ecs/get world eid c/eccentricity)
   :orbit-stable?            (ecs/get world eid c/orbit-stable?)
   :atmosphere-class         (ecs/get world eid c/atmosphere-class)
   :retained-species         (ecs/get world eid c/retained-species)
   :bulk-composition         (ecs/get world eid c/composition)
   :angular-momentum         (ecs/get world eid c/angular-momentum)
   :rotation-axis            (ecs/get world eid c/rotation-axis)
   :oblateness               (ecs/get world eid c/oblateness)
   :surface-gravity          (ecs/get world eid c/surface-gravity)
   :core-dynamo?             (ecs/get world eid c/core-dynamo?)
   :magnetic-field           (ecs/get world eid c/magnetic-field)
   :formation-events         (ecs/get world eid c/formation-events [])})
```

That pseudocode mirrors the exact contract fields already listed in the spec, which is the strongest reason to keep the projection layer small and literal.[^21_2]

## Candidate eligibility

The success condition in the handoff spec requires a stable star, at least one candidate planet, and a physically grounded record rather than a cinematic endpoint.[^21_3]
A good first-pass `eligible-candidate?` predicate should therefore require:

- a stable star exists,[^21_3]
- `matter-state` is planet-like rather than debris-like,[^21_1][^21_2]
- `orbit-stable?` is true, using the analytic proxy for now,[^21_1]
- equilibrium temperature sits inside the current candidate band of 150 K to 400 K, which the spec already uses as a plausibility window for liquid water under some atmosphere.[^21_3]

I would keep moons out of the first MVP unless you add a `:candidate-kind` field, because the current contract is explicitly named `:planet-candidate` even though the broader architecture could later support habitable satellites.[^21_2]

## Tests first

The implementation plan already gives you the minimum useful tests, and they map almost one-to-one to the projection functions.[^21_1]
So the first concrete TDD set should be:

- `rocky-planet-classified-by-composition` → `material-class`.[^21_1]
- `thermal-band-computed-from-orbit` → `thermal-band`.[^21_1]
- `circular-orbit-is-stable` / `plunging-orbit-is-unstable` / `close-planet-pair-is-unstable` → `orbit-stable?`.[^21_1]
- `earth-like-retains-n2` / `moon-like-loses-atmosphere` / `gas-giant-retains-h2` → `atmosphere-class` + `retained-species`.[^21_1]
- `handoff-emits-when-star-and-planet-exist` and `handoff-record-contains-required-keys` → `handoff-system`.[^21_1]

That gets you a real Phase‑0 deliverable fast, while leaving the richer FSM stack free to deepen later without breaking the outward contract.[^21_3][^21_1]

## Spec shape

If you want the next doc section to feel native to Truth, I would write it under headings like:

- `## Projection components`
- `## Pure projection functions`
- `## Handoff-system pseudocode`
- `## Candidate eligibility`
- `## TDD checklist`

That would connect the long “nebula to life” architecture back to the repo’s actual immediate milestone: emitting a trustworthy `:phase0-handoff` record.[^21_3][^21_1]

<div align="center">⁂</div>

[^21_1]: https://pubs.acs.org/doi/pdf/10.1021/acscentsci.4c00820

[^21_2]: https://arxiv.org/html/2410.00093v1

[^21_3]: http://arxiv.org/pdf/1510.06205.pdf

