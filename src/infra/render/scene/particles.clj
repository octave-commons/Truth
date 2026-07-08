(ns infra.render.scene.particles
  "Particle and magnetic-field-line render helpers.

   Fog puffs, cached nebula clouds, and dipole field-line loops are built here as
   pure render-shape maps. No GL calls."
  (:require
   [clojure.math :as math]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.em :as em]
   [shape.spatial :as sp]
   [infra.render.units :as units]))

;; --- Fog particles ----------------------------------------------------------

(defn- fog-particle-size
  "Screen-space particle size for a fog sample. Lower-density samples represent
   a larger sampled volume so they read bigger and fainter; higher-density cores
   read smaller and brighter. `support` is the SPH smoothing/support radius in
   render units."
  [support density-norm rng]
  (let [s (double (or support 1.0))
        d (double (or density-norm 0.5))
        base (* s (+ 6.0 (* 16.0 (- 1.0 d))))]
    (max 2.0 (+ base (* 4.0 (.nextDouble rng))))))

(defn- nebula-fog*
  "Actual particle cloud generation; split out so `nebula-fog` can cache."
  [{:keys [center extent color density support]} particle-count seed]
  (let [[cx cy cz] center
        rng (java.util.Random. (long (or seed 1)))
        dens (double (or density 0.5))
        sup  (double (or support extent 1.0))]
    (mapv
     (fn [_]
       (let [theta (* 2 math/PI (.nextDouble rng))
             phi   (math/acos (dec (* 2 (.nextDouble rng))))
             r     (* (double extent) (math/sqrt (.nextDouble rng)))]
         {:position [(+ cx (* r (math/sin phi) (math/cos theta)))
                     (+ cy (* r (math/sin phi) (math/sin theta)))
                     (+ cz (* r (math/cos phi)))]
          :color color
          :size  (fog-particle-size sup dens rng)
          :density (float dens)
          :render-mode :particle}))
     (range particle-count))))

(def ^:private particle-cache
  "Cache keyed by [eid seed count] → immutable particle cloud."
  (atom {}))

(defn- particle-cache-key
  "Stable key for a nebula clump's cached particle cloud."
  [eid seed cnt]
  [eid seed cnt])

(defn- cache-match? [cached params]
  (= (select-keys cached [:center :extent :support :color :density])
     (select-keys params [:center :extent :support :color :density])))

(defn nebula-fog
  "Soft fog puffs through a clump, DETERMINISTIC in `seed` so the cloud is stable
   frame-to-frame. Generated clouds are cached per-entity."
  [{:keys [center extent color seed density support] :as params}]
  (let [eid   (long (or seed 1))
        cnt   (int (or (:count params) 1))
        ckey  (particle-cache-key eid seed cnt)
        params {:center center :extent extent :support support
                :color color :density density}]
    (if-let [cached (get @particle-cache ckey)]
      (if (cache-match? cached params)
        (:particles cached)
        (let [fresh (nebula-fog* params cnt seed)]
          (swap! particle-cache assoc ckey (assoc params :particles fresh))
          fresh))
      (let [fresh (nebula-fog* params cnt seed)]
        (swap! particle-cache assoc ckey (assoc params :particles fresh))
        fresh))))

(defn field-line
  "Two endpoints for a clump's magnetic field line in render units. Brightness
   rises with field magnitude relative to the seed field. nil when no field."
  [center extent b-field]
  (when (and b-field (pos? (sp/len b-field)))
    (let [mag  (sp/len b-field)
          dir  (sp/v* b-field (/ 1.0 mag))
          half (sp/v* dir (* (double extent) 1.6))
          p0   (sp/v- center half)
          p1   (sp/v+ center half)
          glow (max 0.3 (min 1.0 (+ 0.3 (* 0.25 (math/log10
                                                 (+ 1.0 (/ mag em/default-nebula-field)))))))
          color [(* 0.45 glow) (* 0.85 glow) (* 1.0 glow)]]
      [{:position (vec p0) :color color :size 1.0 :render-mode :line}
       {:position (vec p1) :color color :size 1.0 :render-mode :line}])))

;; --- Field-line streamlines -------------------------------------------------

(defn- unit-vec [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) [0.0 0.0 1.0])))

(defn- any-perp [n]
  (let [a (if (> (abs (double (nth n 2))) 0.9) [1.0 0.0 0.0] [0.0 0.0 1.0])]
    (unit-vec (sp/cross n a))))

(defn- segments-of
  "Adjacent points of a render-space polyline → :line shapes."
  [pts color]
  (vec (mapcat (fn [a b]
                 [{:position (vec a) :color color :size 1.0 :render-mode :line}
                  {:position (vec b) :color color :size 1.0 :render-mode :line}])
               pts (rest pts))))

(defn field-line-shapes
  "Render the magnetic field as visible dipole loops, one set per body."
  [ctx world]
  (let [sources (->> (em/field-sources world)
                     (filter #(pos? (sp/len (:moment %))))
                     (sort-by #(- (sp/len (:moment %))))
                     (take 12))
        nseg 22]
    (vec (mapcat
          (fn [{:keys [position moment eid]}]
            (let [axis (unit-vec moment)
                  e1   (any-perp axis)
                  e2   (sp/cross axis e1)
                  rpos (units/world->render ctx position)
                  rr   (max 0.6 (units/phys->body-render-radius ctx
                                                                (ecs/get-component world eid c/radius)))
                  color [0.45 0.8 1.0]]
              (vec (mapcat
                    (fn [[shell az]]
                      (let [ca (math/cos (math/to-radians az))
                            sa (math/sin (math/to-radians az))
                            rdir (sp/v+ (sp/v* e1 ca) (sp/v* e2 sa))
                            pts (for [i (range (inc nseg))]
                                  (let [th (+ 0.12 (* (- math/PI 0.24) (/ (double i) nseg)))
                                        r  (* shell rr (math/sin th) (math/sin th))]
                                    (sp/v+ rpos
                                           (sp/v+ (sp/v* rdir (* r (math/sin th)))
                                                  (sp/v* axis  (* r (math/cos th)))))))]
                        (segments-of pts color)))
                    (for [shell [1.6 2.4 3.4]
                          az [0.0 60.0 120.0 180.0 240.0 300.0]] [shell az])))))
          sources))))
