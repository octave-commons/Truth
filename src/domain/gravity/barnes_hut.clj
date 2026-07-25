(ns domain.gravity.barnes-hut
  "Barnes–Hut n-body gravity in 3D, using an octree over AABBs.
   Thin facade over `domain.gravity.barnes-hut.tree` and
   `domain.gravity.barnes-hut.force`."
  (:require
   [domain.gravity.barnes-hut.force :as force]
   [domain.gravity.barnes-hut.tree :as tree]))

(def build-tree
  "Build a Barnes–Hut octree from a seq of Body records."
  tree/build-tree)

(def build-tree-from-soa
  "Build a Barnes–Hut octree directly from the `:genesis/physics-soa` arrays."
  tree/build-tree-from-soa)

(def internal-node?
  "True if `node` is an internal Barnes-Hut tree node."
  tree/internal-node?)

(def leaf-node?
  "True if `node` is a leaf Barnes-Hut tree node."
  tree/leaf-node?)

(def acceleration
  "Compute gravitational acceleration on `body` from all bodies in `tree`."
  force/acceleration)

(def acceleration-for-soa
  "Gravitational acceleration for every entity in the SoA cache."
  force/acceleration-for-soa)

(def default-softening
  "Plummer softening length."
  force/default-softening)
