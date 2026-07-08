(ns law.sed
  "Contracts and schemas for panchromatic stellar spectral energy distributions.

   Derived from: docs/research/phase1-radiation-plasma-truth.md §2
   Stars emit radiation across the full electromagnetic spectrum. The SED shape
   is set by effective temperature (T_eff), surface gravity (log g), and
   metallicity. A scalar bolometric luminosity obscures band-dependent effects:
   XUV drives atmospheric escape, FUV/NUV affect photochemistry, IR regulates
   climate.

   The SED is parameterized as fractional band luminosities in a fixed set of
   broad bands, pre-tabulated per spectral type and scaled by L_bol."
  (:require
   [clojure.math :as math] [law.contract :as contract]
   [law.composition :as comp]))

;; --- SED band definitions ---

(def sed-bands
  "The ordered set of broad electromagnetic bands for SED representation.
   Covers gamma through radio. Not all bands are significant for all star types."
  [:gamma :xray :euv :fuv :nuv :vis :nir :mir :fir :radio])

(def xuv-bands
  "The XUV (X-ray + EUV) bands that drive atmospheric escape.
   These are the high-energy bands responsible for photoionization and heating
   of planetary upper atmospheres."
  [:xray :euv])

(def uv-bands
  "UV bands (FUV + NUV) that affect photochemistry."
  [:fuv :nuv])

(def climate-bands
  "Bands relevant to planetary climate: visible + NIR (stellar heating)
   plus IR (atmospheric absorption)."
  [:vis :nir :mir :fir])

;; --- SED template grid ---
;; Research: 12 minimum templates capture the physically important SED variation
;; across the HR diagram. Full interpolation grid: 450 templates (18 T_eff ×
;; 5 log g × 5 [Fe/H]) from ATLAS9/PHOENIX. We start with 12.
;; Source: Pickles 1998, Castelli & Kurucz 2004, Husser+ 2013

(def spectral-templates
  "The 12 canonical spectral-type templates for band-integrated SEDs.
   Each template is a map of band-keyword → fraction-of-L_bol.
   The fractions must sum to 1.0.

   Key band-ratio variations across the grid:
   - UV/Vis: 10³× (O to M) — critical for photochemistry
   - FUV/Vis: 10⁴× — prebiotic chemistry, ice photolysis
   - X-ray/Vis: 10²× — atmospheric stripping, ionization
   - NIR/Vis: 10× — thermal equilibrium, greenhouse effect"
  {:O5V  {:teff 42000 :logg 4.0 :feh 0.0
          :bands {:gamma 0.0 :xray 1e-4 :euv 0.05 :fuv 0.25 :nuv 0.30 :vis 0.25 :nir 0.10 :mir 0.04 :fir 0.01 :radio 0.0}}
   :B0V  {:teff 30000 :logg 4.0 :feh 0.0
          :bands {:gamma 0.0 :xray 5e-5 :euv 0.02 :fuv 0.20 :nuv 0.30 :vis 0.30 :nir 0.12 :mir 0.05 :fir 0.01 :radio 0.0}}
   :A0V  {:teff 9500  :logg 4.0 :feh 0.0
          :bands {:gamma 0.0 :xray 0.0  :euv 0.001 :fuv 0.05 :nuv 0.15 :vis 0.50 :nir 0.20 :mir 0.07 :fir 0.02 :radio 0.0}}
   :F0V  {:teff 7200  :logg 4.0 :feh 0.0
          :bands {:gamma 0.0 :xray 0.0  :euv 0.0   :fuv 0.02 :nuv 0.08 :vis 0.50 :nir 0.25 :mir 0.10 :fir 0.03 :radio 0.0}}
   :G2V  {:teff 5800  :logg 4.5 :feh 0.0
          :bands {:gamma 0.0 :xray 0.0  :euv 0.0   :fuv 0.01 :nuv 0.05 :vis 0.45 :nir 0.30 :mir 0.12 :fir 0.04 :radio 0.0}}
   :K0V  {:teff 5200  :logg 4.5 :feh 0.0
          :bands {:gamma 0.0 :xray 0.0  :euv 0.0   :fuv 0.005 :nuv 0.02 :vis 0.35 :nir 0.35 :mir 0.18 :fir 0.06 :radio 0.0}}
   :M0V  {:teff 3800  :logg 4.5 :feh 0.0
          :bands {:gamma 0.0 :xray 0.0  :euv 0.0   :fuv 0.001 :nuv 0.005 :vis 0.15 :nir 0.35 :mir 0.30 :fir 0.12 :radio 0.0}}
   :M5V  {:teff 3100  :logg 4.5 :feh 0.0
          :bands {:gamma 0.0 :xray 0.0  :euv 0.0   :fuv 0.0005 :nuv 0.002 :vis 0.08 :nir 0.25 :mir 0.35 :fir 0.20 :radio 0.0}}
   :G2III {:teff 5800 :logg 2.5 :feh 0.0
           :bands {:gamma 0.0 :xray 0.0 :euv 0.0   :fuv 0.01 :nuv 0.05 :vis 0.45 :nir 0.30 :mir 0.12 :fir 0.04 :radio 0.0}}
   :K5III {:teff 4000 :logg 1.5 :feh 0.0
           :bands {:gamma 0.0 :xray 0.0 :euv 0.0   :fuv 0.002 :nuv 0.01 :vis 0.20 :nir 0.30 :mir 0.30 :fir 0.12 :radio 0.0}}
   :M5III {:teff 3300 :logg 1.0 :feh 0.0
           :bands {:gamma 0.0 :xray 0.0 :euv 0.0   :fuv 0.001 :nuv 0.005 :vis 0.10 :nir 0.22 :mir 0.35 :fir 0.22 :radio 0.0}}
   :DA_WD {:teff 30000 :logg 8.0 :feh 0.0
           :bands {:gamma 0.0 :xray 1e-4 :euv 0.03 :fuv 0.20 :nuv 0.25 :vis 0.30 :nir 0.15 :mir 0.05 :fir 0.01 :radio 0.0}}})

;; --- Physical constants for SED computation ---

(def ^:const solar-teff
  "Solar effective temperature (K)."
  5778.0)

(def ^:const solar-logg
  "Solar surface gravity log10(g) in cm/s²."
  4.44)

;; --- Validation predicates ---

(defn positive-watts?
  "True if x is a positive, finite number (luminosity in Watts)."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (pos? (double x))))

(defn non-negative-watts?
  "True if x is a non-negative, finite number."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)))

(defn valid-teff?
  "True if x is a plausible stellar effective temperature (K).
   Range: 1000 K (ultra-cool dwarfs) to 200,000 K (WR stars)."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 1000.0)
       (<= (double x) 200000.0)))

(defn valid-logg?
  "True if x is a plausible log10(g) in cgs. Range: 0 (supergiants) to 9 (WD)."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)
       (<= (double x) 9.0)))

(defn band-keyword?
  "True if k is a valid SED band keyword."
  [k]
  (contains? (set sed-bands) k))

;; --- SED schemas ---

(def sed-band-schema
  "A single SED band: keyword name and luminosity in Watts."
  {:band-name band-keyword?
   :luminosity non-negative-watts?})

(def sed-bands-map-schema
  "Map from band keyword to luminosity (W). All values non-negative.
   Sum equals bolometric luminosity."
  (into {}
        (map (fn [b] [b non-negative-watts?]))
        sed-bands))

(def sed-profile-schema
  "Panchromatic SED profile for a star.
   Per-band luminosities plus the physical parameters that generated them.
   Sum of :bands values MUST equal :luminosity (bolometric)."
  {:teff        valid-teff?
   :logg        valid-logg?
   :metallicity comp/mass-fraction?    ;; Z mass fraction
   :luminosity  positive-watts?        ;; L_bol in Watts
   :bands       sed-bands-map-schema}) ;; band-keyword → Watts, all bands validated

;; --- Stellar atmosphere layer schemas ---
;; Derived from: docs/research/phase1-radiation-plasma-truth.md §3

(def atmosphere-layer-ids
  "The four canonical stellar atmosphere layers. A set for use as a predicate."
  #{:photosphere :chromosphere :transition :corona})

(def atmosphere-shell-schema
  "A single atmospheric shell/layer of a star.
   Temperature, electron density, ionization fraction, and magnetic field
   characterize each layer. Height is above the photosphere."
  {:layer/id              atmosphere-layer-ids   ;; set works as a predicate
   :temperature           positive-watts?         ;; K (> 0)
   :electron-density      non-negative-watts?     ;; m^-3
   :ionization-fraction   #(and (number? %)       ;; 0..1
                                (Double/isFinite (double %))
                                (>= (double %) 0.0)
                                (<= (double %) 1.0))
   :b-field               vector?                 ;; [Bx By Bz] Tesla
   :height                number?})               ;; m above photosphere

(def atmosphere-profile-schema
  "Full four-layer atmosphere profile for a star.
   Layers are ordered from innermost (photosphere) to outermost (corona)."
  {:star-id   uuid?
   :shells    sequential?})  ;; seq of atmosphere-shell-schema maps

;; --- Contracts ---

(def sed-band-contract
  (contract/->contract
   {:id       ::sed-band
    :shape-id ::stellar-sed
    :kind     :type
    :schema   sed-band-schema
    :name     "SED Band"
    :description "Single spectral energy distribution band with luminosity."}))

(def sed-profile-contract
  (contract/->contract
   {:id       ::sed-profile
    :shape-id ::stellar-sed
    :kind     :type
    :schema   sed-profile-schema
    :name     "SED Profile"
    :description "Panchromatic spectral energy distribution for a single star."}))

(def atmosphere-shell-contract
  (contract/->contract
   {:id       ::atmosphere-shell
    :shape-id ::stellar-atmosphere
    :kind     :type
    :schema   atmosphere-shell-schema
    :name     "Atmosphere Shell"
    :description "Single atmospheric layer of a stellar atmosphere."}))

(def atmosphere-profile-contract
  (contract/->contract
   {:id       ::atmosphere-profile
    :shape-id ::stellar-atmosphere
    :kind     :type
    :schema   atmosphere-profile-schema
    :name     "Atmosphere Profile"
    :description "Four-layer stellar atmosphere profile."}))

;; --- SED helper functions ---

(defn xuv-luminosity
  "Sum of XUV band luminosities (X-ray + EUV) from an SED bands map.
   This is the luminosity that drives atmospheric escape."
  [bands]
  (+ (double (:xray bands 0.0))
     (double (:euv bands 0.0))))

(defn uv-luminosity
  "Sum of UV band luminosities (FUV + NUV) from an SED bands map.
   Drives photochemistry in planetary atmospheres."
  [bands]
  (+ (double (:fuv bands 0.0))
     (double (:nuv bands 0.0))))

(defn climate-luminosity
  "Sum of climate-relevant band luminosities (vis + NIR + MIR + FIR).
   Drives equilibrium temperature and greenhouse forcing."
  [bands]
  (+ (double (:vis bands 0.0))
     (double (:nir bands 0.0))
     (double (:mir bands 0.0))
     (double (:fir bands 0.0))))

(defn bolometric-luminosity
  "Sum of all band luminosities. Should equal :luminosity on the profile."
  [bands]
  (reduce + 0.0 (map (fn [[_ v]] (double v)) bands)))

(defn bands-sum-to-bolometric?
  "True if band luminosities sum to L_bol within 1% tolerance."
  [profile]
  (let [L-bol (double (:luminosity profile 0.0))
        L-sum (bolometric-luminosity (:bands profile {}))]
    (if (pos? L-bol)
      (< (abs (- L-sum L-bol)) (* 0.01 L-bol))
      (zero? L-sum))))

;; --- SED template selection ---

(defn nearest-template
  "Find the nearest spectral template for given (T_eff, log g).
   Uses Euclidean distance in (T_eff/1000, log g) space.
   Returns the template keyword."
  [teff logg]
  (let [teff (double (or teff 5800.0))
        logg (double (or logg 4.5))
        ;; Normalize: T_eff in units of 1000K, log g as-is
        target [(/ teff 1000.0) logg]
        dist (fn [[t g]]
               (let [dt (- (first target) t)
                     dg (- (second target) g)]
                 (math/sqrt (+ (* dt dt) (* dg dg)))))]
    (key (apply min-key (fn [[_ v]] (dist [(/ (:teff v) 1000.0) (:logg v)]))
                spectral-templates))))

(defn select-sed-bands
  "Select band luminosities for a star given its T_eff and log g.
   Looks up the nearest template and scales by L_bol.
   Returns a map of band-keyword → Watts."
  [teff logg lbol]
  (let [template (get spectral-templates (nearest-template teff logg))
        bands    (:bands template)
        L        (double (or lbol 0.0))]
    (into {} (map (fn [[k frac]] [k (* L (double frac))])) bands)))
