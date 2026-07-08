(ns infra.render.color
  "Body colour, material, and temperature mapping for the renderer.
   Everything here is pure: no OpenGL, no world mutation."
  (:require
   [clojure.math :as math]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.chemistry :as chemistry]
   [law.stellar :as law]))

(defn body-color
  "Fallback base colour for a body kind when no physical colour is available."
  [kind]
  (case kind
    :body/star   [1.0 0.9 0.2]
    :body/planet [0.2 0.5 1.0]
    :body/debris [0.6 0.6 0.6]
    :body/moon   [0.8 0.8 0.8]
    :body/person [1.0 0.2 0.2]
    [0.7 0.7 0.7]))

(def regime-tint
  "Per-regime colour multiplier (kept for regime-view tooling and tests)."
  {:gravitationally-unstable [1.30 0.85 0.65]
   :mhd-dominated            [0.70 0.75 1.35]
   :gravity-hydro            [1.00 1.00 1.00]})

(defn tint-color
  "Multiply [r g b] by a regime tint, clamped to [0,1]."
  [color regime]
  (let [t (get regime-tint regime [1.0 1.0 1.0])]
    (mapv (fn [c m] (max 0.0 (min 1.0 (* (double c) (double m))))) color t)))

(def ^:private temp-stops
  ;; [x r g b] — colour ramp keyed on normalized log-temperature
  ;; 10 K … 1e8 K covers diffuse gas through hot stars
  [[0.0  0.20 0.15 0.55]    ; ~10 K  cold diffuse gas: dim blue-violet
   [0.18 0.55 0.35 0.75]    ; ~100 K warming: violet
   [0.35 0.90 0.30 0.55]    ; ~1e3 K: magenta
   [0.52 1.0  0.55 0.20]    ; ~1e4 K: orange
   [0.68 1.0  0.90 0.55]    ; ~1e5 K: yellow-white
   [0.82 1.0  0.95 0.90]    ; ~1e6 K: white
   [0.92 0.80 0.85 1.0]     ; ~1e7 K: pale blue
   [1.0  0.55 0.70 1.0]])   ; ~1e8 K: deep blue (stellar core)

(defn temp-color
  "Temperature (K) → RGB on a cold-violet → warm → white → hot-blue ramp,
   log-scaled over ~10 K … 1e8 K. Stars live at the blue-white end, hot debris
   in orange-yellow, cold nebula gas in violet-blue."
  [t]
  (let [x (max 0.0 (min 1.0 (/ (- (math/log10 (max 1.0 (double (or t 10.0)))) 1.0) 7.0)))]
    (loop [stops temp-stops]
      (let [[x0 r0 g0 b0] (first stops)
            nxt           (second stops)]
        (cond
          (nil? nxt)          [r0 g0 b0]
          (> x (first nxt))   (recur (rest stops))
          :else (let [[x1 r1 g1 b1] nxt
                      f (/ (- x x0) (max 1e-9 (- x1 x0)))]
                  [(+ r0 (* (- r1 r0) f))
                   (+ g0 (* (- g1 g0) f))
                   (+ b0 (* (- b1 b0) f))]))))))

(def ^:private disk-temp-stops
  ;; [x r g b] — warm disk ramp: cool outer disk is green, warming inward
  ;; through yellow-green and orange to orange-brown and red at the hot inner
  ;; edge.  Log-scaled over ~50 K … 2,000 K, the range where a protoplanetary
  ;; disk actually glows thermally.
  [[0.0  0.25 0.65 0.15]    ; ~50 K: bright green
   [0.25 0.75 0.85 0.15]    ; ~200 K: yellow-green
   [0.50 1.00 0.55 0.10]    ; ~500 K: orange
   [0.75 0.95 0.45 0.10]    ; ~1000 K: orange-brown
   [1.0  0.90 0.20 0.08]])  ; ~2000 K: red

(defn disk-temp-color
  "Temperature (K) → RGB for rotationally-supported disc material: a warm
   green → orange → brown → red ramp keyed on log-temperature over the
   ~50 K … 2,000 K disk range.  This is deliberately distinct from the
   nebula/stellar `temp-color` ramp so the disk reads as its own phase of
   matter even when it is embedded in the surrounding nebula."
  [t]
  (let [log-t (math/log10 (max 50.0 (double (or t 50.0))))
        x     (max 0.0 (min 1.0 (/ (- log-t 1.7) 1.6)))]
    (loop [stops disk-temp-stops]
      (let [[x0 r0 g0 b0] (first stops)
            nxt           (second stops)]
        (cond
          (nil? nxt)          [r0 g0 b0]
          (> x (first nxt))   (recur (rest stops))
          :else (let [[x1 r1 g1 b1] nxt
                      f (/ (- x x0) (max 1e-9 (- x1 x0)))]
                  [(+ r0 (* (- r1 r0) f))
                   (+ g0 (* (- g1 g0) f))
                   (+ b0 (* (- b1 b0) f))]))))))

(def ^:private gas-temp-stops
  ;; [x r g b] — gas ramp.  Cold nebula gas reads as blue-violet,
  ;; warming through magenta to orange.  The hot end is clamped to
  ;; red-orange so ionized gas near a star keeps its colour instead
  ;; of bleaching to white/blue-white.  Stars still use the full
  ;; `temp-color` ramp, so they remain white/blue sparks.
  [[0.0  0.15 0.10 0.45]    ; ~10 K: cold diffuse gas, blue-violet
   [0.18 0.45 0.25 0.75]    ; ~100 K: warming violet
   [0.35 0.85 0.25 0.55]    ; ~1e3 K: magenta
   [0.52 1.00 0.50 0.18]    ; ~1e4 K: orange (cap)
   [0.64 0.98 0.40 0.10]    ; ~1e5 K: deeper orange
   [0.76 0.95 0.30 0.08]])  ; ~1e6 K+: red-orange

(defn gas-temp-color
  "Temperature (K) → RGB for volumetric gas.  Cold nebula gas reads as blue-violet,
   warming through magenta to orange; the hot end is clamped to red-orange so
   ionized gas near a star does not bleach to white/blue-white.  Stars themselves
   still use the full `temp-color` ramp, so they render as white/blue sparks."
  [t]
  (let [x (max 0.0 (min 0.76 (/ (- (math/log10 (max 1.0 (double (or t 10.0)))) 1.0) 7.0)))]
    (loop [stops gas-temp-stops]
      (let [[x0 r0 g0 b0] (first stops)
            nxt           (second stops)]
        (cond
          (nil? nxt)          [r0 g0 b0]
          (> x (first nxt))   (recur (rest stops))
          :else (let [[x1 r1 g1 b1] nxt
                      f (/ (- x x0) (max 1e-9 (- x1 x0)))]
                  [(+ r0 (* (- r1 r0) f))
                   (+ g0 (* (- g1 g0) f))
                   (+ b0 (* (- b1 b0) f))]))))))

(defn body-brightness
  "Perceived brightness multiplier for a resolved body. Stars scale with
   luminosity, protostars with temperature, and planets/debris are dim. Used for
   both the emissive glow in the body shader and the sprite proxy size so stars
   remain visible from a distance."
  [world eid state]
  (case state
    :star (let [lum (double (or (ecs/get-component world eid c/luminosity) 0.0))]
            (if (pos? lum)
              (max 0.5 (min 5.0 (+ 1.0 (* 0.5 (math/log10 (/ lum law/solar-luminosity))))))
              0.5))
    :protostar (let [t (double (or (ecs/get-component world eid c/temperature) 10.0))]
                 (max 0.5 (min 2.0 (+ 0.6 (* 0.4 (/ (math/log10 (max 10.0 t)) 7.0))))))
    0.3))

(defn composition->material-color
  "Base material colour from the element-resolved composition at temperature
   `temp` (K). Derives the {:gas :rock :metal :ice} bulk categories via
   `domain.chemistry/bulk-categories` (the condensation partition) and blends
   category colours: H/He gas reads pale tan, rock warm grey-brown, metal dark
   grey, ice cold blue-white. Uncategorised condensate (frozen H/He/Ne) reads as
   pale gas so the fractions always sum to 1. A cold Fe/Si world reads rock; a
   primordial parcel reads tan; an ice-rich world shifts blue-white."
  [compose temp]
  (let [{:keys [gas rock metal ice]} (chemistry/bulk-categories (or compose {})
                                                                (double (or temp 10.0)))
        ;; frozen gas-formers (H/He/Ne) belong to no solid category; fold the
        ;; unaccounted remainder into gas so pale material never renders black.
        gas     (+ (double gas) (max 0.0 (- 1.0 (+ (double gas) (double rock)
                                                   (double metal) (double ice)))))
        rock-c  [0.62 0.50 0.40]
        metal-c [0.42 0.40 0.40]
        ice-c   [0.75 0.85 0.95]
        gas-c   [0.85 0.80 0.62]]
    (mapv (fn [i] (+ (* gas (nth gas-c i))
                     (* (double rock) (nth rock-c i))
                     (* (double metal) (nth metal-c i))
                     (* (double ice) (nth ice-c i))))
          [0 1 2])))

(defn body-render-color
  "Surface colour of a resolved body: its composition (material) colour when
   cold, crossfading to its thermal blackbody colour as it heats past ~1000 K.
   A cold rocky world shows rock; an incandescent one glows by temperature."
  [temp compose]
  (let [mat (composition->material-color compose temp)
        th  (temp-color temp)
        t   (double (or temp 10.0))
        f   (max 0.0 (min 1.0 (/ (- (math/log10 (max 1.0 t)) 2.7) 2.3)))]
    (mapv (fn [m h] (+ (* (- 1.0 f) m) (* f h))) mat th)))

;; --- Procedural surface appearance ------------------------------------------

(def ^:const surface-flat 0)
(def ^:const surface-star 1)
(def ^:const surface-gas-giant 2)
(def ^:const surface-ice-giant 3)
(def ^:const surface-terrestrial 4)
(def ^:const surface-rocky 5)
(def ^:const surface-molten 6)

(def ^:private temperate-band
  "Surface temperature band (K) within which a terrestrial world renders with
   liquid-ocean base colouring."
  [240.0 340.0])

(defn body-appearance
  "Procedural surface parameters for a body: {:surface :accent :seed} plus an
   optional :base colour override.

   - stars → granulated photosphere (colour = spectral colour)
   - protostars & hot debris → molten crust over glowing cracks
   - gas/ice giants → banded atmospheres
   - terrestrial planets → ocean/land/ice; a temperate world gets a blue ocean
     base, and a LIVING world (ecology past prebiotic) gets green land — life
     must be visible from orbit.
   - cold debris → rocky albedo patches"
  [{:keys [state planet-type temp living? eid]}]
  (let [seed   (double (+ 0.5 (mod (abs (long (hash [:surface eid]))) 89)))
        t      (double (or temp 10.0))
        molten? (> t 1200.0)
        [t-lo t-hi] temperate-band]
    (case state
      :star      {:surface surface-star :accent [1.0 0.9 0.6] :seed seed}
      :protostar {:surface surface-molten :accent [1.0 0.5 0.15] :seed seed}
      :planet    (case planet-type
                   :gas-giant {:surface surface-gas-giant
                               :accent [0.80 0.63 0.44] :seed seed}
                   :ice-giant {:surface surface-ice-giant
                               :accent [0.65 0.82 0.95] :seed seed}
                   ;; :terrestrial or untyped
                   (cond-> {:surface surface-terrestrial
                            :accent (if living? [0.24 0.60 0.28] [0.60 0.49 0.36])
                            :seed seed}
                     (and (>= t t-lo) (<= t t-hi))
                     (assoc :base [0.10 0.28 0.52])))
      :gas-giant {:surface surface-gas-giant :accent [0.80 0.63 0.44] :seed seed}
      ;; A dim, warm contracting first core — pre-protostar gas collapse.
      :condensed-core {:surface surface-molten :accent [0.75 0.35 0.18] :seed seed}
      :brown-dwarf {:surface surface-molten :accent [1.0 0.55 0.2] :seed seed}
      :planetesimal (if molten?
                      {:surface surface-molten :accent [1.0 0.5 0.15] :seed seed}
                      {:surface surface-rocky :accent [0.5 0.45 0.4] :seed seed})
      {:surface surface-flat :accent [0.0 0.0 0.0] :seed seed})))

(defn stellar-spectral-color
  "RGB color for a star based on its effective temperature (K).
   Maps T_eff to the visible spectral sequence: cool red M-dwarfs through
   solar yellow to hot blue O-stars. Interpolated between anchor points."
  [teff]
  (let [t (double (or teff 5800.0))
        ;; Anchor points: [T_eff R G B] — spectral-type colors
        anchors [[2400.0 1.00 0.55 0.35]  ;; M5V — deep red
                 [3700.0 1.00 0.70 0.45]  ;; K/M — orange
                 [5200.0 1.00 0.87 0.65]  ;; K0V — yellow-orange
                 [5800.0 1.00 0.95 0.82]  ;; G2V — yellow-white (solar)
                 [6500.0 0.98 0.97 0.92]  ;; F0V — white
                 [8500.0 0.90 0.92 1.00]  ;; A0V — blue-white
                 [15000.0 0.80 0.85 1.00] ;; B0V — blue
                 [42000.0 0.70 0.78 1.00]]] ;; O5V — deep blue
    (cond
      (<= t 2400.0) (subvec (first anchors) 1)
      (>= t 42000.0) (subvec (last anchors) 1)
      :else
      (let [;; Find the two anchors to interpolate between
            idx (reduce (fn [i a] (if (> t (first a)) (inc i) i)) 0 anchors)
            [t0 r0 g0 b0] (nth anchors (dec idx))
            [t1 r1 g1 b1] (nth anchors idx)
            frac (/ (- t t0) (- t1 t0))]
        [(+ r0 (* frac (- r1 r0)))
         (+ g0 (* frac (- g1 g0)))
         (+ b0 (* frac (- b1 b0)))]))))

(defn coherence-color
  "Reticle colour for each decoherence state: teal when highly coherent, warming
   to red as the spark fades — so the player reads their own coherence at a glance."
  [state]
  (case state
    :highly-coherent [0.40 1.00 0.75]
    :coherent        [0.45 0.85 1.00]
    :wavering        [1.00 0.90 0.40]
    :fading          [1.00 0.55 0.30]
    :dissolved       [0.65 0.30 0.30]
    [0.70 0.90 1.00]))
