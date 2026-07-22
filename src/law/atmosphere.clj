(ns law.atmosphere
  "Coarse Phase-0 atmosphere-retention classifier constants and schemas
   (M5 handoff Phase 3). Derived from
   docs/research/atmosphere/planetary-atmosphere-retention-classifier.md.

   Standardizes on the RMS thermal-velocity convention
   `v_th = sqrt(3 k_B T / m)`, matching the already-shipped
   `domain.chemistry/can-retain-gas?` (the parent kanban spec's
   `sqrt(2 k_B T / mu)` most-probable-speed convention would shift every
   threshold by ~22% — see the research note §3.4 — so the species
   thresholds below are re-derived against RMS, not copied verbatim from
   the parent card)."
  (:require
   [clojure.math :as math]
   [malli.core :as m]
   [law.stellar.orbital.constants :as constants]))

(def ^:const amu
  "Atomic mass unit (kg)." 1.6605e-27)

(def species-mass
  "Molecular mass (kg) of each tracked atmospheric volatile species."
  {:H2  (* 2.016 amu)
   :He  (* 4.0026 amu)
   :H2O (* 18.015 amu)
   :N2  (* 28.014 amu)
   :CO2 (* 44.01 amu)})

(def ^:const h2-he-mean-mass
  "Mean molecular mass (kg) of a solar-composition H/He primordial envelope
   (X=0.75 H2 + Y=0.25 He by mass), used as the representative species for
   :gaseous bodies' overall atmosphere-class bucket:
   1 / (X/m_H2 + Y/m_He)."
  (/ amu (+ (/ 0.75 2.016) (/ 0.25 4.0026))))

;; Retention thresholds, r = v_esc / v_th(rms). See research note §3.4:
;; H2/He need the higher bar because they are also exposed to the early
;; T Tauri-phase XUV, which drives genuine hydrodynamic loss independent of
;; the Jeans number; heavier secondary volatiles are typically outgassed
;; later, after the high-XUV epoch, so a lower Jeans-only bar is defensible.
(def ^:const h-he-retention-ratio
  "Retention ratio threshold (r = v_esc/v_th, RMS) for H2/He (lambda>36)." 6.0)

(def ^:const heavy-retention-ratio
  "Retention ratio threshold (r = v_esc/v_th, RMS) for H2O/N2/CO2 (lambda>9)." 3.0)

;; Atmosphere-class bucket boundaries (r = v_esc/v_th of the representative
;; species for the body's material class).
(def ^:const thin-ratio-floor 3.0)
(def ^:const substantial-ratio-floor 6.0)
(def ^:const thick-ratio-floor 10.0)

(def atmosphere-class-schema
  "Coarse Phase-0 atmosphere-retention bucket."
  [:enum :none :thin :substantial :thick])

(def atmosphere-class?
  "Predicate: does `value` satisfy `atmosphere-class-schema`?"
  (m/validator atmosphere-class-schema))

(def retained-species-schema
  "Set of atmospheric volatile species a body retains against thermal
   (Jeans) escape."
  [:set [:enum :H2 :He :H2O :N2 :CO2]])

(def retained-species?
  "Predicate: does `value` satisfy `retained-species-schema`?"
  (m/validator retained-species-schema))

;; --- Shared Jeans-escape helpers ---------------------------------------------
;; FLAG 2 reconciliation: the repo previously had two live v_th conventions —
;; `domain.chemistry/can-retain-gas?` used the RMS speed with an inline
;; k_B/amu, while the parent kanban spec's pseudocode used the most-probable
;; speed (sqrt(2 k_B T / m)). RMS and most-probable speed differ by a factor
;; of sqrt(3/2) ≈ 1.22, so reusing the parent spec's 3/6 thresholds against
;; an RMS v_th would silently shift the retention boundary by ~22% (research
;; note §3.4). This namespace is now the SINGLE place both `can-retain-gas?`
;; and `domain.stellar.classifier/atmosphere-class` get `v_esc`/`v_th` from,
;; standardized on RMS with thresholds re-derived against it (`h-he-retention-
;; ratio`, `heavy-retention-ratio` above).

(defn escape-velocity
  "Surface escape velocity (m/s) of a body: v_esc = sqrt(2 G M / R), for
   mass `mass` (kg) and radius `radius` (m)."
  [mass radius]
  (math/sqrt (/ (* 2.0 constants/G (double mass)) (double radius))))

(defn thermal-velocity-rms
  "RMS thermal speed (m/s) of a species of molecular mass `species-mass` (kg)
   at temperature `temperature` (K): v_th = sqrt(3 k_B T / m)."
  [species-mass temperature]
  (math/sqrt (/ (* 3.0 constants/k-B (double temperature)) (double species-mass))))

(defn retention-ratio
  "Jeans retention ratio r = v_esc/v_th(rms) for a body of `mass`/`radius`
   at `temperature`, for a species of mass `species-mass` (kg). r>1 means
   escape velocity exceeds thermal speed; the bucket/species thresholds
   above translate this into a coarse verdict.

   `temperature<=0` (e.g. an unignited protostar's zero-luminosity
   equilibrium temperature, or any not-yet-physically-resolved body) gives
   v_th=0, i.e. an unconditionally-retained atmosphere in the limit — handled
   explicitly here as `##Inf` rather than falling through to `/`, because
   Clojure's generic (boxed) divide throws `ArithmeticException` on an exact
   zero divisor even for `Double` operands, unlike raw primitive double
   division (`x/0.0` at the bytecode level, which IEEE-754 defines as
   infinity)."
  [mass radius temperature species-mass]
  (let [v-th (thermal-velocity-rms species-mass temperature)]
    (if (zero? v-th)
      Double/POSITIVE_INFINITY
      (/ (escape-velocity mass radius) v-th))))
