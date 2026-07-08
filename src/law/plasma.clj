(ns law.plasma
  "Contracts and schemas for stellar winds, atmospheric escape, and space weather.

   Derived from: docs/research/phase1-radiation-plasma-truth.md §4-6
   Stellar winds are ionized plasma outflows driven by coronal pressure gradients
   and radiation. They carry ram pressure and magnetic fields that couple to
   planetary magnetospheres. Close-in planets experience XUV-driven atmospheric
   escape in energy-limited or recombination-limited regimes.

   All units: SI."
  (:require
   [clojure.math :as math] [law.contract :as contract]))

;; --- Physical constants ---

(def ^:const solar-mass-loss-rate
  "Solar wind mass loss rate (kg/s). ~2e-14 M_sun/yr."
  6.3e9)

(def ^:const solar-wind-speed
  "Solar wind terminal speed at 1 AU (m/s). ~400 km/s."
  4.0e5)

(def ^:const solar-alfven-radius
  "Solar Alfvén radius (m). ~10-20 R_sun where wind becomes super-Alfvénic."
  1.0e10)

;; --- XUV escape regime constants ---
;; Source: Murray-Clay+2009, Owen & Alvarez 2016, Lampón+2021
;; The transition between energy-limited and recombination-limited escape
;; is controlled by R = t_rec / t_flow = c_s / (n_e · α_B · R_p)

(def ^:const case-b-recombination
  "Case B recombination coefficient for hydrogen (cm³/s).
   α_B ≈ 2.6e-13 cm³/s at T ~ 10⁴ K."
  2.6e-13)

(def ^:const xuv-sound-speed
  "Sound speed in XUV-heated atmosphere (m/s).
   c_s ~ 10 km/s for T ~ 10⁴ K, mean molecular weight ~ 1 amu."
  1.0e4)

(def ^:const critical-xuv-flux-cgs
  "Critical XUV flux for regime transition (erg/cm²/s).
   Below this: energy-limited (Ṁ ∝ F^0.9).
   Above this: recombination-limited (Ṁ ∝ F^0.6).
   Murray-Clay+2009 for hot Jupiters. Shifts with planet parameters."
  1.0e4)

(def ^:const critical-xuv-flux-si
  "Critical XUV flux for regime transition (W/m²).
   10⁴ erg/cm²/s = 0.1 W/m²."
  0.1)

;; --- Validation predicates ---

(defn positive-si?
  "True if x is a positive finite number (generic SI quantity)."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (pos? (double x))))

(defn non-negative-si?
  "True if x is a non-negative finite number."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)))

(defn ionization-fraction?
  "True if x is a valid ionization fraction: 0 (neutral) to 1 (fully ionized)."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)
       (<= (double x) 1.0)))

(defn escape-regime?
  "True if k is a valid atmospheric escape regime keyword."
  [k]
  (contains? #{:energy-limited :recombination-limited :blow-off} k))

(defn event-kind?
  "True if k is a valid space-weather event type."
  [k]
  (contains? #{:flare :cme :supernova :grb} k))

;; --- Stellar wind schemas ---
;; From: docs/research/phase1-radiation-plasma-truth.md §4.1, §5.1-5.2

(def wind-profile-schema
  "Characteristics of a star's steady stellar wind.
   Derived from coronal temperature and SED XUV/EUV bands."
  {:wind/dot-m          positive-si?    ;; kg/s — mass-loss rate Ṁ
   :wind/v-escape       positive-si?    ;; m/s — launch speed v_w (Parker terminal)
   :wind/ram-pressure   positive-si?    ;; Pa — reference ram pressure at reference-r
   :wind/reference-r    positive-si?    ;; m — radius where ram-pressure is defined
   :wind/luminosity-xuv non-negative-si? ;; W — XUV luminosity driving the wind
   :wind/ionization     ionization-fraction?  ;; 0..1 — wind ionization state
   :wind/corona-t       positive-si?})  ;; K — coronal temperature

(def plasma-wind-schema
  "Schema for a stellar wind parcel treated as ionized plasma.
   Replaces the neutral-gas wind parcels of Phase 0.
   Carries ionization state, magnetic field, and ram pressure."
  {:id                    (some-fn uuid? integer?)
   :origin-star           (some-fn uuid? integer?)
   :position              vector?          ;; [x y z] m
   :velocity              vector?          ;; [vx vy vz] m/s
   :mass                  positive-si?     ;; kg
   :radius                positive-si?     ;; m
   :density               positive-si?     ;; kg/m³
   :temperature           positive-si?     ;; K
   :ionization-fraction   ionization-fraction?  ;; 0..1
   :b-field               vector?          ;; [Bx By Bz] Tesla
   :ram-pressure          non-negative-si?})  ;; Pascals

;; --- Atmospheric escape schemas ---
;; From: docs/research/phase1-radiation-plasma-truth.md §4.2

(def atmosphere-escape-schema
  "Planetary atmospheric escape state driven by stellar XUV irradiation.
   Updated each tick by the xuv-atmospheric-escape-system."
  {:regime         escape-regime?      ;; :energy-limited or :recombination-limited
   :xuv-flux       non-negative-si?    ;; W/m² — incident XUV at the planet
   :mass-loss-rate non-negative-si?})  ;; kg/s — atmospheric escape rate

;; --- Space weather event schemas ---
;; From: docs/research/phase1-radiation-plasma-truth.md §6

(def event-source-schema
  "A transient space-weather event: flare, CME, supernova, etc.
   Carries a payload of event-specific parameters."
  {:kind    event-kind?
   :payload map?})

(def flare-payload-schema
  "Payload for a stellar flare event.
   Transient XUV enhancement with exponential decay."
  {:energy         positive-si?       ;; Joules — total flare energy
   :xuv-boost      positive-si?       ;; dimensionless — multiplier on XUV bands
   :duration       positive-si?       ;; seconds — decay timescale
   :origin-star    (some-fn uuid? integer?)})

(def cme-payload-schema
  "Payload for a coronal mass ejection.
   Dense, magnetized plasma cloud propagating outward."
  {:mass           positive-si?       ;; kg — CME mass
   :speed          positive-si?       ;; m/s — CME propagation speed
   :b-field        vector?            ;; [Bx By Bz] Tesla — carried field
   :origin-star    (some-fn uuid? integer?)})

;; --- Contracts ---

(def wind-profile-contract
  (contract/->contract
   {:id       ::wind-profile
    :shape-id ::stellar-wind
    :kind     :type
    :schema   wind-profile-schema
    :name     "Wind Profile"
    :description "Stellar wind characteristics (mass-loss rate, launch speed, ram pressure, ionization, coronal temperature) derived from coronal properties."}))

(def plasma-wind-contract
  (contract/->contract
   {:id       ::plasma-wind
    :shape-id ::wind-parcel
    :kind     :type
    :schema   plasma-wind-schema
    :name     "Plasma Wind Parcel"
    :description "Ionized plasma parcel in stellar wind with ram pressure and B-field."}))

(def atmosphere-escape-contract
  (contract/->contract
   {:id       ::atmosphere-escape
    :shape-id ::planetary-escape
    :kind     :type
    :schema   atmosphere-escape-schema
    :name     "Atmosphere Escape"
    :description "Planetary atmospheric escape regime and mass loss from XUV."}))

(def event-source-contract
  (contract/->contract
   {:id       ::event-source
    :shape-id ::space-weather
    :kind     :type
    :schema   event-source-schema
    :name     "Event Source"
    :description "Transient space-weather event (flare, CME, etc)."}))

;; --- Wind physics helpers ---

(defn parker-mass-loss
  "Parker solar wind mass loss rate estimate: Ṁ ~ ρ_corona · v_wind · 4π R².
   Simplified scaling: Ṁ ∝ L / (v_esc · c) with a tunable scale factor k.
   Returns kg/s."
  [luminosity escape-velocity k]
  (let [L   (double (or luminosity 0.0))
        v-e (double (or escape-velocity 0.0))
        c   2.99792458e8]
    (if (and (pos? L) (pos? v-e) (pos? k))
      (/ (* (double k) L) (* v-e c))
      0.0)))

(defn ram-pressure
  "Wind ram pressure P = ρ v² at distance r from the star.
   For a spherical wind: P = Ṁ v / (4π r²).
   Returns Pascals."
  [mass-loss-rate wind-speed distance]
  (let [mdot (double (or mass-loss-rate 0.0))
        v    (double (or wind-speed 0.0))
        r    (double (or distance 0.0))]
    (if (and (pos? mdot) (pos? v) (pos? r))
      (/ (* mdot v) (* 4.0 math/PI r r))
      0.0)))

(defn xuv-flux-at
  "XUV flux (W/m²) at distance r from a star with XUV luminosity L_xuv.
   F = L_xuv / (4π r²)."
  [xuv-luminosity distance]
  (let [L (double (or xuv-luminosity 0.0))
        r (double (or distance 0.0))]
    (if (and (pos? L) (pos? r))
      (/ L (* 4.0 math/PI r r))
      0.0)))

(defn energy-limited-escape
  "Energy-limited atmospheric mass loss rate: Ṁ = ε π R_p³ XUV / (G M_p K_tide).
   Simplified: Ṁ ∝ F_xuv · R_p³ / (M_p · binding-factor).
   Returns kg/s. Valid when F_xuv is moderate (not in blow-off regime)."
  [xuv-flux planet-radius planet-mass heating-efficiency]
  (let [F   (double (or xuv-flux 0.0))
        Rp  (double (or planet-radius 0.0))
        Mp  (double (or planet-mass 0.0))
        eps (double (or heating-efficiency 0.15))
        G   6.674e-11]
    (if (and (pos? F) (pos? Rp) (pos? Mp))
      (/ (* eps math/PI (math/pow Rp 3) F) (* G Mp))
      0.0)))

;; --- Regime selector ---
;; Source: Murray-Clay+2009, Owen & Alvarez 2016, Lampón+2021
;; The dimensionless ratio R = t_rec / t_flow = c_s / (n_e · α_B · R_p)
;; determines which regime applies.

(defn recombination-timescale
  "Recombination timescale t_rec = 1 / (n_e · α_B). Seconds.
   n_e is electron density at the sonic point (m⁻³).
   α_B is the Case B recombination coefficient."
  [n-electron]
  (let [ne (double (or n-electron 0.0))]
    (if (pos? ne)
      (/ 1.0 (* ne case-b-recombination 1e-6)) ;; convert α_B from cm³/s to m³/s
      Double/POSITIVE_INFINITY)))

(defn flow-timescale
  "Flow/advection timescale t_flow = R_p / c_s. Seconds.
   How long a fluid element takes to cross one planetary radius at sound speed."
  [planet-radius]
  (let [Rp (double (or planet-radius 0.0))]
    (if (pos? Rp)
      (/ Rp xuv-sound-speed)
      Double/POSITIVE_INFINITY)))

(defn escape-regime
  "Determine the atmospheric escape regime from incident XUV flux and
   planet properties. Returns :energy-limited, :recombination-limited,
   or :blow-off.

   The transition is controlled by R = t_rec / t_flow:
   - R >> 1: recombination is slow, all absorbed XUV → escape (energy-limited)
   - R << 1: recombination radiates away energy (recombination-limited)
   - F_xuv very high: photon-limited (every ionizing photon strips an atom)

   When electron density is known, uses the physical R criterion.
   Otherwise falls back to the critical XUV flux threshold."
  ([xuv-flux planet-radius]
   (escape-regime xuv-flux planet-radius nil))
  ([xuv-flux planet-radius n-electron]
   (let [F   (double (or xuv-flux 0.0))
         Rp  (double (or planet-radius 0.0))]
     (if (and n-electron (pos? Rp))
       ;; Physical criterion: R = t_rec / t_flow
       (let [t-r (recombination-timescale n-electron)
             t-f (flow-timescale Rp)
             R   (if (pos? t-f) (/ t-r t-f) 0.0)]
          ;; Intentional: both the strong (R≥10) and marginal (R≥1) energy-limited
          ;; regimes return the same classification; the split is documentary.
         #_{:splint/disable [lint/identical-branches]}
         (cond
           (>= R 10.0)  :energy-limited      ;; recombination ≪ advection
           (>= R 1.0)   :energy-limited      ;; marginally energy-limited
           (>= R 0.1)   :recombination-limited
           :else         :blow-off))          ;; R ≪ 1, photon-limited
       ;; Fallback: flux threshold (Murray-Clay+2009)
       (cond
         (< F critical-xuv-flux-si)           :energy-limited
         (< F (* 10.0 critical-xuv-flux-si))  :recombination-limited
         :else                                 :blow-off)))))
