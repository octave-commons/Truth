(ns law.interior
  "Tuned constants for the macro geology field seed — the honest first models
   of design docs/designs/planetary-voxel-substrate.md §4/§7 (layer-thickness
   translation is a documented §7 design gap; these are the pinned knobs of
   the model `domain.interior` implements, NOT a differentiation simulation).

   Units: SI throughout (kg, m, K, m/s). Constants only: no behavior, no
   schemas (those live in `law.voxel`). Every constant here is deliberately
   coarse and documented as such — no false precision.")

;; --- Reference densities (uncompressed, kg/m³) --------------------------------

(def ^:const core-density-reference
  "Reference density of a differentiated Fe-Ni metallic core (kg/m³).
   Used only to size the core's radius from its mass; the emitted layer's
   `:density` is the mass/volume-consistent value."
  7900.0)

(def ^:const crust-density-reference
  "Reference density of a basaltic/continental crust (kg/m³). Sets the rocky
   crust's mass from its shell volume; the emitted layer's `:density` is the
   mass/volume-consistent value."
  2800.0)

(def ^:const ice-density-reference
  "Reference density of water-ice shell material (kg/m³, ~ice Ih)."
  1000.0)

;; --- Layer template (design §7 gap: no specified thickness formula) -----------

(def ^:const core-iron-fraction-reference
  "Fe+Ni mass share of a differentiated metallic core. The core-mass model is
   f_core = (bulk Fe+Ni fraction) / this — i.e. all the body's iron sank to
   the core. Earth's ~0.32 bulk Fe+Ni / 0.85 ≈ 0.38, bracketing the true
   ~0.325 core mass fraction: the right order, honestly coarse."
  0.85)

(def ^:const core-mass-fraction-bounds
  "Clamp [min max] on the derived core mass fraction: keeps degenerate
   iron-poor (icy) and iron-rich (Mercury-like) bulks inside a differentiable
   stack rather than inventing precision the model doesn't have."
  [0.05 0.60])

(def ^:const crust-thickness-reference-m
  "Reference solid-crust thickness (m) at the `:temperate` band — 30 km,
   Earth-continental order. Scaled per thermal band by
   `crust-thickness-band-factor` (design §4: hot bands start thin/absent,
   frozen bands thick and sluggish)."
  3.0e4)

(def ^:const crust-thickness-band-factor
  "Crust-thickness multiplier per `:thermal-band` (design §4 table):
   `:hot` worlds start near `:env/magma-ocean` with a thin or absent solid
   crust; `:frozen` worlds start with a thick, low-activity crust."
  {:hot 0.25 :warm 0.75 :temperate 1.0 :cold 2.0 :frozen 3.0})

(def ^:const shell-max-radius-fraction
  "A crust/ice shell may span at most this fraction of the body radius —
   guards the shell-volume solve on small bodies where the reference
   thickness would otherwise swallow the planet."
  0.5)

(def ^:const ice-shell-max-volume-fraction
  "An icy world's surface ice shell may occupy at most this fraction of the
   body's total volume. Composition says how much ice the body HAS; this cap
   says how much of it can sit at surface-ice density — ice beyond the cap
   is folded into the mantle's mass (physically: high-pressure ice phases
   and ice-rock mixtures at depth), keeping the layer solve volume-
   consistent on ice-rich bodies instead of letting the shell swallow the
   planet."
  0.5)

(def ^:const core-max-remaining-mass-fraction
  "The core may claim at most this fraction of the mass remaining after the
   shell — keeps the mantle non-empty on icy worlds whose ice shell is a
   large mass share (layer masses stay positive, no invented material)."
  0.9)

;; --- Mean density (for radius/mass derivation from :surface-gravity) ----------

(def ^:const mean-density-reference
  "Uncompressed-ish mean density per `:material-class` (kg/m³): rocky 5500
   (Earth's actual mean, no compression model), icy 2000 (ice-over-rock),
   mixed 3500 (interpolation). Adjusted by the iron lever below."
  {:rocky 5500.0 :icy 2000.0 :mixed 3500.0})

(def ^:const iron-density-lever
  "kg/m³ of mean-density adjustment per unit bulk Fe+Ni mass fraction above
   or below the material class's OWN reference share
   (`iron-fraction-reference`): iron-rich bodies are denser, so the same
   surface gravity implies a smaller radius. One linear lever, no
   compression physics — the honest first model."
  3000.0)

(def ^:const iron-fraction-reference
  "Bulk Fe+Ni mass fraction each material class's mean-density reference
   already assumes — the iron lever is ZERO-CENTERED PER CLASS around these
   shares, not around one absolute value: rocky 0.30 (~Earth), icy 0.05
   (metal-poor ice/rock mixture), mixed 0.15 (interpolation). Without
   per-class centering, a metal-poor icy body's derived density is dragged
   below every real icy body (Europa 3010, Enceladus 1610 kg/m³)."
  {:rocky 0.30 :icy 0.05 :mixed 0.15})

;; --- Thermal seed ---------------------------------------------------------------

(def ^:const thermal-band-midpoint-k
  "Fallback surface temperature (K) per `:thermal-band`, used only when the
   candidate's `:equilibrium-temperature` is nil (the degenerate unbound-
   orbit case the schema permits). Band edges per
   `law.stellar.schema/thermal-band-schema`."
  {:frozen 100.0 :cold 200.0 :temperate 300.0 :warm 400.0 :hot 600.0})

(def ^:const geothermal-gradient-k-per-m
  "Linear near-surface geothermal gradient (K/m) — 15 K/km, continental-Earth
   order. Applied from the surface down to each layer's mid-depth, capped by
   `interior-temperature-ceiling-k`. A linear profile with a band cap is the
   documented first model, not an adiabat."
  0.015)

(def ^:const interior-temperature-ceiling-k
  "Interior-temperature cap (K) per `:thermal-band`: the most the linear
   gradient may add before the band's melt regime takes over. `:hot` worlds
   sit near a magma ocean; `:frozen` worlds stay sluggish (design §4)."
  {:hot 4000.0 :warm 2500.0 :temperate 2000.0 :cold 1200.0 :frozen 800.0})

;; --- Tectonics / convection ------------------------------------------------------

(def ^:const plate-count-reference
  "Plate count at reference surface gravity (`surface-gravity-reference`) —
   Earth's ~7 major plates. Scales with sqrt(g/g_ref) (higher g fragments
   the lithosphere), clamped to `plate-count-bounds`."
  7.0)

(def ^:const plate-count-bounds
  "Clamp [min max] on seeded plate count."
  [3 14])

(def ^:const surface-gravity-reference
  "Reference surface gravity (m/s²) for tectonic scaling — Earth's 9.81."
  9.81)

(def ^:const plate-speed-reference-m-per-s
  "Reference plate speed (m/s) at activity factor 1 — 1e-9 ≈ 3.2 cm/yr,
   Earth order. Multiplied by `tectonic-activity-band-factor` and a clamped
   sqrt(g/g_ref) lever."
  1.0e-9)

(def ^:const tectonic-activity-band-factor
  "Tectonic/convection activity multiplier per `:thermal-band` (design §4:
   hot bands vigorous, frozen bands nearly inert)."
  {:hot 3.0 :warm 2.0 :temperate 1.0 :cold 0.3 :frozen 0.05})

(def ^:const plate-speed-gravity-lever-bounds
  "Clamp [min max] on the sqrt(g/g_ref) plate-speed lever."
  [0.2 3.0])

(def ^:const convection-speed-reference-m-per-s
  "Reference mantle-convection flow speed (m/s) at activity factor 1 —
   2e-9 ≈ 6 cm/yr, upper-mantle order."
  2.0e-9)

(def ^:const convection-cell-radius-fraction
  "Convection-cell radius as a fraction of body radius — mantle-scale cells,
   a quarter of the planet."
  0.25)

;; --- Resource field --------------------------------------------------------------

(def ^:const ore-enrichment-factor
  "Fe/Ni concentration multiplier in convergent-margin resource cells
   (design §7.4's qualitative steer: 'richer near convergent margins') —
   renormalized after scaling, so this is a RELATIVE enrichment, never new
   mass."
  3.0)

(def ^:const hotspot-sulfur-enrichment-factor
  "Sulfur concentration multiplier in hotspot (upwelling) resource cells —
   volcanic outgassing concentrates chalcophiles. Relative, renormalized."
  2.0)

(def ^:const resource-cell-weights
  "Crust-mass partition weights per resource-cell kind: convergent margins
   hold the most ore-bearing mass, hotspots next, polar ice less, background
   regolith the remainder. Weights only — masses derive from the crust mass,
   so the field conserves mass by construction."
  {:convergent 3.0 :hotspot 2.0 :polar-ice 1.5 :background 1.0})

(def ^:const resource-cell-radius-fraction
  "Resource-cell region radius as a fraction of body radius (design §3's
   coarse resource field: cells are regional, resolving to concrete voxels
   only under focus)."
  0.15)

;; --- Deterministic layout ---------------------------------------------------------

(def ^:const golden-angle-radians
  "The golden angle π(3−√5) ≈ 2.399963 — the Fibonacci-spiral step used for
   all deterministic point layouts (plate centres, convection cells). Spiral
   spacing is well-distributed for any count without any PRNG: the seed's
   determinism contract is BY CONSTRUCTION, no randomness anywhere."
  2.39996322972865332)
