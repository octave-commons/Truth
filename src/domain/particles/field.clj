(ns domain.particles.field
  "The gas particle field for Phase 0 — a flat primitive-array buffer of up to
   `cap` particles. Gravity comes from the particle-mesh solver; close-range
   inelastic accretion (spatial-hash merging) provides the dissipation that lets
   a rotating cloud collapse into stars and flatten into a disk, and lets the
   cloud fragment into multiple protostars.

   A dead/absorbed particle is marked by mass = 0 and skipped everywhere.
   Particle 'kind' is derived from mass at render time, not stored.

   Mutable in place for performance: step! mutates the arrays and returns the
   same Field. This buffer is the design's statistical field; massive accreted
   clumps are what later get promoted to resolved bodies."
  (:require [domain.particles.pm :as pm])
  (:import [java.util HashMap ArrayList]))

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(defrecord Field [^long cap ^double r0 ^double m0
                  ^doubles px ^doubles py ^doubles pz
                  ^doubles vx ^doubles vy ^doubles vz
                  ^doubles mass ^doubles radius
                  ^doubles ax ^doubles ay ^doubles az])

(defn make-field [cap r0 m0]
  (let [cap (long cap)]
    (->Field cap (double r0) (double m0)
             (double-array cap) (double-array cap) (double-array cap)
             (double-array cap) (double-array cap) (double-array cap)
             (double-array cap) (double-array cap)
             (double-array cap) (double-array cap) (double-array cap))))

(defn radius-for-mass
  "Visual/physical radius from mass, assuming constant density (r ∝ m^(1/3))."
  ^double [^double r0 ^double m0 ^double m]
  (* r0 (Math/cbrt (/ m m0))))

(defn live-count
  "Number of still-existing particles (mass > 0)."
  ^long [^Field f]
  (let [^doubles mass (.mass f) cap (.cap f)]
    (loop [i 0 c 0] (if (< i cap) (recur (inc i) (if (pos? (aget mass i)) (inc c) c)) c))))

(defn total-mass
  ^double [^Field f]
  (let [^doubles mass (.mass f) cap (.cap f)]
    (loop [i 0 s 0.0] (if (< i cap) (recur (inc i) (+ s (aget mass i))) s))))

;; --- Seeding ----------------------------------------------------------------

(defn seed-cloud!
  "Fill the field with a rotating, turbulent spherical cloud of `n` particles of
   radius `cloud-r`, plus `n-seeds` Gaussian over-density blobs that gravity
   amplifies into separate protostars (so the cloud can fragment into
   binaries/multiples). `spin` sets solid-body rotation ω about z; `turb` sets
   random velocity dispersion. `rng` is a java.util.Random for reproducibility."
  [^Field f {:keys [n cloud-r spin turb particle-mass n-seeds seed-r ^java.util.Random rng]
             :or   {n-seeds 3}}]
  (let [^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
        ^doubles vx (.vx f) ^doubles vy (.vy f) ^doubles vz (.vz f)
        ^doubles mass (.mass f) ^doubles radius (.radius f)
        n        (long n)
        cloud-r  (double cloud-r)
        spin     (double spin)
        turb     (double turb)
        pmass    (double particle-mass)
        seed-r   (double (or seed-r (* 0.18 cloud-r)))
        r0       (.r0 f) m0 (.m0 f)
        ;; centres of the over-density seeds
        ^doubles scx (double-array n-seeds)
        ^doubles scy (double-array n-seeds)
        ^doubles scz (double-array n-seeds)]
    (dotimes [s n-seeds]
      (let [u (.nextDouble rng) ct (- (* 2.0 (.nextDouble rng)) 1.0)
            rr (* 0.55 cloud-r (Math/cbrt u))
            st (Math/sqrt (- 1.0 (* ct ct)))
            ph (* 2.0 Math/PI (.nextDouble rng))]
        (aset scx s (* rr st (Math/cos ph)))
        (aset scy s (* rr st (Math/sin ph)))
        (aset scz s (* rr ct 0.4))))
    (dotimes [i n]
      ;; ~45% of particles cluster into a random seed blob; the rest fill the
      ;; cloud uniformly. Seeds become the overdensities that gravity grows into
      ;; separate protostars.
      (let [in-seed? (and (pos? (long n-seeds)) (< (.nextDouble rng) 0.45))
            pos (if in-seed?
                  (let [s (long (* (.nextDouble rng) (long n-seeds)))
                        sr (double seed-r)]
                    [(+ (aget scx s) (* sr (.nextGaussian rng)))
                     (+ (aget scy s) (* sr (.nextGaussian rng)))
                     (+ (aget scz s) (* 0.5 sr (.nextGaussian rng)))])
                  (let [u (.nextDouble rng) ct (- (* 2.0 (.nextDouble rng)) 1.0)
                        rr (* cloud-r (Math/cbrt u)) st (Math/sqrt (- 1.0 (* ct ct)))
                        ph (* 2.0 Math/PI (.nextDouble rng))]
                    [(* rr st (Math/cos ph))
                     (* rr st (Math/sin ph))
                     (* rr ct 0.6)]))            ;; mild initial flattening in z
            xx (double (nth pos 0)) yy (double (nth pos 1)) zz (double (nth pos 2))]
        (aset px i xx) (aset py i yy) (aset pz i zz)
        ;; solid-body rotation about z + turbulent dispersion
        (aset vx i (+ (* (- spin) yy) (* turb (.nextGaussian rng))))
        (aset vy i (+ (* spin xx)     (* turb (.nextGaussian rng))))
        (aset vz i (* turb (.nextGaussian rng)))
        (aset mass i pmass)
        (aset radius i (radius-for-mass r0 m0 pmass))))))

;; --- Accretion (spatial-hash inelastic merge) -------------------------------

(defn- cell-key ^long [^long ix ^long iy ^long iz]
  ;; pack into one long; offset keeps indices non-negative for a ~±1024 range
  (let [o 1024]
    (+ ix o (* (+ iy o) 4096) (* (+ iz o) 16777216))))

(defn accrete!
  "Merge particles within `acc-r` of one another into the more massive one,
   conserving mass and momentum and growing radius by volume. `acc-r` is a fixed
   accretion radius; the spatial-hash cell size equals it so the 27-cell
   neighbourhood covers the full search range regardless of how large sinks grow.
   O(N)."
  [^Field f ^double acc-r]
  (let [^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
        ^doubles vx (.vx f) ^doubles vy (.vy f) ^doubles vz (.vz f)
        ^doubles mass (.mass f) ^doubles radius (.radius f)
        cap (.cap f) r0 (.r0 f) m0 (.m0 f)
        acc-r2 (* acc-r acc-r)
        inv (/ 1.0 acc-r)
        ^HashMap grid (HashMap.)]
    ;; bucket live particles by cell
    (dotimes [p cap]
      (when (pos? (aget mass p))
        (let [k (cell-key (long (Math/floor (* (aget px p) inv)))
                          (long (Math/floor (* (aget py p) inv)))
                          (long (Math/floor (* (aget pz p) inv))))
              ^ArrayList bucket (or (.get grid k) (let [b (ArrayList.)] (.put grid k b) b))]
          (.add bucket (int p)))))
    ;; for each live particle, test partners in its 27-cell neighbourhood
    (dotimes [p cap]
      (when (pos? (aget mass p))
        (let [ipx (long (Math/floor (* (aget px p) inv)))
              ipy (long (Math/floor (* (aget py p) inv)))
              ipz (long (Math/floor (* (aget pz p) inv)))]
          (doseq [dz [-1 0 1] dy [-1 0 1] dx [-1 0 1]]
            (when (pos? (aget mass p))
              (let [^ArrayList bucket (.get grid (cell-key (+ ipx (long dx)) (+ ipy (long dy)) (+ ipz (long dz))))]
                (when bucket
                  (dotimes [bi (.size bucket)]
                    (let [q (long (.get bucket bi))]
                      (when (and (> q p) (pos? (aget mass p)) (pos? (aget mass q)))
                        (let [ddx (- (aget px q) (aget px p))
                              ddy (- (aget py q) (aget py p))
                              ddz (- (aget pz q) (aget pz p))
                              d2  (+ (* ddx ddx) (* ddy ddy) (* ddz ddz))]
                          (when (< d2 acc-r2)
                            ;; merge q into p (p is the survivor)
                            (let [mp (aget mass p) mq (aget mass q) mt (+ mp mq)]
                              (aset px p (/ (+ (* mp (aget px p)) (* mq (aget px q))) mt))
                              (aset py p (/ (+ (* mp (aget py p)) (* mq (aget py q))) mt))
                              (aset pz p (/ (+ (* mp (aget pz p)) (* mq (aget pz q))) mt))
                              (aset vx p (/ (+ (* mp (aget vx p)) (* mq (aget vx q))) mt))
                              (aset vy p (/ (+ (* mp (aget vy p)) (* mq (aget vy q))) mt))
                              (aset vz p (/ (+ (* mp (aget vz p)) (* mq (aget vz q))) mt))
                              (aset mass p mt)
                              (aset radius p (radius-for-mass r0 m0 mt))
                              (aset mass q 0.0))))))))))))))
    f))

;; --- Integration ------------------------------------------------------------

(defn step!
  "Advance the field one step: PM gravity → symplectic Euler kick/drift →
   accretion. Particles are kept inside the box by a soft reflecting boundary.
   Returns the same Field."
  [^Field f mesh ^double dt {:keys [merge-cell]
                             :or {merge-cell nil}}]
  (let [^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
        ^doubles vx (.vx f) ^doubles vy (.vy f) ^doubles vz (.vz f)
        ^doubles ax (.ax f) ^doubles ay (.ay f) ^doubles az (.az f)
        ^doubles mass (.mass f)
        cap (.cap f)
        half (* 0.5 (double (:box mesh)))
        bound (* 0.98 half)
        mcell (double (or merge-cell (* 2.0 (.r0 f))))]
    (pm/solve! mesh px py pz mass cap ax ay az)
    (dotimes [p cap]
      (when (pos? (aget mass p))
        ;; kick
        (aset vx p (+ (aget vx p) (* (aget ax p) dt)))
        (aset vy p (+ (aget vy p) (* (aget ay p) dt)))
        (aset vz p (+ (aget vz p) (* (aget az p) dt)))
        ;; drift
        (aset px p (+ (aget px p) (* (aget vx p) dt)))
        (aset py p (+ (aget py p) (* (aget vy p) dt)))
        (aset pz p (+ (aget pz p) (* (aget vz p) dt)))
        ;; soft reflecting boundary so nothing leaves the periodic box region
        (when (> (Math/abs (aget px p)) bound)
          (aset px p (Math/copySign bound (aget px p))) (aset vx p (* -0.5 (aget vx p))))
        (when (> (Math/abs (aget py p)) bound)
          (aset py p (Math/copySign bound (aget py p))) (aset vy p (* -0.5 (aget vy p))))
        (when (> (Math/abs (aget pz p)) bound)
          (aset pz p (Math/copySign bound (aget pz p))) (aset vz p (* -0.5 (aget vz p))))))
    (accrete! f mcell)
    f))

;; --- Sink promotion ---------------------------------------------------------

(defn sink-particles
  "Return a vector of [index mass] for particles whose mass is at least
   `threshold` and which are therefore candidates for promotion to resolved
   ECS bodies (protostars / planetesimals). Sorted most massive first."
  [^Field f ^double threshold]
  (let [^doubles mass (.mass f) cap (.cap f)]
    (loop [i 0 acc []]
      (if (< i cap)
        (let [m (aget mass i)]
          (recur (inc i) (if (>= m threshold) (conj acc [i m]) acc)))
        (sort-by second #(compare %2 %1) acc)))))
