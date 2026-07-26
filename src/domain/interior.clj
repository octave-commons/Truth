(ns domain.interior
  "Macro geology field seed: the pure, deterministic function from an M5
   `:planet-candidate` handoff record (`law.stellar.schema/planet-candidate-schema`)
   to the initial macro geology field — layer template, tectonic plates,
   mantle convection, coarse resource field, initial thermal/environment
   state (Voxel 2, kanban/tasks/planet-candidate-to-voxel-seed.md; design
   docs/designs/planetary-voxel-substrate.md §4, §7).

   DETERMINISM IS THE CONTRACT: the field-seed + edit-diff save strategy
   (design §7.3, owner decision 2026-07-22) regenerates the field from the
   candidate record and replays persisted `law.voxel/edit-diff-schema` diffs
   on top — so `seed-field` MUST be bit-for-bit reproducible. The seeding
   scheme: there is NO PRNG anywhere in this namespace. Every spatial
   pattern is a closed-form function of the candidate record — Fibonacci
   spirals (`law.interior/golden-angle-radians`) oriented on the candidate's
   `:rotation-axis`, an orientation phase mixed from `:angular-momentum` and
   `:equilibrium-temperature`, and per-index golden-angle rotations. Same
   candidate in ⇒ identical doubles out, on any JVM.

   FP-ORDER HARDENING: double addition is order-sensitive, and every
   reduction in this namespace that feeds seed values iterates a SORTED
   collection (`(sort comp/ice-formers)` for ice mass fractions,
   `normalize-shares` sorting by key before `law.composition/normalize`),
   so the addition order is pinned by keyword compare — spec-stable across
   Clojure upgrades, not merely stable for the current runtime's hash
   layout. (`law.composition/normalize` is shared with other consumers and
   deliberately unmodified; all uses HERE are order-insensitive by
   construction.)

   INPUT CONTRACT: the candidate is assumed to have passed the M5 handoff
   gate (`domain.stellar.classifier.candidate/handoff-system`): bound orbit,
   150–400 K equilibrium temperature, ≤95% H/He by mass, at least :thin
   atmosphere. In particular the bulk composition ALWAYS has >5% non-gas
   elements, so the resource-field renormalizations below never divide by
   zero for a gated candidate. A `:gaseous` candidate CAN technically clear
   that gate (95% H/He ceiling) but has no solid surface to seed (design §4)
   — `seed-field` fails loudly on it rather than inventing a crust.

   MISSING DATA: the candidate record carries no `:mass`/`:radius` — only
   `:surface-gravity` g. Radius and mass are DERIVED (not invented, not
   added to the handoff writer): a per-material-class mean-density estimate
   (`law.interior/mean-density-reference`, iron-adjusted) turns g = (4/3)πGρR
   into R, and M = gR²/G follows. Uniform-density inversion is the honest
   first model; the §7 layer-thickness gap is documented in
   `law.interior`'s constants."
  (:require
   [clojure.math :as math]
   [law.composition :as comp]
   [law.interior :as law]
   [law.stellar :as law-stellar]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

;; --- Small vec helpers (over shape.spatial primitives) ---------------------------

(defn- normalize
  "Unit vector of `v`; `fallback` when `v` is near-zero (degenerate axis —
   e.g. a candidate whose `:rotation-axis` came through as the zero vector)."
  [v fallback]
  (let [l (sp/len v)]
    (if (> l 1.0e-12)
      (mapv (fn [x] (double (/ x l))) v)
      fallback)))

(defn- fract
  "Fractional part of `x` in [0,1)."
  [x]
  (- (double x) (math/floor (double x))))

(defn- ice-formers-mass-fraction
  "Total ice-formers mass fraction of composition map `m`. The reduction is
   over (sort comp/ice-formers) — keywords compare deterministically — so
   the double-addition order is spec-stable, not runtime-hash-layout-stable."
  [m]
  (reduce + 0.0 (map (fn [e] (double (get m e 0.0))) (sort comp/ice-formers))))

(defn- normalize-shares
  "Order-insensitive wrapper over `law.composition/normalize`: the input is
   sorted by key first, so normalize's internal double-addition order is
   spec-stable (keyword compare), not dependent on runtime hash layout.
   `law.composition/normalize` itself is shared (other consumers) and
   deliberately left untouched."
  [m]
  (comp/normalize (into (sorted-map) m)))

;; --- Thermal seed ------------------------------------------------------------------

(defn surface-temperature
  "Initial surface temperature (K): the candidate's `:equilibrium-temperature`
   when present, else its `:thermal-band` midpoint (the degenerate unbound-
   orbit case `planet-candidate-schema` permits; unreachable for a gated
   candidate, which always has a resolved temperature)."
  [candidate]
  (double (or (:equilibrium-temperature candidate)
              (get law/thermal-band-midpoint-k (:thermal-band candidate) 300.0))))

(defn initial-environment
  "Initial environment-FSM state for the seeded world, per the ownership
   ladder in docs/research/physics/nebula-to-life-fsm.md §4.3 — the voxel
   crust's initial state and the Environment FSM's initial state describe
   the same physical fact at two resolutions, so they must agree
   (design §4). `domain.environment` does not exist yet; this mapping IS
   the agreed starting state that namespace must reproduce from the same
   candidate. Throws on an unknown material class."
  [candidate]
  (let [band    (:thermal-band candidate)
        airless? (= :none (:atmosphere-class candidate))]
    (case (:material-class candidate)
      :rocky (case band
               :hot       :env/magma-ocean
               :warm      :env/crusted-volcanic
               :temperate :env/temperate-habitable
               :cold      :env/snowball
               :frozen    (if airless? :env/airless-inert :env/snowball))
      :icy   (if (contains? #{:hot :warm :temperate} band)
               :env/ocean-world
               :env/icy-volatile-world)
      :mixed (case band
               :hot       :env/crusted-volcanic
               :warm      :env/crusted-volcanic
               :temperate :env/temperate-habitable
               :cold      :env/snowball
               :frozen    :env/icy-volatile-world)
      (throw (ex-info "domain.interior/initial-environment: no environment mapping for material class"
                      {:material-class (:material-class candidate)})))))

;; --- Body figure: radius/mass derived from :surface-gravity -------------------------

(defn mean-density
  "Estimated mean density (kg/m³): the `:material-class` reference adjusted
   linearly for the bulk Fe+Ni fraction AROUND THE CLASS'S OWN reference
   share (`law.interior/iron-fraction-reference`) — iron-rich bodies are
   denser, so at the same surface gravity they are smaller, and metal-poor
   icy bodies are not dragged below real icy-body densities. No compression
   model (honest first model, `law.interior` constants)."
  [candidate]
  (let [mclass (:material-class candidate)
        base   (get law/mean-density-reference mclass
                    (:mixed law/mean-density-reference))
        fe-ref (get law/iron-fraction-reference mclass
                    (:mixed law/iron-fraction-reference))
        fe-ni  (+ (double (get-in candidate [:bulk-composition :Fe] 0.0))
                  (double (get-in candidate [:bulk-composition :Ni] 0.0)))]
    (+ (double base)
       (* law/iron-density-lever (- fe-ni fe-ref)))))

(defn derive-radius-m
  "Body radius (m) implied by surface gravity `g` (m/s²) and mean density
   `rho` (kg/m³) under the uniform-density inversion g = (4/3)πGρR. Throws
   on non-positive g — a gated candidate always has a resolved figure, so a
   zero here is a contract violation, not a case to paper over."
  [g rho]
  (when-not (pos? (double g))
    (throw (ex-info "domain.interior/derive-radius-m: non-positive surface gravity"
                    {:surface-gravity g})))
  (/ (* 3.0 (double g))
     (* 4.0 math/PI law-stellar/G (double rho))))

(defn derive-mass-kg
  "Body mass (kg) from surface gravity and radius: M = gR²/G."
  [g radius-m]
  (/ (* (double g) (double radius-m) (double radius-m))
     law-stellar/G))

;; --- Layer template (design §7 gap — honest first model) ------------------------------

(defn core-mass-fraction
  "Core mass fraction: the bulk Fe+Ni share divided by the core's assumed
   Fe+Ni content (`law.interior/core-iron-fraction-reference`) — i.e. all
   the body's iron differentiated into the core. Clamped to
   `core-mass-fraction-bounds`; this is the documented §7 first model, not
   a differentiation simulation."
  [candidate]
  (let [fe-ni (+ (double (get-in candidate [:bulk-composition :Fe] 0.0))
                 (double (get-in candidate [:bulk-composition :Ni] 0.0)))
        [lo hi] law/core-mass-fraction-bounds]
    (-> (/ fe-ni law/core-iron-fraction-reference)
        (max lo)
        (min hi))))

(defn- layer-temperature
  "Temperature (K) at a layer whose mid-depth below the surface is
   `depth-m`: surface temperature plus the linear geothermal gradient,
   capped per thermal band (`law.interior/interior-temperature-ceiling-k`)."
  [t-surface band depth-m]
  (min (+ t-surface (* law/geothermal-gradient-k-per-m (double depth-m)))
       (double (get law/interior-temperature-ceiling-k band 2000.0))))

(defn- shell-layer
  "Outer shell volume solve: mass `shell-mass` (kg) at reference density
   `rho` (kg/m³) inside outer radius `radius-m` → layer map. Inner radius is
   solved from the spherical-shell volume, never allowed to swallow the
   whole body by construction of the caller's mass budget."
  [layer-name radius-m shell-mass rho t-surface band]
  (let [r3    (math/pow radius-m 3.0)
        dv    (/ (double shell-mass)
                 (* (/ 4.0 3.0) math/PI (double rho)))
        inner (math/cbrt (max 0.0 (- r3 dv)))
        vol   (* (/ 4.0 3.0) math/PI (- r3 (math/pow inner 3.0)))
        depth (- radius-m (/ (+ radius-m inner) 2.0))]
    {:name         layer-name
     :inner-radius (double inner)
     :outer-radius (double radius-m)
     :mass         (double shell-mass)
     :density      (double (/ shell-mass vol))
     :temperature  (double (layer-temperature t-surface band depth))}))

(defn layer-template
  "Differentiated layer stack (inside-out: core, mantle, shell) for
   `candidate` with derived `radius-m` (m), `mass-kg` (kg), and surface
   temperature `t-surface` (K). Every record validates
   `law.voxel/macro-layer-schema`.

   Model (the §7 first model, constants in `law.interior`):
   - SHELL: `:rocky`/`:mixed` get a `:crust` whose thickness is the band-
     scaled reference; `:icy` gets an `:ice-shell` whose mass is the bulk
     ice-formers share of the body, CAPPED so the shell occupies at most
     `law.interior/ice-shell-max-volume-fraction` of the body's volume at
     surface-ice density — ice beyond the cap is deep high-pressure ice,
     folded into the mantle's mass below (keeps the solve volume-consistent
     on ice-rich bodies).
   - CORE: `core-mass-fraction` of the total mass, capped to leave mantle.
   - MANTLE: whatever mass remains — mass conservation is EXACT (layer
     masses sum to `mass-kg` up to double rounding); densities are the
     mass/volume-consistent values, temperatures the band-capped linear
     profile. No false precision: this is a starting stack for edit diffs
     to diverge from, not a claimed interior structure."
  [candidate radius-m mass-kg t-surface]
  (let [band      (:thermal-band candidate)
        mclass    (:material-class candidate)
        icy?      (= :icy mclass)
        bulk      (:bulk-composition candidate)
        shell-mass (if icy?
                     (min (* (double mass-kg) (ice-formers-mass-fraction bulk))
                          (* law/ice-density-reference
                             law/ice-shell-max-volume-fraction
                             (/ 4.0 3.0) math/PI (math/pow radius-m 3.0)))
                     (let [d (min (* law/crust-thickness-reference-m
                                     (get law/crust-thickness-band-factor band 1.0))
                                  (* law/shell-max-radius-fraction radius-m))
                           r3 (math/pow radius-m 3.0)
                           ri (- radius-m d)
                           vol (* (/ 4.0 3.0) math/PI (- r3 (math/pow ri 3.0)))]
                       (* vol law/crust-density-reference)))
        shell     (shell-layer (if icy? :ice-shell :crust)
                               radius-m shell-mass
                               (if icy? law/ice-density-reference
                                   law/crust-density-reference)
                               t-surface band)
        remaining (- (double mass-kg) shell-mass)
        core-mass (* (min (core-mass-fraction candidate)
                          law/core-max-remaining-mass-fraction)
                     remaining)
        core-r    (math/cbrt (/ (* 3.0 core-mass)
                                (* 4.0 math/PI law/core-density-reference)))
        core      {:name         :core
                   :inner-radius 0.0
                   :outer-radius (double core-r)
                   :mass         (double core-mass)
                   :density      (double law/core-density-reference)
                   :temperature  (double (layer-temperature t-surface band
                                                            (- radius-m (/ core-r 2.0))))}
        mantle-in  core-r
        mantle-out (:inner-radius shell)
        mantle-mass (- remaining core-mass)
        mantle-vol (* (/ 4.0 3.0) math/PI
                      (- (math/pow mantle-out 3.0) (math/pow mantle-in 3.0)))
        mantle    {:name         :mantle
                   :inner-radius (double mantle-in)
                   :outer-radius (double mantle-out)
                   :mass         (double mantle-mass)
                   :density      (double (/ mantle-mass mantle-vol))
                   :temperature  (double (layer-temperature
                                          t-surface band
                                          (- radius-m (/ (+ mantle-in mantle-out) 2.0))))}]
    [core mantle shell]))

;; --- Deterministic spatial layout ------------------------------------------------------

(defn- phase-of
  "Orientation phase (radians) for the spiral layouts: 2π × the fractional
   part of a fixed mix of candidate scalars (spin angular-momentum magnitude,
   surface temperature). Any fixed function of the record would do; this one
   simply decorrelates two candidates' layouts. NOT randomness — a pure
   function of the candidate, part of the documented seeding scheme."
  [candidate t-surface]
  (let [l (sp/len (:rotation-axis candidate [0.0 0.0 1.0]))
        am (sp/len (:angular-momentum candidate [0.0 0.0 0.0]))]
    (* 2.0 math/PI (fract (+ l (/ am 1.0e34) (/ t-surface 137.0))))))

(defn- axis-basis
  "Orthonormal basis [a u v] with `a` along the candidate's rotation axis
   (spiral poles = spin poles), `u`/`v` spanning the equatorial plane.
   Deterministic; degenerate axes fall back to +z."
  [candidate]
  (let [a   (normalize (:rotation-axis candidate [0.0 0.0 1.0]) [0.0 0.0 1.0])
        seed (if (< (Math/abs (nth a 0)) 0.9) [1.0 0.0 0.0] [0.0 1.0 0.0])
        u   (normalize (sp/cross a seed) [0.0 1.0 0.0])
        v   (sp/cross a u)]
    [a u v]))

(defn- spiral-point
  "Fibonacci-spiral point `i` of `n` on the unit sphere in basis [a u v],
   rotated by `phase` radians about `a`. Closed form; equal-area spread for
   any n without a PRNG."
  [i n phase a u v]
  (let [y     (- 1.0 (/ (* 2.0 (+ (double i) 0.5)) (double n)))
        r     (math/sqrt (max 0.0 (- 1.0 (* y y))))
        theta (+ (* (double i) law/golden-angle-radians) phase)
        c     (math/cos theta)
        s     (math/sin theta)]
    (mapv (fn [ac uc vc] (double (+ (* y ac) (* r (+ (* c uc) (* s vc))))))
          a u v)))

;; --- Tectonic plates --------------------------------------------------------------------

(defn plate-count
  "Number of tectonic plates: the reference count scaled by sqrt(g/g_ref)
   (higher surface gravity fragments the lithosphere), clamped."
  [candidate]
  (let [g (double (:surface-gravity candidate))
        [lo hi] law/plate-count-bounds]
    (-> (math/round ^double (* law/plate-count-reference
                               (math/sqrt (/ g law/surface-gravity-reference))))
        (max lo)
        (min hi)
        int)))

(defn- activity-factor
  "Tectonic activity multiplier: per-band factor times a clamped
   sqrt(g/g_ref) lever."
  [candidate]
  (let [band-factor (get law/tectonic-activity-band-factor (:thermal-band candidate) 1.0)
        lever       (-> (math/sqrt (/ (double (:surface-gravity candidate))
                                      law/surface-gravity-reference))
                        (max (first law/plate-speed-gravity-lever-bounds))
                        (min (second law/plate-speed-gravity-lever-bounds)))]
    (* band-factor lever)))

(defn- tangent-basis
  "Orthonormal tangent basis [t1 t2] at unit surface point `c` (seeded by the
   rotation axis so neighbouring plates' frames are deterministic)."
  [c axis]
  (let [t1 (normalize (sp/cross axis c) (normalize (sp/cross [1.0 0.0 0.0] c) [0.0 1.0 0.0]))]
    [t1 (sp/cross c t1)]))

(defn- plate-boundary
  "Hexagonal boundary polygon (m) for plate `i` of `n` centred at unit point
   `center` on a body of radius `radius-m`: six vertices at a fixed angular
   radius (equal-area packing), rotated by i × golden-angle. Qualitative
   macro geometry — plate boundaries are the seed later slices refine."
  [center axis i n radius-m]
  (let [alpha (* 0.55 (math/sqrt (/ (* 4.0 math/PI) (double n))))
        rot   (* (double i) law/golden-angle-radians)
        [t1 t2] (tangent-basis center axis)]
    (mapv (fn [k]
            (let [phi (+ (* (double k) (/ math/PI 3.0)) rot)
                  off (sp/v+ (sp/v* center (dec (math/cos alpha)))
                             (sp/v* (sp/v+ (sp/v* t1 (math/cos phi))
                                           (sp/v* t2 (math/sin phi)))
                                    (math/sin alpha)))
                  p   (normalize (sp/v+ center off) center)]
              (mapv double (sp/v* p radius-m))))
          (range 6))))

(defn seed-plates
  "Tectonic plates for `candidate` on a body of radius `radius-m` (m).
   Centres are Fibonacci-spiral points pinned to the rotation axis; the
   boundary is a per-plate hexagon; velocity is tangential (axis × centre),
   magnitude the band/gravity-scaled reference speed with alternating sense
   so neighbouring plates shear. `:kind` is the coarse seeder
   classification: `:rocky` alternates :continental/:oceanic, `:icy` crusts
   read :oceanic (ice over liquid), `:mixed` is :mixed. Every record
   validates `law.voxel/plate-schema`."
  [candidate radius-m]
  (let [n           (plate-count candidate)
        t-surface   (surface-temperature candidate)
        phase       (phase-of candidate t-surface)
        [a u v]     (axis-basis candidate)
        speed       (* law/plate-speed-reference-m-per-s (activity-factor candidate))
        mclass      (:material-class candidate)]
    (mapv (fn [i]
            (let [c   (spiral-point i n phase a u v)
                  dir (normalize (sp/cross a c) [1.0 0.0 0.0])
                  s   (* speed (if (even? i) 1.0 -1.0))]
              {:id       (keyword (str "plate-" i))
               :boundary (plate-boundary c a i n radius-m)
               :velocity (mapv double (sp/v* dir s))
               :kind     (case mclass
                           :rocky (if (even? i) :continental :oceanic)
                           :icy   :oceanic
                           :mixed :mixed
                           :mixed)}))
          (range n))))

;; --- Mantle convection -----------------------------------------------------------------

(defn seed-convection-cells
  "Mantle-convection cells for `candidate` on a body of radius `radius-m`
   (m): paired `:upwelling`/`:downwelling` cells on a Fibonacci spiral
   (phase-offset from the plate spiral so the two lattices don't trivially
   coincide). Upwellings seed rifts/hotspots, downwellings seed the
   convergent margins the resource field enriches (design §3). Every record
   validates `law.voxel/mantle-convection-cell-schema`."
  [candidate radius-m]
  (let [n-up    (max 2 (quot (plate-count candidate) 2))
        n       (* 2 n-up)
        phase   (+ (phase-of candidate (surface-temperature candidate))
                   (/ law/golden-angle-radians 2.0))
        [a u v] (axis-basis candidate)
        speed   (* law/convection-speed-reference-m-per-s (activity-factor candidate))
        r-cell  (* radius-m law/convection-cell-radius-fraction)
        depth   (- radius-m (* 2.0 r-cell))]
    (mapv (fn [i]
            (let [c (spiral-point i n phase a u v)]
              {:id     (keyword (str "cell-" (if (< i n-up) "up" "down") "-" (mod i n-up)))
               :center (mapv double (sp/v* c depth))
               :radius (double r-cell)
               :flow   (if (< i n-up) :upwelling :downwelling)
               :speed  (double speed)}))
          (range n))))

;; --- Resource field ----------------------------------------------------------------------

(defn- solid-composition
  "Bulk composition with the gas giants stripped — the condensible inventory
   the resource field is made of. Never empty for a gated candidate (>5%
   non-gas by the handoff gate's H/He ceiling)."
  [candidate]
  (apply dissoc (:bulk-composition candidate) comp/gas-giants))

(defn- scale-key
  "Scale key `k` of map `m` by factor `f` when present; identity otherwise."
  [m k f]
  (if (contains? m k) (update m k * (double f)) m))

(defn- cell-shares
  "Element mass shares (renormalized, gas-free) for a resource cell of
   `kind` — the qualitative §7.4 steer made deterministic: `:convergent`
   margins enrich Fe/Ni (ore), `:hotspot` upwellings enrich sulfur
   (volcanic), `:polar-ice` cells hold the ice-formers, `:background` is
   undifferentiated regolith. Enrichment is RELATIVE (renormalized): it
   redistributes share, never creates mass."
  [kind candidate]
  (let [solid (solid-composition candidate)]
    (case kind
      :convergent (-> solid
                      (scale-key :Fe law/ore-enrichment-factor)
                      (scale-key :Ni law/ore-enrichment-factor)
                      normalize-shares)
      :hotspot    (-> solid
                      (scale-key :S law/hotspot-sulfur-enrichment-factor)
                      normalize-shares)
      :polar-ice  (-> (select-keys solid comp/ice-formers)
                      normalize-shares)
      :background (normalize-shares solid))))

;; Intentional: `kind` is UNUSED-PENDING (see the docstring) — the schema gap is
;; the finding, not the binding. Underscoring it would hide a real design seam.
#_{:clj-kondo/ignore [:unused-binding]}
(defn- resource-cell
  "One resource cell at unit surface point `c` on a body of radius
   `radius-m`, holding `cell-mass` kg with element `shares`: region centre
   sits one cell-radius below the surface (the cell occupies the crust
   band), density-per-element is share × mass / region volume. Validates
   `law.voxel/resource-cell-schema`.

   UNUSED-PENDING `kind` — the caller (`seed-resource-cells`) genuinely
   computes the enrichment kind (`:hotspot`/`:polar-ice`/`:downwelling`/
   `:background`) and it is deliberately dropped here, because
   `law.voxel/resource-cell-schema` (slice 1) carries no `:kind` key.
   `domain.voxel.band/cell-material` therefore re-derives the material from
   element content instead of reading it. Threading `:kind` through the
   schema and retiring that inference is a design change, not a lint fix; see
   `kanban/tasks/static-analysis-unused-pending-convention.md`. Keep the
   parameter named so the seam stays visible at both ends."
  [kind c radius-m cell-mass shares]
  (let [r   (* radius-m law/resource-cell-radius-fraction)
        vol (* (/ 4.0 3.0) math/PI (math/pow r 3.0))]
    {:region {:center (mapv double (sp/v* c (- radius-m r)))
              :radius (double r)}
     :total-mass (double cell-mass)
     :density-per-element
     (into {} (map (fn [[e s]] [e (double (/ (* (double s) cell-mass) vol))]))
           shares)}))

(defn seed-resource-cells
  "Coarse resource field for `candidate` on a body of radius `radius-m`,
   partitioning `shell-mass` (kg — the crust/ice-shell layer's mass) across
   enrichment sites: downwelling (convergent-margin) cells, upwelling
   (hotspot) cells, polar-ice caps (only when the world carries ice-formers
   AND is cold/frozen or icy-class), and background regolith at the
   trailing plate centres. Mass partition is by
   `law.interior/resource-cell-weights`, so cell masses sum to `shell-mass`
   up to double rounding — the resource field conserves crust mass by
   construction. Every record validates `law.voxel/resource-cell-schema`."
  [candidate radius-m shell-mass plates cells]
  (let [upwellings   (filterv #(= :upwelling (:flow %)) cells)
        downwellings (filterv #(= :downwelling (:flow %)) cells)
        [a _ _]      (axis-basis candidate)
        solid        (solid-composition candidate)
        ice-mass     (ice-formers-mass-fraction solid)
        polar?       (and (pos? ice-mass)
                          (or (= :icy (:material-class candidate))
                              (contains? #{:cold :frozen} (:thermal-band candidate))))
        background   (take 3 (reverse plates))
        sites        (concat
                      (map (fn [cell] [:convergent (normalize (:center cell) [1.0 0.0 0.0])])
                           downwellings)
                      (map (fn [cell] [:hotspot (normalize (:center cell) [1.0 0.0 0.0])])
                           upwellings)
                      (when polar?
                        [[:polar-ice a] [:polar-ice (sp/v* a -1.0)]])
                      (map (fn [plate]
                             [:background (normalize (first (:boundary plate)) [1.0 0.0 0.0])])
                           background))
        weight-of    (fn [[kind _]] (get law/resource-cell-weights kind 1.0))
        total-weight (reduce + 0.0 (map weight-of sites))]
    (mapv (fn [[kind c]]
            (let [mass (* (double shell-mass) (/ (weight-of [kind c]) total-weight))]
              (resource-cell kind c radius-m mass (cell-shares kind candidate))))
          sites)))

;; --- Validation (fail loudly, per domain.field precedent) -----------------------------

(defn- validate-field!
  "Throw `ex-info` naming the first emitted record that fails its slice-1
   `law.voxel` schema. A malformed seed would otherwise corrupt every
   regenerate-from-seed load downstream — fail at construction, not at
   replay."
  [field]
  (doseq [layer (:layers field)]
    (when-not (voxel/macro-layer? layer)
      (throw (ex-info "domain.interior: layer fails law.voxel/macro-layer-schema"
                      {:layer layer}))))
  (doseq [plate (:plates field)]
    (when-not (voxel/plate? plate)
      (throw (ex-info "domain.interior: plate fails law.voxel/plate-schema"
                      {:plate plate}))))
  (doseq [cell (:convection field)]
    (when-not (voxel/mantle-convection-cell? cell)
      (throw (ex-info "domain.interior: cell fails law.voxel/mantle-convection-cell-schema"
                      {:cell cell}))))
  (doseq [cell (:resources field)]
    (when-not (voxel/resource-cell? cell)
      (throw (ex-info "domain.interior: resource cell fails law.voxel/resource-cell-schema"
                      {:cell cell}))))
  field)

;; --- Entry point -------------------------------------------------------------------------

(defn seed-field
  "The initial macro geology field for a `:planet-candidate` record — the
   deterministic seed the field-seed + edit-diff save strategy regenerates
   from (design §4, §7.3). Pure: same candidate ⇒ identical field,
   bit-for-bit, by construction (no PRNG; see ns docstring for the seeding
   scheme).

   INPUT CONTRACT: `candidate` is a record that passed the M5 handoff gate
   (`domain.stellar.classifier.candidate/handoff-system`). A `:gaseous` candidate can
   clear that gate but has no solid surface to seed (design §4) — this
   throws rather than inventing a crust. `:mixed` candidates seed with the
   rocky template under the :mixed kind/kind conventions documented on
   `seed-plates`/`layer-template`.

   Output map:
     :seed-version            1 — bump on any layout/model change (diff
                              replay targets one seed version)
     :canonical-voxel-edge-m  law.voxel/canonical-voxel-edge-m — the ONE
                              canonical grid the edit diffs' :offset indexes
     :planet-id               the candidate's :planet-id
     :radius-m :mass-kg :mean-density
                             derived body figure (see ns docstring MISSING DATA)
     :environment             initial-environment — agrees with the future
                              domain.environment FSM's starting state
     :thermal                 {:surface-temperature :core-temperature}
     :layers                  inside-out macro-layer records (mass-balanced)
     :plates :convection :resources
                             law.voxel plate/convection-cell/resource-cell
                             records — every one schema-validated here"
  [candidate]
  (when (= :gaseous (:material-class candidate))
    (throw (ex-info "domain.interior/seed-field: :gaseous candidates have no solid surface to seed (design §4)"
                    {:planet-id (:planet-id candidate)
                     :material-class (:material-class candidate)})))
  (let [t-surface (surface-temperature candidate)
        rho       (mean-density candidate)
        g         (double (:surface-gravity candidate))
        radius-m  (derive-radius-m g rho)
        mass-kg   (derive-mass-kg g radius-m)
        layers    (layer-template candidate radius-m mass-kg t-surface)
        plates    (seed-plates candidate radius-m)
        cells     (seed-convection-cells candidate radius-m)
        resources (seed-resource-cells candidate radius-m
                                       (:mass (last layers)) plates cells)]
    (validate-field!
     {:seed-version           1
      :canonical-voxel-edge-m voxel/canonical-voxel-edge-m
      :planet-id              (:planet-id candidate)
      :radius-m               (double radius-m)
      :mass-kg                (double mass-kg)
      :mean-density           (double rho)
      :environment            (initial-environment candidate)
      :thermal                {:surface-temperature t-surface
                               :core-temperature    (:temperature (first layers))}
      :layers                 layers
      :plates                 plates
      :convection             cells
      :resources              resources})))
