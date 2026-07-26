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

(def neighbor-cache-entry?
  "Predicate: does `value` satisfy the neighbor-cache entry schema?"
  neighbor/neighbor-cache-entry?)

(def displacement-tolerance
  "Fraction of smoothing length a particle may move before its neighbor set must be requeried."
  neighbor/displacement-tolerance)

(def neighbor-with-gradients
  "Attach pressure and curl gradients to a spatial-index item."
  neighbor/neighbor-with-gradients)

(def build-neighbor-cache
  "Build a fresh `c/neighbor-cache` component for every hydro/EM-active entity.

   This is a convenience wrapper for tests and legacy callers; the production
   tick path uses `neighbor-cache-system` in the parallel fan-out."
  neighbor/build-neighbor-cache)

(def rebuild-neighbor-cache
  "Return a write-set `{c/neighbor-cache {eid entry}}` for the current tick.

   Used by `neighbor-cache-system` in the fan-out; tests and legacy callers
   should prefer `build-neighbor-cache`, which applies the write-set."
  neighbor/rebuild-neighbor-cache)

(def strip-neighbor-cache
  "Remove `c/neighbor-cache` from every entity in `world`."
  neighbor/strip-neighbor-cache)

(def neighbor-cache-system
  "Fan-out system that builds/refreshes per-entity `c/neighbor-cache` components."
  neighbor/neighbor-cache-system)

(def build-physics-soa
  "Build and assoc a fresh `:genesis/physics-soa` SoA cache onto `world`."
  soa/build-physics-soa)

(def strip-physics-soa
  "Remove the transient `:genesis/physics-soa` from `world`."
  soa/strip-physics-soa)

(def predicted-position-fn
  "Return `(fn [eid] position)` reading the drift-predicted position from the SoA."
  soa/predicted-position-fn)
