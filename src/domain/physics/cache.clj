(ns domain.physics.cache
  "Public facade for the persistent physics caches.

   The implementation is split into `domain.physics.cache.neighbor` (the
   persistent SPH/MHD neighbor list) and `domain.physics.cache.soa` (the
   transient structure-of-arrays gravity/kinematics cache). This namespace
   re-exports the public API so existing callers can keep using
   `domain.physics.cache`."
  (:require
   [domain.physics.cache.neighbor :as neighbor]
   [domain.physics.cache.soa :as soa]))

(def cache-active?
  "Particles that participate in the shared SPH density/pressure or EM Lorentz pair loops."
  neighbor/cache-active?)

(def neighbor-cache-entry?
  "Predicate: does `value` satisfy the neighbor-cache entry schema?"
  neighbor/neighbor-cache-entry?)

(def displacement-tolerance
  "Fraction of smoothing length a particle may move before its neighbor set must be requeried."
  neighbor/displacement-tolerance)

(def max-displacement-squared
  "Return the squared displacement threshold for smoothing length `h` and `tolerance`."
  neighbor/max-displacement-squared)

(def cache-entry-valid?
  "True when `prev-entry`'s neighbor and nearest-neighbor identities can be reused for `eid` in `world`."
  neighbor/cache-entry-valid?)

(def neighbor-with-gradients
  "Attach pressure and curl gradients to a spatial-index item."
  neighbor/neighbor-with-gradients)

(def rebuild-neighbor-cache
  "Build or refresh a persistent `:genesis/neighbor-cache` onto `world`."
  neighbor/rebuild-neighbor-cache)

(def build-neighbor-cache
  "Build a fresh `:genesis/neighbor-cache` onto `world`."
  neighbor/build-neighbor-cache)

(def strip-neighbor-cache
  "Remove `:genesis/neighbor-cache` from `world`."
  neighbor/strip-neighbor-cache)

(def build-physics-soa
  "Build and assoc a fresh `:genesis/physics-soa` SoA cache onto `world`."
  soa/build-physics-soa)

(def strip-physics-soa
  "Remove the transient `:genesis/physics-soa` from `world`."
  soa/strip-physics-soa)

(def predicted-position-fn
  "Return `(fn [eid] position)` reading the drift-predicted position from the SoA."
  soa/predicted-position-fn)
