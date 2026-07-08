(ns domain.stellar.merge
  "Collision and merge handling for stellar bodies.
   The stellar-merge-handler decides whether a collision shatters or inelastically
   merges the smaller body, and emits the appropriate lifecycle packets."
  (:require
   [clojure.math :as math] [law.stellar                  :as law]
   [domain.stellar.thermodynamics :as thermo]
   [domain.ecs.core              :as ecs]
   [domain.ecs.components        :as c]
   [shape.spatial                :as sp]))

(defn- shatter-bodies
  "Brittle collision response: the smaller body breaks into two :planetesimal fragments
   (mass AND momentum conserved — each fragment is half the mass with symmetric
   ± split velocities), the larger body survives. The split axis is deterministic
   (perpendicular to the impact line), fragments are placed clear of the larger
   body, and over the Myr-scale dt they immediately disperse — so this does not
   cascade. Makes law.stellar/malleability load-bearing (cold brittle bodies
   shatter; hot molten ones fall through to the merge path).

   Emits c/spawn-request-shatter (handled by materialize-lifecycle) and
   c/consumed-merge instead of inline spawning — no single-writer violation."
  [world big small ms*]
  (let [ms      (double (:mass ms*))
        rs      (double (:radius ms*))
        pos     (ecs/get-component world small c/position)
        vs      (ecs/get-component world small c/velocity)
        big-pos (ecs/get-component world big c/position)
        rl      (double (or (ecs/get-component world big c/radius) 0.0))
        away    (let [d (sp/v- pos big-pos) l (sp/len d)]
                  (if (pos? l) (sp/v* d (/ 1.0 l)) [1.0 0.0 0.0]))
        perp    (let [rf (if (> (abs (double (nth away 0))) 0.9)
                           [0.0 1.0 0.0] [1.0 0.0 0.0])
                      x   (sp/cross away rf) l (sp/len x)]
                  (if (pos? l) (sp/v* x (/ 1.0 l)) [0.0 1.0 0.0]))
        frag-m  (* 0.5 ms)
        frag-r  (* rs (math/cbrt 0.5))
        center  (sp/v+ big-pos (sp/v* away (* 1.2 (+ rl frag-r)))) ;; clear of big
        sep     (* 1.5 frag-r)
        dvs     (min 50.0 (* 0.1 (sp/len vs)))   ;; tiny symmetric split (momentum-conserving)
        spec    (fn [s] {:position     (sp/v+ center (sp/v* perp (* s sep)))
                         :velocity     (sp/v+ vs (sp/v* perp (* s dvs)))
                         :mass         frag-m :radius frag-r :matter-state :planetesimal
                         :composition  (:composition ms*)
                         :temperature  (:temperature ms*)})]

    (cond-> world
      true (ecs/put-component big c/spawn-request-shatter
                              [(spec 1.0) (spec -1.0)])
      true (ecs/put-component small c/consumed-merge true))))

(defn- merge-packet
  "Build the c/absorb-merge packet for the smaller body in a collision."
  [world small big ms*]
  (let [r-small (ecs/get-component world small c/position)
        r-big (ecs/get-component world big c/position)
        r-rel (sp/v- r-small r-big)
        vs (ecs/get-component world small c/velocity)
        va (ecs/get-component world big c/velocity)
        v-rel (sp/v- vs va)
        ms (double (:mass ms*))
        Ls (or (ecs/get-component world small c/angular-momentum)
               (thermo/orbital-angular-momentum ms
                                                (ecs/get-component world small c/position)
                                                vs))
        L-orbital-small (thermo/orbital-angular-momentum ms r-rel v-rel)
        L-small (sp/v+ Ls L-orbital-small)]
    {:mass ms
     :velocity vs
     :position r-small
     :angular-momentum L-small
     :composition (:composition ms*)
     :temperature (:temperature ms*)}))

(defn- shatter-or-merge
  "Route the collision to either the shatter path or the inelastic merge path."
  [world big small mb* ms*]
  (let [va (ecs/get-component world big c/velocity)
        vs (ecs/get-component world small c/velocity)
        t-cold (min (double (or (:temperature mb*) 0.0))
                    (double (or (:temperature ms*) 0.0)))
        budget (long (:genesis/max-resolved-bodies world 400))
        resolved (long (or (get-in world [:genesis/stats :resolved-count]) 0))]
    (if (and (> (double (:mass ms*)) law/shatter-min-mass)
             (< (law/malleability t-cold) law/shatter-malleability-max)
             (> (sp/len (sp/v- va vs)) law/shatter-dv-threshold)
             (or (<= budget 0) (< resolved budget)))
      ;; brittle body + hard impact → the smaller shatters into debris
      (shatter-bodies world big small ms*)
      ;; molten or gentle impact → inelastic merge (emit absorb-merge packet)
      (-> world
          (ecs/put-component big c/absorb-merge [(merge-packet world small big ms*)])
          (ecs/put-component small c/consumed-merge true)))))

(defn stellar-merge-handler
  "Collision handler that merges the smaller body into the larger AND blends
    their stellar state (mass-weighted composition, max temperature, conserved
    momentum AND angular momentum, volume-summed radius). Registered for
    :event/collision."
  [world event]
  (let [{:keys [eid-a eid-b]} (:payload event)]
    (if (and (ecs/alive? world eid-a) (ecs/alive? world eid-b))
      (let [a (thermo/entity->region world eid-a)
            b (thermo/entity->region world eid-b)
            ma (double (:mass a)) mb (double (:mass b))
            [big small mb* ms*] (if (>= ma mb) [eid-a eid-b a b] [eid-b eid-a b a])]
        (shatter-or-merge world big small mb* ms*))
      world)))
