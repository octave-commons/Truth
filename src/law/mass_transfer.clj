(ns law.mass-transfer
  "Contracts and physics helpers for gradual mass transfer.

   Covers Bondi–Hoyle–Lyttleton sink accretion and Roche-lobe overflow.
   All functions are pure; all constants live here so `domain/` can compute
   without embedding magic numbers.

   Sources:
   - docs/research/physics/rate-limited-accretion-mass-transfer.md
   - docs/specs/gradual-mass-transfer-realspec.md",
  (:require
   [law.contract :as contract]
   [law.stellar  :as law]
   [malli.core   :as m]))

;; --- Physical constants -------------------------------------------------------

(def ^:const earth-mass
  "Earth mass in kg."
  5.972e24)

(def ^:const jupiter-mass
  "Jupiter mass in kg."
  1.898e27)

(def ^:const default-accretion-fraction-cap
  "Maximum fraction of available donor gas mass that may move to a sink in one
   tick. Krumholz+2004 used 25% per cell per step for stability."
  0.25)

(def ^:const default-donor-fraction-cap
  "Maximum fraction of an individual donor parcel that may be debited in one
   tick. Independent of the zone-level cap above."
  0.25)

(def ^:const default-bondi-lambda
  "Bondi eigenvalue for an isothermal gas (λ = e^{3/2}/4 ≈ 1.120). Used by
   Krumholz+2004; our simplified BHL formula bakes the λ² into the velocity
   denominator, so this is retained for compatibility checks."
  1.12)

(def ^:const default-softening-factor
  "Accretion radius is clamped to at least this fraction of the donor smoothing
   length / grid scale, so the sink always resolves the gas it is eating."
  0.5)

(def ^:const binding-energy-tolerance
  "A parcel is considered bound to a sink if its specific energy is below this
   positive threshold (m²/s²). Small positive value avoids noise at marginal
   binding."
  1.0)

;; --- Roche-lobe overflow constants -------------------------------------------

(def ^:const rlof-pols-A
  "Order-unity prefactor in the Pols scaling for mass-transfer rate."
  10.0)

(def ^:const ritter-isothermal-prefactor
  "Scaled prefactor for the Ritter (1988) isothermal overflow rate."
  (* 2.0 Math/PI (Math/sqrt Math/E)))

(def ^:const default-accreted-fraction
  "Fraction of Roche-lobe overflow mass that is accreted by the companion in
   the conservative limit."
  1.0)

;; --- BHL accretion ------------------------------------------------------------

(defn bondi-radius
  "Bondi radius R_B = 2 G M / c_s² (m)."
  [M c-s]
  (let [M   (double (or M 0.0))
        c-s (double (or c-s 0.0))]
    (if (and (pos? M) (pos? c-s))
      (/ (* 2.0 law/G M) (* c-s c-s))
      0.0)))

(defn capture-radius
  "Gravitational capture radius R_acc = 2 G M / (c_s² + v_rel²) (m)."
  [M c-s v-rel]
  (let [M     (double (or M 0.0))
        c-s   (double (or c-s 0.0))
        v-rel (double (or v-rel 0.0))
        denom (+ (* c-s c-s) (* v-rel v-rel))]
    (if (and (pos? M) (pos? denom))
      (/ (* 2.0 law/G M) denom)
      0.0)))

(defn bhl-accretion-rate
  "Bondi–Hoyle–Lyttleton accretion rate (kg/s) for a point-mass sink.

       Ṁ = 4π G² M² ρ_∞ / (c_s² + v_rel²)^{3/2}

   Returns 0 if any required input is non-positive."
  [M rho-inf c-s v-rel]
  (let [M       (double (or M 0.0))
        rho-inf (double (or rho-inf 0.0))
        c-s     (double (or c-s 0.0))
        v-rel   (double (or v-rel 0.0))
        denom   (Math/pow (+ (* c-s c-s) (* v-rel v-rel)) 1.5)]
    (if (and (pos? M) (pos? rho-inf) (pos? denom))
      (/ (* 4.0 Math/PI law/G law/G M M rho-inf) denom)
      0.0)))

(defn accretion-regime
  "Classify the BHL regime from the ratio v_rel / c_s."
  [c-s v-rel]
  (let [c-s   (double (or c-s 0.0))
        v-rel (double (or v-rel 0.0))
        ratio (if (pos? c-s) (/ v-rel c-s) 0.0)]
    (cond
      (< ratio 0.5) :subsonic
      (> ratio 2.0) :supersonic
      :else         :transonic)))

(defn capped-delta-mass
  "Apply the three standard caps to a proposed mass transfer.

   Returns min( rate·dt , f_zone·M_gas , f_donor·M_donor )."
  [{:keys [dot-m dt gas-mass donor-mass
           accretion-fraction-cap donor-fraction-cap]}]
  (let [dot-m                (double (or dot-m 0.0))
        dt                   (double (or dt 0.0))
        gas-mass             (double (or gas-mass 0.0))
        donor-mass           (double (or donor-mass 0.0))
        accretion-fraction-cap (double (or accretion-fraction-cap default-accretion-fraction-cap))
        donor-fraction-cap     (double (or donor-fraction-cap default-donor-fraction-cap))]
    (max 0.0 (min (* dot-m dt)
                  (* accretion-fraction-cap gas-mass)
                  (* donor-fraction-cap donor-mass)))))

(defn binding-energy
  "Specific orbital energy (m²/s²) of a test particle at distance r from a sink
   of mass M, with relative speed v_rel. Negative means bound."
  [M r v-rel]
  (let [M     (double (or M 0.0))
        r     (double (or r 0.0))
        v-rel (double (or v-rel 0.0))]
    (if (and (pos? M) (pos? r))
      (- (* 0.5 v-rel v-rel) (/ (* law/G M) r))
      0.0)))

(defn bound-and-infalling?
  "True if the parcel is bound to the sink and moving toward it.

   `v-rad` is the radial velocity (positive = moving away)."
  [M r v-rel v-rad]
  (and (< (binding-energy M r v-rel) binding-energy-tolerance)
       (< v-rad 0.0)))

;; --- Roche-lobe overflow ------------------------------------------------------

(defn roche-lobe-radius
  "Eggleton (1983) volume-equivalent Roche-lobe radius for the donor of mass
   M_d around accretor M_a at separation a. Returns meters.

       R_L / a = 0.49 q^{2/3} / (0.6 q^{2/3} + ln(1 + q^{1/3}))

   with q = M_d / M_a."
  [a M-donor M-accretor]
  (let [a        (double (or a 0.0))
        M-donor  (double (or M-donor 0.0))
        M-accretor (double (or M-accretor 0.0))]
    (if (and (pos? a) (pos? M-donor) (pos? M-accretor))
      (let [q     (/ M-donor M-accretor)
            q13   (Math/pow q (/ 1.0 3.0))
            q23   (* q13 q13)
            frac  (/ (* 0.49 q23)
                     (+ (* 0.6 q23) (Math/log1p q13)))]
        (* a frac))
      0.0)))

(defn roche-overfilling
  "Fractional overfilling δ = (R_donor - R_L) / R_L. Positive means overflow."
  [R-donor R-L]
  (let [R-donor (double (or R-donor 0.0))
        R-L     (double (or R-L 0.0))]
    (if (pos? R-L)
      (/ (- R-donor R-L) R-L)
      0.0)))

(defn orbital-period
  "Keplerian orbital period P = 2π √(a³ / G(M_d+M_a)) (s)."
  [a M-donor M-accretor]
  (let [a          (double (or a 0.0))
        M-donor    (double (or M-donor 0.0))
        M-accretor (double (or M-accretor 0.0))
        M-total    (+ M-donor M-accretor)]
    (if (and (pos? a) (pos? M-total))
      (* 2.0 Math/PI (Math/sqrt (/ (* a a a) (* law/G M-total))))
      0.0)))

(defn ritter-isothermal-rate
  "Ritter (1988) isothermal Roche-lobe overflow rate (kg/s), scaled form.

   Uses the Pols approximation as a practical implementation:

       Ṁ ≈ -A · M_d / P · δ³

   where A ~ 10, P is the orbital period, and δ is the fractional overfilling.

   This is the default Phase 0 branch; the full Ritter integral with donor
   envelope structure is deferred to docs/specs/roche-lobe-envelope-physics.

   Returns a non-positive number (donor loses mass)."
  [M-donor a R-donor R-L]
  (let [M-donor  (double (or M-donor 0.0))
        a        (double (or a 0.0))
        R-donor  (double (or R-donor 0.0))
        R-L      (double (or R-L 0.0))
        delta    (roche-overfilling R-donor R-L)]
    (if (and (pos? M-donor) (pos? a) (pos? delta))
      (- (* rlof-pols-A (/ M-donor (orbital-period a M-donor 1.0))
            (* delta delta delta)))
      0.0)))

;; --- Conservation helpers -----------------------------------------------------

(defn momentum-of-mass
  "Linear momentum p = m · v (vector)."
  [mass velocity]
  (let [m (double (or mass 0.0))]
    (mapv #(* m (double %)) velocity)))

(defn add-momentum
  "Component-wise vector addition of two momenta."
  [p1 p2]
  (mapv + p1 p2))

(defn scale-momentum
  "Scale a momentum vector by scalar s."
  [s p]
  (mapv #(* (double s) (double %)) p))

;; --- Schemas ------------------------------------------------------------------

(def accretion-radius-schema
  "Capture radius and ambient conditions for a sink."
  [:map
   [:sink/r-acc number?]
   [:sink/r-bondi number?]
   [:sink/ambient-density number?]
   [:sink/ambient-cs number?]
   [:sink/relative-velocity number?]])

(def accretion-rate-schema
  "Mass flux and regime for a sink."
  [:map
   [:sink/dot-m number?]
   [:sink/dot-m-this-tick number?]
   [:sink/efficiency number?]
   [:sink/regime keyword?]])

(def mass-flux-schema
  "Shared influence for all gradual mass transfer.

   Stored as a vector of event maps on the emitting entity (sink or binary-pair).
   The integrator reads the vector and applies each event to the referenced
   entities, so c/mass remains single-writer."
  [:vector
   [:map
    [:mass-flux/kind [:enum :bhl :rlof]]
    [:mass-flux/delta-m number?]
    [:mass-flux/delta-p [:vector number?]]
    [:mass-flux/tick int?]
    [:mass-flux/sink-id {:optional true} int?]
    [:mass-flux/donor-id {:optional true} int?]
    [:mass-flux/binary-pair-id {:optional true} int?]
    [:mass-flux/donor-eid {:optional true} int?]
    [:mass-flux/accretor-eid {:optional true} int?]
    [:mass-flux/delta-l {:optional true} [:vector number?]]
    [:mass-flux/accretion-zone-density {:optional true} number?]
    [:mass-flux/roche-overfilling {:optional true} number?]
    [:mass-flux/accreted-fraction {:optional true} number?]]])

(def binary-pair-schema
  "A relation entity linking a donor and an accretor."
  [:map
   [:binary-pair/donor int?]
   [:binary-pair/accretor int?]
   [:orbit/semi-major-axis number?]
   [:orbit/eccentricity number?]])

(def roche-lobe-schema
  "Roche-lobe geometry and overflow state."
  [:map
   [:roche-lobe/radius number?]
   [:roche-lobe/overfilling number?]
   [:roche-lobe/overflow? boolean?]])

(def mass-transfer-rate-schema
  "Signed rate and accreted fraction for RLOF."
  [:map
   [:mass-transfer/rate number?]
   [:mass-transfer/accreted-fraction number?]])

(def accretion-radius-contract
  "Capture radius and ambient conditions for a sink."
  (contract/->contract
   {:id       ::accretion-radius
    :shape-id ::accretion-radius
    :kind     :type
    :schema   accretion-radius-schema}))

(def accretion-rate-contract
  "Mass flux and regime for a sink."
  (contract/->contract
   {:id       ::accretion-rate
    :shape-id ::accretion-rate
    :kind     :type
    :schema   accretion-rate-schema}))

(def mass-flux-contract
  "Shared influence for all gradual mass transfer."
  (contract/->contract
   {:id       ::mass-flux
    :shape-id ::mass-flux
    :kind     :type
    :schema   mass-flux-schema}))

(def binary-pair-contract
  "A relation entity linking a donor and an accretor."
  (contract/->contract
   {:id       ::binary-pair
    :shape-id ::binary-pair
    :kind     :type
    :schema   binary-pair-schema}))

(def roche-lobe-contract
  "Roche-lobe geometry and overflow state."
  (contract/->contract
   {:id       ::roche-lobe
    :shape-id ::roche-lobe
    :kind     :type
    :schema   roche-lobe-schema}))

(def mass-transfer-rate-contract
  "Signed rate and accreted fraction for RLOF."
  (contract/->contract
   {:id       ::mass-transfer-rate
    :shape-id ::mass-transfer-rate
    :kind     :type
    :schema   mass-transfer-rate-schema}))

(defn validate-accretion-radius [x] (contract/validate accretion-radius-contract x))
(defn validate-accretion-rate   [x] (contract/validate accretion-rate-contract   x))
(defn validate-mass-flux        [x] (contract/validate mass-flux-contract        x))
(defn validate-binary-pair      [x] (contract/validate binary-pair-contract      x))
(defn validate-roche-lobe       [x] (contract/validate roche-lobe-contract       x))
(defn validate-mass-transfer-rate [x] (contract/validate mass-transfer-rate-contract x))
