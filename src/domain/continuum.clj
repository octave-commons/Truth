(ns domain.continuum
  "Closed plane-strain continuum region: consistent inertia, elastic/contact energy and refinement."
  (:require [law.continuum :as law]
            [domain.ecs.core :as ecs] [domain.ecs.components :as c]))

(defn norm "Euclidean length." [v] (Math/sqrt (reduce + 0.0 (map #(* % %) v))))
(defn- add [a b] (mapv + a b))
(defn- sub [a b] (mapv - a b))
(defn- mul [a s] (mapv #(* s %) a))
(defn- dot [a b] (reduce + 0.0 (map * a b)))
(defn- cross [[a b] [c d]] (- (* a d) (* b c)))
(defn- mm [[a b c d] [e f g h]] [(+ (* a e) (* b g)) (+ (* a f) (* b h)) (+ (* c e) (* d g)) (+ (* c f) (* d h))])
(defn- det [[a b c d]] (- (* a d) (* b c)))
(defn- inv [[a b c d :as f]]
  (let [j (det f)]
    (when-not (> j 1e-9) (throw (ex-info "Inverted continuum element" {:determinant j})))
    [(/ d j) (/ (- b) j) (/ (- c) j) (/ a j)]))
(defn- trans [[a b c d]] [a c b d])
(defn- mv [[a b c d] [x y]] [(+ (* a x) (* b y)) (+ (* c x) (* d y))])
(defn- dm [x [i j k]] (let [[a c] (sub (x j) (x i)) [b d] (sub (x k) (x i))] [a b c d]))
(defn- matvec [m x] (mapv #(dot % x) m))

(defn- cholesky [m]
  (let [n (count m) a (double-array (* n n))]
    (dotimes [i n]
      (dotimes [jj (inc i)]
        (let [j jj s (loop [k 0 s 0.0]
                      (if (= k j) s (recur (inc k) (+ s (* (aget a (+ (* i n) k)) (aget a (+ (* j n) k)))))))
              v (- (double (get-in m [i j])) s)]
          (aset a (+ (* i n) j)
                (if (= i j)
                  (if (pos? v) (Math/sqrt v) (throw (ex-info "Nonpositive consistent mass" {:row i :value v})))
                  (/ v (aget a (+ (* j n) j))))))))
    (mapv (fn [i] (mapv #(aget a (+ (* i n) %)) (range n))) (range n))))

(defn- solve [l b]
  (let [n (count b)
        y (reduce (fn [y i] (conj y (/ (- (b i) (dot (subvec (l i) 0 i) y)) (get-in l [i i])))) [] (range n))]
    (loop [i (dec n) x (vec (repeat n 0.0))]
      (if (neg? i) x
        (let [s (reduce + 0.0 (for [j (range (inc i) n)] (* (get-in l [j i]) (x j))))]
          (recur (dec i) (assoc x i (/ (- (y i) s) (get-in l [i i])))))))))

(defn- prepare [s]
  (let [n (count (:ref s)) {:keys [rho thickness]} (:material s)
        elements (mapv (fn [tri]
                         (let [d (dm (:ref s) tri) area (* 0.5 (det d)) inverse (inv d)
                               [a b c d] inverse]
                           {:ids tri :volume (* area thickness) :inverse inverse
                            :grads [[(- (+ a c)) (- (+ b d))] [a b] [c d]]})) (:tri s))
        mass (reduce (fn [m {:keys [ids volume]}]
                       (reduce (fn [m [i j]]
                                 (update-in m [(ids i) (ids j)] + (* rho volume (/ (if (= i j) 2.0 1.0) 12.0))))
                               m (for [i (range 3) j (range 3)] [i j])))
                     (vec (repeat n (vec (repeat n 0.0)))) elements)
        weights (mapv #(reduce + %) mass)
        boundary (->> (mapcat (fn [tri] [[(tri 0) (tri 1)] [(tri 1) (tri 2)] [(tri 2) (tri 0)]]) (:tri s))
                      (filter (fn [[a b]] (and (zero? (first ((:ref s) a))) (zero? (first ((:ref s) b)))))))
        contacts (mapv (fn [j]
                         (let [y (* 8.0 (+ j 0.5))
                               [a b] (first (filter (fn [[a b]] (<= (min (second ((:ref s) a)) (second ((:ref s) b))) y
                                                                     (max (second ((:ref s) a)) (second ((:ref s) b))))) boundary))
                               ya (second ((:ref s) a)) yb (second ((:ref s) b)) f (/ (- y ya) (- yb ya))]
                           {:ids [a b] :weights [(- 1.0 f) f] :area (* 8.0 thickness)})) (range 16))]
    (assoc s :elements elements :mass-matrix mass :mass-factor (cholesky mass) :node-masses weights :contacts contacts)))

(declare refine observe)

(defn create-region
  "Initialize one finite plane-strain patch and a declared cylindrical impactor."
  [mode]
  (let [ref (vec (for [j (range 3) i (range 5)] [(* 64.0 i) (* 64.0 j)]))
        tris (vec (mapcat (fn [[i j]] (let [a (+ i (* j 5)) b (inc a) d (+ a 5) c (inc d)] [[a b c] [a c d]]))
                         (for [j (range 2) i (range 4)] [i j])))
        pm (* Math/PI 24.0 24.0 64.0 7800.0)
        s (prepare {:ref ref :x ref :v (vec (repeat (count ref) [0.0 0.0])) :tri tris
                    :damage (vec (repeat 16 0.0)) :heat (vec (repeat 16 0.0)) :history (vec (repeat 16 0.0))
                    :material {:rho 3000.0 :thickness 64.0 :young-pa 200000.0 :poisson 0.25
                               :contact-length-m 8.0 :relaxation-s 60.0 :damage-onset-J-m3 300.0 :damage-full-J-m3 4000.0}
                    :projectile {:p [-90.0 64.0] :v [0.8 0.0] :mass pm :radius 24.0}
                    :mode mode :level 0 :time 0.0 :transfers [] :last-contact -1e9 :quiet-s 0.0
                    :stocks-kg {:silicate (* 0.95 256.0 128.0 64.0 3000.0)
                                :water (* 0.05 256.0 128.0 64.0 3000.0) :iron pm}})]
    (law/validate-region! s)
    (if (= mode :fine) (refine s) s)))

(defn affine-state
  "Set affine geometry and velocity for exact interpolation/conservation probes."
  [s matrix offset velocity-matrix bulk]
  (let [flat (vec (mapcat identity matrix)) vf (vec (mapcat identity velocity-matrix))]
    (assoc s :x (mapv #(add offset (mv flat %)) (:ref s))
             :v (mapv #(add bulk (mv vf %)) (:ref s)))))

(defn elastic
  "Elastic energy, objective internal force and pressure/shear observations."
  [s]
  (let [{:keys [young-pa poisson]} (:material s)
        mu (/ young-pa (* 2.0 (+ 1.0 poisson))) lambda (/ (* young-pa poisson) (* (+ 1.0 poisson) (- 1.0 (* 2.0 poisson))))]
    (reduce
     (fn [{:keys [forces] :as result} [ei {:keys [ids volume inverse grads]}]]
       (let [f (mm (dm (:x s) ids) inverse) j (det f) fi-t (trans (inv f)) logj (Math/log j)
             density (+ (* 0.5 mu (- (dot f f) 2.0)) (* (- mu) logj) (* 0.5 lambda logj logj))
             damage ((:damage s) ei) p (mapv (fn [a b] (* (- 1.0 damage) (+ (* mu (- a b)) (* lambda logj b)))) f fi-t)
             sigma (mul (mm p (trans f)) (/ 1.0 j))
             pressure (- (/ (+ (sigma 0) (sigma 3) (/ (* (- 1.0 damage) lambda logj) j)) 3.0))
             shear (Math/sqrt (+ (Math/pow (- (sigma 0) (sigma 3)) 2) (* 4.0 (sigma 1) (sigma 1))))
             forces (reduce (fn [forces k] (update forces (ids k) add (mul (mv p (grads k)) (- volume)))) forces (range 3))]
         (-> result (assoc :forces forces) (update :energy + (* volume (- 1.0 damage) density))
             (update :density conj density) (update :pressure conj pressure) (update :shear conj shear) (update :jacobian conj j))))
     {:forces (vec (repeat (count (:x s)) [0.0 0.0])) :energy 0.0 :density [] :pressure [] :shear [] :jacobian []}
     (map-indexed vector (:elements s)))))

(defn contact
  "Fixed physical surface quadrature: reciprocal forces from one penalty potential."
  [s]
  (let [center (get-in s [:projectile :p]) radius (get-in s [:projectile :radius])
        stiffness (/ (get-in s [:material :young-pa]) (get-in s [:material :contact-length-m]))]
    (reduce
     (fn [{:keys [forces] :as r} {:keys [ids weights area]}]
       (let [point (add (mul ((:x s) (ids 0)) (weights 0)) (mul ((:x s) (ids 1)) (weights 1)))
             d (sub point center) distance (norm d) overlap (max 0.0 (- radius distance))
             force (if (pos? overlap) (mul d (/ (* stiffness area overlap) (max distance 1e-9))) [0.0 0.0])]
         (-> r (update :energy + (* 0.5 stiffness area overlap overlap))
             (update :projectile-force sub force)
             (assoc :forces (-> forces (update (ids 0) add (mul force (weights 0))) (update (ids 1) add (mul force (weights 1)))))
             (update :maximum-overlap max overlap)
             (update :minimum-gap min (- distance radius)))))
     {:forces (vec (repeat (count (:x s)) [0.0 0.0])) :projectile-force [0.0 0.0] :energy 0.0
      :maximum-overlap 0.0 :minimum-gap Double/POSITIVE_INFINITY} (:contacts s))))

(defn- kinetic [s v]
  (* 0.5 (+ (dot (mapv first v) (matvec (:mass-matrix s) (mapv first v)))
            (dot (mapv second v) (matvec (:mass-matrix s) (mapv second v))))))

(defn observe
  "Account finite inventory, consistent inertia, all modeled energies and spin."
  [s]
  (let [m (:mass-matrix s) w (:node-masses s) total (reduce + w) x (:x s) v (:v s)
        vx (mapv first v) vy (mapv second v) px (mapv first x) py (mapv second x)
        projectile (:projectile s) pm (:mass projectile)
        center (mul (reduce add [0.0 0.0] (map #(mul %1 %2) x w)) (/ 1.0 total))
        bulk (mul (reduce add [0.0 0.0] (map #(mul %1 %2) v w)) (/ 1.0 total))
        angular (- (dot px (matvec m vy)) (dot py (matvec m vx)))
        k (+ (kinetic s v) (* 0.5 pm (dot (:v projectile) (:v projectile))))
        e (elastic s) co (contact s) q (reduce + (:heat s))]
    {:time-s (:time s) :level (:level s) :nodes (count x) :elements (count (:tri s))
     :mass-kg (+ total pm) :stocks-kg (:stocks-kg s)
     :momentum (add (mul bulk total) (mul (:v projectile) pm))
     :angular-momentum (+ angular (* pm (cross (:p projectile) (:v projectile))))
     :kinetic-J k :elastic-J (:energy e) :contact-J (:energy co) :heat-J q
     :energy-J (+ k (:energy e) (:energy co) q) :maximum-damage (reduce max (:damage s))
     :min-J (reduce min (:jacobian e)) :maximum-overlap-m (:maximum-overlap co)
     :center center :bulk bulk :peak-pressure-pa (reduce max (:pressure e))
     :nonrigid-rms-m-s (:nonrigid-rms s 0.0)
     :coarsen-rejections (:coarsen-rejections s 0) :transfers (:transfers s)}))

(defn refine
  "Subdivide affine elements with consistent mass; inherit damage and split heat."
  [s]
  (let [edges (vec (sort (set (mapcat (fn [[a b c]] [(vec (sort [a b])) (vec (sort [b c])) (vec (sort [c a]))]) (:tri s)))))
        old-n (count (:ref s)) lookup (zipmap edges (range old-n (+ old-n (count edges))))
        prolong (fn [v] (into v (map (fn [[a b]] (mul (add (v a) (v b)) 0.5))) edges))
        tris (vec (mapcat (fn [[a b c]]
                           (let [ab (lookup (vec (sort [a b]))) bc (lookup (vec (sort [b c]))) ca (lookup (vec (sort [c a])))]
                             [[a ab ca] [ab b bc] [ca bc c] [ab bc ca]])) (:tri s)))
        fine (-> s (assoc :ref (prolong (:ref s)) :x (prolong (:x s)) :v (prolong (:v s)) :tri tris :level (inc (:level s))
                          :damage (vec (mapcat #(repeat 4 %) (:damage s)))
                          :history (vec (mapcat #(repeat 4 %) (:history s)))
                          :heat (vec (mapcat #(repeat 4 (/ % 4.0)) (:heat s)))
                          :coarse-cache (select-keys s [:ref :tri :damage :history :heat :elements :mass-matrix :mass-factor :node-masses :contacts])
                          :refinement-edges edges)
                 prepare)
        before (observe s) after (observe fine)]
    (update fine :transfers conj {:op :refine :time-s (:time s)
                                  :energy-residual-J (- (:energy-J after) (:energy-J before))
                                  :mass-residual-kg (- (:mass-kg after) (:mass-kg before))
                                  :momentum-residual (sub (:momentum after) (:momentum before))
                                  :angular-residual (- (:angular-momentum after) (:angular-momentum before))})))

(defn- relax [s dt]
  (let [tau (get-in s [:material :relaxation-s]) a (Math/exp (/ (- dt) tau))
        w (:node-masses s) m (:mass-matrix s) total (reduce + w)
        center (mul (reduce add [0.0 0.0] (map #(mul %1 %2) (:x s) w)) (/ 1.0 total))
        bulk (mul (reduce add [0.0 0.0] (map #(mul %1 %2) (:v s) w)) (/ 1.0 total))
        r (mapv #(sub % center) (:x s)) dv (mapv #(sub % bulk) (:v s))
        rx (mapv first r) ry (mapv second r) vx (mapv first dv) vy (mapv second dv)
        inertia (+ (dot rx (matvec m rx)) (dot ry (matvec m ry)))
        angular (- (dot rx (matvec m vy)) (dot ry (matvec m vx))) omega (/ angular inertia)
        rigid (mapv (fn [[x y]] (add bulk [(* (- omega) y) (* omega x)])) r)
        residual (mapv sub (:v s) rigid)
        v (mapv #(add %1 (mul %2 a)) rigid residual)
        heat (* (- 1.0 (* a a)) (kinetic s residual))
        volume (reduce + (map :volume (:elements s)))]
    (-> s (assoc :v v :nonrigid-rms (Math/sqrt (/ (* 2.0 (kinetic s residual)) total)))
        (update :heat #(mapv (fn [q e] (+ q (* heat (/ (:volume e) volume)))) % (:elements s))))))

(defn- damage [s]
  (let [densities (:density (elastic s)) onset (get-in s [:material :damage-onset-J-m3]) full (get-in s [:material :damage-full-J-m3])
        history (mapv max (:history s) densities)
        next (mapv (fn [d h] (max d (min 0.65 (max 0.0 (/ (- h onset) (- full onset)))))) (:damage s) history)
        heat (mapv (fn [q old new density element] (+ q (* (- new old) density (:volume element))))
                   (:heat s) (:damage s) next densities (:elements s))]
    (assoc s :damage next :history history :heat heat)))

(defn- acceleration [s]
  (let [el (elastic s) co (contact s) force (mapv add (:forces el) (:forces co))
        ax (solve (:mass-factor s) (mapv first force)) ay (solve (:mass-factor s) (mapv second force))]
    {:nodes (mapv vector ax ay) :projectile (mul (:projectile-force co) (/ 1.0 (get-in s [:projectile :mass])))
     :contact? (pos? (:maximum-overlap co))}))

(defn try-coarsen
  "Admit an actual coarse projection only when accounted error and spin tolerances pass."
  [s]
  (if-not (:coarse-cache s) s
    (let [cache (:coarse-cache s) n (count (:ref cache))
          edges (:refinement-edges s) m (:mass-matrix s)
          transpose-prolong (fn [v]
                              (reduce (fn [out [[a b] value]] (-> out (update a + (* 0.5 value)) (update b + (* 0.5 value))))
                                      (subvec v 0 n) (map vector edges (subvec v n))))
          project (fn [v]
                    (mapv vector
                          (solve (:mass-factor cache) (transpose-prolong (matvec m (mapv first v))))
                          (solve (:mass-factor cache) (transpose-prolong (matvec m (mapv second v))))))
          coarse (-> (merge s cache) (assoc :x (project (:x s)) :v (project (:v s)) :level 0
                                           :damage (mapv #(reduce + (map (fn [d] (/ d 4.0)) %)) (partition 4 (:damage s)))
                                           :history (mapv #(reduce max %) (partition 4 (:history s)))
                                           :heat (mapv #(reduce + %) (partition 4 (:heat s)))))
          before (observe s) after (observe coarse)
          de (- (:energy-J after) (:energy-J before))
          dp (norm (sub (:momentum after) (:momentum before)))
          dl (abs (- (:angular-momentum after) (:angular-momentum before)))
          tolerance (* 1e-9 (:initial-energy-J s (:energy-J before)))]
      (if (and (< (abs de) tolerance) (< dp 1e-4) (< dl 1e-3))
        (-> coarse (dissoc :coarse-cache :refinement-edges)
            (assoc :fine-history (select-keys s [:damage :history :heat :x :v :tri]))
            (update :transfers conj {:op :coarsen :time-s (:time s) :energy-residual-J de :momentum-residual dp :angular-residual dl}))
        (-> s (update :coarsen-rejections (fnil inc 0))
            (assoc :last-coarsen-error {:energy-residual-J de :momentum-residual dp :angular-residual dl}))))))

(defn step
  "Advance the closed region in the existing integrator; refine from contact lookahead."
  [s dt]
  (let [co (contact s) speed (max 0.0 (first (get-in s [:projectile :v])))
        s (if (and (= :adaptive (:mode s)) (zero? (:level s)) (> speed 0.01)
                   (< (:minimum-gap co) (* 28.0 speed))) (refine s) s)
        s (if (:initial-energy-J s) s (assoc s :initial-energy-J (:energy-J (observe s))))
        s (relax s (* 0.5 dt)) a (acceleration s)
        half-v (mapv #(add %1 (mul %2 (* 0.5 dt))) (:v s) (:nodes a))
        pv (add (get-in s [:projectile :v]) (mul (:projectile a) (* 0.5 dt)))
        s (-> s (assoc :x (mapv #(add %1 (mul %2 dt)) (:x s) half-v) :v half-v)
              (update-in [:projectile :p] add (mul pv dt)) (assoc-in [:projectile :v] pv) damage)
        b (acceleration s)
        s (-> s (assoc :v (mapv #(add %1 (mul %2 (* 0.5 dt))) half-v (:nodes b)))
              (update-in [:projectile :v] add (mul (:projectile b) (* 0.5 dt)))
              (relax (* 0.5 dt)) (update :time + dt))
        s (cond-> s (:contact? b) (assoc :last-contact (:time s) :contact-seen? true))
        quiet? (and (:contact-seen? s) (not (:contact? b)) (< (:nonrigid-rms s) 1e-4) (> (- (:time s) (:last-contact s)) 60.0))
        s (assoc s :quiet-s (if quiet? (+ (:quiet-s s) dt) 0.0))]
    (if (and (= :adaptive (:mode s)) (pos? (:level s)) (> (:quiet-s s) 30.0)
             (zero? (mod (long (Math/round (/ (:time s) dt))) (max 1 (long (/ 10.0 dt))))))
      (try-coarsen s) s)))


(defn integrator-ws
  "Update localized material fields under the existing physical-state owner."
  [world dt]
  {c/continuum-region
   (into {} (map (fn [id] [id (step (ecs/get-component world id c/continuum-region) dt)]))
         (ecs/entities-with world c/continuum-region))})
