(ns law.stellar.schema
  "Malli schemas and contracts for stellar nebula, star formation, and planetary bodies."
  (:require
   [malli.core :as m]
   [law.contract :as contract]))

(def matter-state-schema
  "Schema for matter in various states from nebula to planet"
  {:id          (some-fn uuid? integer?) ;; ECS entity ids are integers; UUIDs also ok
   :position    vector? ;; [x y z]
   :velocity    vector? ;; [vx vy vz]
   :mass        pos?
   :radius      pos?
   :temperature pos?
   :density     pos?
   :composition map? ;; {:H 0.75 :He 0.24 :metals 0.01}
   :state       keyword? ;; :nebula :condensed-core :planetesimal :gas-giant :brown-dwarf :protostar :star :planet :stellar-remnant
   :luminosity  number?
   :pressure    number?})

(def nebula-cloud-schema
  "Statistical representation of unfocused nebular region"
  {:id          uuid?
   :center      vector?
   :extent      pos? ;; radius of cloud
   :total-mass  pos?
   :temperature pos?
   :density     pos?
   :composition map?
   :angular-momentum vector?
   :turbulence  number? ;; 0.0 to 1.0
   :focus-level number? ;; 0.0 (statistical) to 1.0 (fully resolved)
   })

(def ^:export angular-momentum-schema
  "Specific angular momentum vector [Lx Ly Lz] in kg m²/s."
  vector?)

(def ^:export spin-schema
  "Body-fixed angular velocity vector [ωx ωy ωz] in rad/s."
  vector?)

(def ^:export oblateness-schema
  "Polar/equatorial axis ratio c/a. 1 is spherical; smaller values are flatter discs."
  (some-fn nil? #(and (number? %) (<= 0.0 % 1.0))))

(def ^:export rotation-axis-schema
  "Unit vector [nx ny nz] along the body's angular momentum / spin axis."
  vector?)

(def ^:export accretion-radius-schema
  "Gravitational feeding-zone radius (m) of a star-forming body. Larger than the
   photosphere: it is the capture radius within which gas is accreted, and it
   does NOT shrink when the photosphere contracts. nil for ordinary gas clumps."
  (some-fn nil? pos?))

;; --- Planet classification (M5 handoff Phase 1, §3.1-3.2) -------------------
;; Material class and thermal band are the first structured "planet candidate"
;; tags — composition/mass and two-body equilibrium temperature, no orbit
;; integration or atmosphere physics. See
;; kanban/tasks/ecology-m5-phase1-planet-classification.md.

(def material-class-schema
  "Bulk material class derived from composition (`domain.chemistry/bulk-
   categories`) and mass: `:rocky` (metal+rock dominant, low H/He, sub-1e25 kg),
   `:icy` (ice/volatile dominant, sub-5e25 kg), `:gaseous` (H/He dominant,
   above 1e25 kg), or `:mixed` (none of the above strongly)."
  [:enum :rocky :icy :gaseous :mixed])

(def ^:export material-class?
  "Predicate: does `value` satisfy `material-class-schema`?"
  (m/validator material-class-schema))

(def thermal-band-schema
  "Coarse two-body equilibrium-temperature band, bucketed from
   T_eff = (L(1-A) / (16 π σ a²))^0.25: `:frozen` (<150K), `:cold` (150-250K),
   `:temperate` (250-350K), `:warm` (350-450K), `:hot` (>450K)."
  [:enum :frozen :cold :temperate :warm :hot])

(def ^:export thermal-band?
  "Predicate: does `value` satisfy `thermal-band-schema`?"
  (m/validator thermal-band-schema))

;; --- Orbit stability (M5 handoff Phase 2, §3.3) -----------------------------
;; Analytic proxy tag — periapsis/apoapsis bounds plus Hill-radius separation
;; from other candidates, NOT a 10 Myr two-body integration. See
;; kanban/tasks/ecology-m5-phase2-orbit-stability.md.

(def orbit-stable-schema
  "Whether a candidate planet's orbit passes the analytic stability proxy
   (`domain.orbital.stability/orbit-stability`): periapsis clear of the star,
   apoapsis bound to the system, and no close approach to a sibling
   candidate."
  :boolean)

(def ^:export orbit-stable?
  "Predicate: does `value` satisfy `orbit-stable-schema`?"
  (m/validator orbit-stable-schema))

;; --- Atmosphere retention (M5 handoff Phase 3) -------------------------------
;; `:component/atmosphere-class` and `:component/retained-species` schemas
;; live in `law.atmosphere` (alongside its retention-ratio thresholds and
;; species-mass constants) and are re-exported here as `law.stellar/
;; atmosphere-class-schema` / `retained-species-schema`, mirroring how
;; `material-class-schema`/`thermal-band-schema` above are re-exported from
;; `law.stellar.schema`. See kanban/tasks/ecology-m5-phase3-atmosphere-
;; retention.md and docs/research/atmosphere/planetary-atmosphere-retention-
;; classifier.md.

;; --- Handoff / planet-candidate record (M5 handoff Phase 4) -----------------
;; The canonical `:planet-candidate` output record (parent
;; kanban/tasks/ecology-water-gate-snowline.md §5) and the `:event/phase0-
;; handoff` ledger event that carries a batch of them. See
;; kanban/tasks/ecology-m5-phase4-handoff-event.md and
;; `domain.stellar.classifier.candidate/handoff-system` /
;; `domain.genesis.tick/emit-handoff-event`.

(def planet-candidate-schema
  "Every key of the parent §5 `:planet-candidate` contract. `:planet-id` and
   `:star-id` are ECS entity ids (integers in this engine; `uuid?` kept as an
   alternative for parity with `matter-state-schema` above).
   `:equilibrium-temperature`/`:semi-major-axis`/`:eccentricity` may be nil
   only in the degenerate case where the two-body orbit is unbound at record-
   build time (should not occur for anything that passed `handoff-system`'s
   own eligibility gate, but the schema does not assume it can't)."
  [:map
   [:planet-id               [:or uuid? integer?]]
   [:star-id                 [:or uuid? integer?]]
   [:material-class          material-class-schema]
   [:thermal-band            thermal-band-schema]
   [:equilibrium-temperature [:maybe number?]] ;; K
   [:semi-major-axis         [:maybe number?]] ;; m
   [:eccentricity            [:maybe number?]]
   [:orbit-stable?           :boolean]
   [:atmosphere-class        keyword?] ;; law.atmosphere/atmosphere-class-schema
   [:retained-species        set?]     ;; law.atmosphere/retained-species-schema
   [:volatile-budget-kg {:optional true} [:maybe number?]] ;; kg, law.chemistry/volatile-budget-schema (nil until :differentiation first writes)
   [:differentiated-layers {:optional true} [:maybe map?]] ;; law.chemistry/differentiated-layers-schema (nil while undifferentiated)
   [:bulk-composition        map?]     ;; {:H double :He double :O double ...} mass fractions
   [:angular-momentum        vector?]  ;; [Lx Ly Lz] kg m²/s
   [:rotation-axis           vector?]  ;; unit [nx ny nz]
   [:oblateness              [:maybe number?]]
   [:surface-gravity         number?]  ;; m/s²
   [:core-dynamo?            :boolean]
   [:magnetic-field          vector?]  ;; [Bx By Bz] tesla
   [:formation-events        sequential?]]) ;; [event-id ...]

(def planet-candidate?
  "Predicate: does `value` satisfy `planet-candidate-schema`?"
  (m/validator planet-candidate-schema))

(def phase0-handoff-event-schema
  "Payload shape of the `:event/phase0-handoff` ledger event
   (`domain.genesis.tick/emit-handoff-event`): the batch of
   `:planet-candidate` records that met the handoff gate this tick."
  [:map [:candidates [:sequential planet-candidate-schema]]])

(def ^:export phase0-handoff-event?
  "Predicate: does `value` satisfy `phase0-handoff-event-schema`?"
  (m/validator phase0-handoff-event-schema))

(def stellar-system-schema
  "Container for all bodies in a forming star system"
  {:id           uuid?
   :age          number? ;; seconds since formation began
   :central-star (some-fn nil? map?) ;; nil until star forms
   :bodies       sequential? ;; all other bodies
   :nebula       (some-fn nil? map?) ;; remaining nebula if any
   :time-scale   pos? ;; current time compression factor
   :complexity   number? ;; observable complexity metric
   })

(def matter-state-contract
  (contract/->contract
   {:id       :law.stellar/matter-state
    :shape-id :law.stellar/stellar-body
    :kind     :type
    :schema   matter-state-schema
    :name     "Matter State"
    :description "Physical state of matter from nebula to planet"}))

(def ^:export nebula-cloud-contract
  (contract/->contract
   {:id       :law.stellar/nebula-cloud
    :shape-id :law.stellar/nebular-region
    :kind     :type
    :schema   nebula-cloud-schema
    :name     "Nebula Cloud"
    :description "Statistical representation of nebular gas cloud"}))

(def ^:export stellar-system-contract
  (contract/->contract
   {:id       :law.stellar/stellar-system
    :shape-id :law.stellar/star-system
    :kind     :type
    :schema   stellar-system-schema
    :name     "Stellar System"
    :description "Complete star system in formation"}))
