(ns domain.em
  "Electromagnetic / MHD-lite layer for Phase 0.

   This namespace is a thin backward-compatible facade over the sub-namespaces:
     - domain.em.field         : field propagation, dipole superposition, flux freezing
     - domain.em.lorentz       : Lorentz force, magnetic braking, curl estimates
     - domain.em.magnetosphere : magnetosphere coupling

   Prefer requiring the specific sub-namespace for new code; domain.em is kept
   for existing callers and tests."
  (:require
   [domain.em.field :as field]
   [domain.em.lorentz :as lorentz]
   [domain.em.magnetosphere :as magneto]))

(def ^{:doc "Magnetic pressure of a field, P_B = |B|² / (2μ₀)  (SI). Pascals."
       :arglists '([b-field])}
  magnetic-pressure field/magnetic-pressure)

(def ^{:doc "Alfvén speed v_A = |B| / √(μ₀ρ)  (SI). m/s. Zero field → zero speed."
       :arglists '([b-field density])}
  alfven-speed field/alfven-speed)

(def ^{:doc "μ₀/4π exactly (T·m/A)"
       :const true}
  mu0-over-4pi field/mu0-over-4pi)

(def ^{:doc "Magnetic dipole moment m (A·m²) of a body whose surface field magnitude is\n   |b-field| at radius `radius`, aligned with `b-field`. From the on-axis dipole\n   relation B_pole = μ₀|m|/(2π R³) ⇒ m = (2π R³/μ₀)·B."
       :arglists '([b-field radius])}
  dipole-moment field/dipole-moment)

(def ^{:doc "Field (T) produced at world point `p` by a dipole of moment `m` at `src`:\n   B = (μ₀/4π)[3(m·d̂)d̂ − m]/|d|³, d = p − src. [0 0 0] at the singularity."
       :arglists '([m src p])}
  dipole-field-at field/dipole-field-at)

(def ^{:doc "Superposed magnetic field at world point `p`: Σ over `sources` of each dipole's\n   field, plus a uniform `background`. `sources` is a seq of {:moment :position}.\n   THIS is the field bodies feel and that field-line tracing follows — the\n   interactions between bodies' fields are this sum, emergent, not hard-coded."
       :arglists '([p sources background])}
  net-field-at field/net-field-at)

(def ^{:doc "Like `net-field-at` but EXCLUDING the source whose position equals `self-pos`\n   (a body does not torque on its own field) — the field a body sees from all the\n   OTHERS. Background is omitted (a uniform field exerts no net force, only torque\n   handled separately)."
       :arglists '([self-pos sources])}
  external-field-at field/external-field-at)

(def ^{:doc "Dipole sources from every resolved body (their fields are amplified enough to\n   matter). Each → {:moment :position :eid}. Diffuse :nebula gas is left out as a\n   source (its weak seeded field is the background), though it still carries B."
       :arglists '([world])}
  field-sources field/field-sources)

(def ^{:doc "Ideal induction under compression: as a clump's density rises from\n   `old-density` to `new-density`, frozen-in flux amplifies the field.\n\n   For isotropic spherical contraction the scaling is B ∝ ρ^(2/3). For collapse\n   along field lines (flux-conserving in the perpendicular plane) B ∝ ρ. The\n   `anisotropy` parameter interpolates: 0 = isotropic, 1 = fully along B.\n   Direction is preserved. Capped at law.field/max-b-field."
       :arglists '([b-field old-density new-density]
                   [b-field old-density new-density anisotropy])}
  flux-freeze field/flux-freeze)

(def ^{:doc "Central pressure of a self-gravitating uniform sphere, P ≈ GM²/((4/3π)R⁴).\n   Duplicated from domain.stellar's formula to keep this namespace free of a\n   dependency on the stellar domain (em is upstream of stellar)."
       :arglists '([mass radius])}
  self-gravity-pressure field/self-gravity-pressure)

(def ^{:doc "True if magnetic pressure can hold a clump against its own self-gravity,\n   i.e. P_B ≥ P_grav. Under flux freezing both scale as 1/r⁴, so this ratio is\n   set at seeding by the clump's mass-to-flux — the magnetic critical-mass idea.\n   A supported (sub-critical) clump resists collapse; an unsupported\n   (super-critical) one falls in. Massive cores are strongly super-critical, so\n   this correctly does NOT stop them — magnetic support matters for small,\n   strongly-magnetized clumps, not for the central core."
       :arglists '([{:keys [b-field mass radius]}])}
  magnetically-supported? field/magnetically-supported?)

(def ^{:doc "Floor on the per-tick resistive-decay factor: a body keeps at least this much\n   of its flux each tick. The decay timescale r²/η can fall below the (Myr-scale)\n   `dt` for a compact core, which would annihilate the flux in a single step and\n   defeat flux freezing — the same large-dt coupling hazard as the stellar wind.\n   Capping the per-tick loss keeps the decay gentle and dt-robust, so flux\n   freezing can amplify a contracting core's field (the physically observed\n   outcome: protostars have strong, amplified fields)."
       :const true}
  min-flux-retention field/min-flux-retention)

(def ^{:doc "Non-ideal flux loss over dt: dB/dt = -ηB/L², with magnetic diffusivity η and\n   length scale L≈radius. Reduced proxy for Ohmic dissipation / ambipolar\n   diffusion. Effect ~ r²/η: ≈no decay in diffuse gas, gentle in dense cores. The\n   per-tick factor is floored at `min-flux-retention` so a large dt cannot wipe a\n   compact core's flux in one step. `eta` is a small magnetic diffusivity tuned so\n   flux freezing dominates during collapse (real cores retain & amplify field)."
       :arglists '([b-field radius dt]
                   [b-field radius dt eta])}
  resistive-decay field/resistive-decay)

(def ^{:doc "Tesla. Coherent large-scale nebular field, ~nT — the molecular-cloud range."
       :const true}
  default-nebula-field field/default-nebula-field)

(def ^{:doc "An initial magnetic field vector for a clump: a coherent large-scale field\n   aligned with the nebula's rotation axis (z), the configuration observed in\n   real molecular clouds where polarization maps show ordered fields roughly\n   along the spin axis."
       :arglists '([]
                   [magnitude])}
  seed-field field/seed-field)

(def ^{:doc "Double-buffer write-set system: SOLE writer of b-field.\n\n    Magnetic flux Φ = B·R² is frozen into a body when it condenses and conserved\n    as it contracts, so B = Φ/R² amplifies as Structure shrinks the radius — ideal\n    flux freezing (B ∝ 1/R² ∝ ρ^{2/3}) — while Φ itself decays by Ohmic/ambipolar\n    resistivity (real only in dense cores). Diffuse :nebula gas keeps its seeded\n    field with the same light resistive decay. Replaces collapse's flux-freezing\n    and em's b-field decay; Φ (frozen-flux) is the reference that turns the\n    amplification into a derivation from the radius Structure owns."
       :arglists '([dt])}
  field-system field/field-system)

(def ^{:doc "Estimate (∇ × B) at a clump from neighboring b-field vectors using an SPH-like\n   curl formula. Returns a vector in T/m. Zero neighbors → zero curl.\n\n   Uses the symmetric SPH curl: (∇ × B)_i = Σ_j m_j/ρ_j (B_i - B_j) × ∇_i W_ij.\n   If `gradients` is supplied it must align with `neighbors` and contain the\n   pre-computed ∇_i W vectors; otherwise the gradient is recomputed per neighbor."
       :arglists '([b-field density position neighbors]
                   [b-field density position neighbors gradients])}
  curl-estimate lorentz/curl-estimate)

(def ^{:doc "Lorentz force density f = (∇ × B) × B / μ₀  (SI). N/m³. Always perpendicular\n   to B. Uses the SPH curl estimate above on the N-body substrate."
       :arglists '([b-field curl-b])}
  lorentz-force-density lorentz/lorentz-force-density)

(def ^{:doc "Lorentz acceleration a = f/ρ = (∇ × B) × B / (μ₀ ρ)  (SI). m/s²."
       :arglists '([b-field curl-b density])}
  lorentz-acceleration lorentz/lorentz-acceleration)

(def ^{:doc "Torque density τ = r × f about the origin, where f is the Lorentz force\n   density. N/m."
       :arglists '([position lorentz-force])}
  magnetic-torque lorentz/magnetic-torque)

(def ^{:doc "Cap on magnetic-braking angular-momentum loss, as a fraction of L removed per\n   second of SIM-TIME (so the per-step cap is this × dt). ~1/τ_brake with a\n   braking timescale τ_brake ≈ 1e14 s (free-fall scale of a molecular cloud);\n   gentle enough that the cloud's spin survives the collapse rather than being\n   braked away in the first seconds of real time."
       :const true}
  braking-fraction-per-time lorentz/braking-fraction-per-time)

(def ^{:doc "Compute the magnetic braking torque on a rotating clump: τ along the rotation\n   axis. The field is assumed to be primarily poloidal (threading the rotation\n   axis); differential rotation wraps it into a toroidal component whose tension\n   brakes the spin. This is a phenomenological per-body reduction of the full\n   MHD braking torque.\n\n   Returns the angular momentum REMOVED this step (a vector aligned with\n   `rotation-axis`), proportional to B² ρ^(-1/2) r³ ω · dt — the characteristic\n   Alfvén-wave torque integrated over the timestep `dt`. Pacing by sim-time (× dt)\n   rather than a per-tick fraction is essential now the tick rate is a fixed\n   60 Hz: a per-tick cap would shed angular momentum ~38× faster than the old\n   variable cadence, braking the cloud's rotation away in seconds."
       :arglists '([{:keys [mass radius density b-field angular-momentum rotation-axis ionization]} dt])}
  magnetic-braking-torque lorentz/magnetic-braking-torque)

(def ^{:doc "Lorentz acceleration a = (∇×B)×B/(μ₀ρ), computed only when magnetic pressure\n   or tension is locally significant (see `law.field/mhd-regime?`). The magnitude\n   is capped at the Alfvén limit v_A² / R so the force cannot accelerate a parcel\n   past the characteristic magnetic scale in one step."
       :arglists '([data curl-b])}
  capped-lorentz-acceleration lorentz/capped-lorentz-acceleration)

(def ^{:doc "The EM tick step. Computes:\n     1. Lorentz acceleration a = (∇×B)×B / (μ₀ ρ) stored on c/hydro-accel so\n        the orbital integrator applies it alongside gravity and hydro.\n     2. Magnetic braking torque applied to c/angular-momentum and c/spin.\n     3. Resistive flux decay applied to c/b-field.\n\n   Diffuse clumps keep their field essentially unchanged; dense cores slowly\n   shed flux — the design's non-ideal hook.\n\n   Reads the shared spatial tree from :genesis/spatial-tree and filters query\n   results to EM-active entities."
       :arglists '([dt])}
  em-system lorentz/em-system)

(def ^{:doc "Double-buffer write-set system: Lorentz acceleration a = (∇×B)×B/(μ₀ρ) and\n   magnetic-braking torque ΔL for every EM-active clump. Reads the shared spatial\n   tree from :genesis/spatial-tree (built once per tick by domain.spatial.index),\n   filters query results to EM-active entities. Writes accel.lorentz and\n   torque.em; the integrator owns angular-momentum/spin and adds the torque."
       :arglists '([dt])}
  lorentz-acceleration-system lorentz/lorentz-acceleration-system)

(def ^{:doc "Double-buffer write-set system: SOLE writer of c/magnetosphere. For each\n   :planet, finds nearby ionized wind/CME parcels and computes magnetosphere\n   compression — standoff distance + compression factor from the parcels'\n   one-tick-stale ram pressure. A compressed magnetosphere (small standoff)\n   means more atmospheric exposure. Emits only the cells that CHANGED. Each\n   phase is profiled when `:genesis/profile-subsystems?` is enabled."
       :arglists '([])}
  magnetosphere-coupling-system magneto/magnetosphere-coupling-system)
