(ns domain.ecs.components
  "Canonical component type keywords for Gates of Truth.
   No logic here — just the vocabulary.
   Every system queries these exact keywords.")

;; --- Spatial ----------------------------------------------------------------
(def position  :component/position)
(def velocity  :component/velocity)
(def mass      :component/mass)
(def radius    :component/radius)

;; --- Orbital ----------------------------------------------------------------
(def elements  :component/elements)
(def orbit-ref :component/orbit-ref)

;; --- Physical ---------------------------------------------------------------
(def force-accum :component/force-accum)
(def body-kind   :component/body-kind)

;; --- Atmosphere -------------------------------------------------------------
(def atmos-cell  :component/atmos-cell)

;; --- Biome ------------------------------------------------------------------
(def biome-cell  :component/biome-cell)

;; --- Civilization -----------------------------------------------------------
(def civilization :component/civilization)
(def territory    :component/territory)

;; --- Render -----------------------------------------------------------------
(def renderable   :component/renderable)
(def cell-id      :component/cell-id)

;; --- Myth engine ------------------------------------------------------------
(def facet-vector :component/facet-vector)
(def favor        :component/favor)
(def scribe       :component/scribe)
