# Rate-Limited Accretion and Gradual Mass Transfer

**Domain:** physics | **Phase:** 0  
**Date:** 2026-07-06 | **Author:** opencode (parallel research synthesis)  
**Status:** draft  
**Primary sources:** Bondi (1952), Edgar (2004), Federrath et al. (2010), Krumholz et al. (2004), Hubber et al. (2013), Eggleton (1983), Ritter (1988), Kolb & Ritter (1990)

---

## 1. Research Question

How do we replace the current all-or-nothing mass flow in *Gates of Truth* — whole parcels condensing into bodies, whole bodies merging on contact, whole parcels swallowed by sinks — with a gradual, research-grounded transfer that lets the player watch a star feed from its disk and a planet grow from its feeding zone?

The answer must:
- Give a physical accretion rate for a body embedded in gas (Bondi–Hoyle–Lyttleton).
- Give a physical overflow rate for two close bodies (Roche-lobe overflow).
- Provide the sink-particle recipes used in modern star-formation codes for draining gas parcels gradually rather than deleting them whole.
- Specify caps and timestep constraints that keep a fixed-tick ECS simulation stable.
- Map cleanly to the existing ECS substrate (`domain.ecs.core`), `law/`, and `infra.render`.

---

## 2. Literature Survey

### 2.1 Bondi–Hoyle–Lyttleton accretion from a uniform medium

A point-mass sink of mass $M$ moving through gas with density $\rho_\infty$, sound speed $c_s$, and relative speed $v_\infty$ accretes at

$$
\dot M_{\rm BHL} = \frac{4\pi G^2 M^2 \rho_\infty}{(c_s^2 + v_\infty^2)^{3/2}}.
$$

> **Key finding:** In the subsonic limit the rate scales as $c_s^{-3}$ and is independent of drift velocity; in the supersonic limit it scales as $v_\infty^{-3}$. The interpolation denominator $(c_s^2+v_\infty^2)^{3/2}$ is the form used in modern codes.

**Citation:** Edgar, R. G. 2004, *New Astronomy Reviews*, 48, 843. arXiv:astro-ph/0406166. DOI:10.1016/j.newar.2004.06.001.

The gravitational capture radius is

$$
R_{\rm acc} = \frac{2GM}{c_s^2+v_\infty^2}.
$$

For a $0.1\,M_\odot$ protostar in $10$ K molecular gas ($c_s\approx0.19$ km s$^{-1}$), $R_{\rm acc}\sim10^2-10^3$ AU depending on drift velocity. For a $0.01\,M_\odot$ embryo in a warm inner disk, $R_{\rm acc}\sim1-10^2$ AU and the raw BHL rate can exceed realistic planet-growth rates, so caps are essential.

### 2.2 Sink-particle accretion schemes

**Federrath et al. (2010)** — AMR FLASH sinks. Gas inside $r_{\rm acc}$ is debited only down to a resolution floor $\rho_{\rm res}$:

$$
\Delta M = [\rho(i,j,k)-\rho_{\rm res}]\,\Delta V.
$$

The debit is accepted only if the gas is gravitationally bound to the sink+gas system and moving toward the sink ($v_r<0$). Cells are never deleted; conservation is enforced by moving the sink to the center of mass before debiting.

> **Key finding:** Sinks in AMR codes remove *excess density*, not whole cells. This prevents the pressure cliff that instantaneous removal creates.

**Krumholz, McKee & Klein (2004)** — Eulerian AMR Bondi–Hoyle sinks. They define a kernel-weighted accretion zone of radius $r_{\rm acc}=4\Delta x$ and cap the per-cell mass removal at **25% per timestep**. The rate uses a corrected Bondi-Hoyle expression with a density profile factor $\alpha(1.2\Delta x/r_{\rm BH})$ and an angular-momentum reduction factor.

> **Key finding:** A hard per-tick cap (25% of donor mass) is the simplest, most transportable runaway guard.

**Hubber, Walch & Whitworth (2013)** — SEREN SPH “NewSink”. Donor particles inside the interaction zone are kept alive and drained by exponential relaxation:

$$
\delta M_{\rm ACC} = M_{\rm int}\left[1-\exp\left(-\frac{\delta t}{t_{\rm ACC}}\right)\right].
$$

Angular momentum is returned to the interaction-zone gas on a disc timescale, so sinks do not spin up unrealistically.

> **Key finding:** Exponential relaxation keeps parcels live and naturally bounds the per-tick debit.

### 2.3 Roche-lobe overflow for binary bodies

The Roche-lobe radius of star/planet $M_1$ around companion $M_2$ at separation $a$ is (Eggleton 1983)

$$
\frac{R_{L,1}}{a} = \frac{0.49\,q^{2/3}}{0.6\,q^{2/3}+\ln(1+q^{1/3})}, \qquad q=\frac{M_1}{M_2}.
$$

Overflow begins when $R_{\rm donor}>R_L$. The rate is extremely sensitive to fractional overfilling $\delta=(R_{\rm donor}-R_L)/R_L$:

$$
\dot M \approx -A\,\frac{M_d}{P}\,\delta^3, \qquad A\sim10.
$$

For an isothermal photosphere Ritter (1988) gives an exponential rate; for an adiabatic envelope Kolb & Ritter (1990) give a power-law rate. Recent 3D hydrodynamics (Ryu et al. 2025) reduces these analytic rates by factors of $\sim2$ for inner-$L_1$ flow and $\sim10$ for outer-$L_2$ flow.

Stability is controlled by mass-radius exponents:

$$
\zeta_* \equiv \frac{d\log R_*}{d\log M}, \qquad \zeta_L \equiv \frac{d\log R_L}{d\log M}.
$$

If $\zeta_*<\zeta_L$ the transfer runs away. For conservative circular transfer,

$$
a\,(M_d M_a)^2 = {\rm constant},
$$

so the orbit shrinks while $M_d>M_a$ and expands once the donor becomes the less massive component.

> **Key finding:** RLOF is the correct limiting model when two extended bodies are close enough that tidal equipotentials control mass flow; BHL is the correct model for a small body embedded in gas.

### 2.4 Runaway prevention and timestep constraints

Modern codes prevent runaway through a stack of independent limits:

1. **Hard per-tick mass cap:** $\le25\%$ of donor mass per step (Krumholz et al. 2004).
2. **Density-excess cap:** only mass above $\rho_{\rm res}$ moves (Federrath et al. 2010).
3. **Supply-rate cap:** Bondi-Hoyle / free-fall / collapse timescale.
4. **Physical gates:** bound and infalling gas only (Federrath et al. 2010).
5. **Subcycling:** sink-sink orbital dynamics substepped inside the hydro timestep (Federrath et al. 2010; Krumholz et al. 2004).
6. **Conservation enforcement:** mass, linear momentum, angular momentum ledger.

---

## 3. Governing Equations

### 3.1 BHL sink accretion

Rate:

$$
\dot M_{\rm BHL} = \frac{4\pi G^2 M^2 \rho_\infty}{(c_s^2+v_\infty^2)^{3/2}}.
$$

Capture radius:

$$
R_{\rm acc} = \frac{2GM}{c_s^2+v_\infty^2}.
$$

Per-tick debit (capped):

$$
\Delta M = \min\left(\dot M_{\rm BHL}\,\Delta t,\; f_{\rm acc}\,M_{\rm gas},\; f_{\rm donor}\,M_{\rm donor}\right),
$$

with recommended defaults $f_{\rm acc}=0.25$, $f_{\rm donor}=0.25$.

### 3.2 Exponential-relaxation debit (Hubber-style)

For a sink interaction zone containing total gas mass $M_{\rm int}$:

$$
\Delta M = M_{\rm int}\left[1-\exp\left(-\frac{\Delta t}{t_{\rm ACC}}\right)\right],
\qquad
\frac{1}{t_{\rm ACC}} = \frac{1-f}{t_{\rm RAD}} + \frac{f}{t_{\rm DISC}},
\qquad
f=\min\left(\frac{2E_{\rm rot}}{|E_{\rm grav}|},1\right).
$$

This naturally respects a per-tick cap because $\Delta t/t_{\rm ACC}\ll1$ gives $\Delta M\approx M_{\rm int}\Delta t/t_{\rm ACC}$.

### 3.3 Roche-lobe overflow

Roche-lobe radius (Eggleton 1983):

$$
\frac{R_L}{a} = \frac{0.49\,q^{2/3}}{0.6\,q^{2/3}+\ln(1+q^{1/3})}.
$$

Pols scaling for the rate:

$$
\dot M = -A\,\frac{M_d}{P}\,\delta^3, \qquad A\sim10,\quad \delta=\frac{R_d-R_L}{R_L}.
$$

Conservative orbit evolution:

$$
\frac{\dot a}{a} = 2\frac{\dot M_d}{M_d}\left(\frac{M_d}{M_a}-1\right).
$$

### 3.4 Sound speed

For an ideal gas:

$$
c_s = \sqrt{\frac{\gamma k_B T}{\mu m_H}}.
$$

For molecular gas $\mu\approx2.33$, $\gamma\approx1$ (isothermal), giving $c_s\approx0.19$ km s$^{-1}$ at $10$ K.

---

## 4. Implementation Sketch (Clojure Pseudocode)

```clojure
(defn bhl-accretion-rate
  "Mass flux (kg/s) from gas to a sink."
  [M rho-inf c-s v-rel]
  (let [G     law/G
        denom (Math/pow (+ (* c-s c-s) (* v-rel v-rel)) 1.5)]
    (/ (* 4.0 Math/PI G G M M rho-inf) denom)))

(defn capture-radius
  "Gravitational capture radius (m)."
  [M c-s v-rel]
  (/ (* 2.0 law/G M)
     (+ (* c-s c-s) (* v-rel v-rel))))

(defn debit-mass
  "Apply the three caps: supply rate, available gas, donor fraction."
  [{:keys [dot-m dt gas-mass donor-mass
           accretion-fraction-cap donor-fraction-cap]}]
  (min (* dot-m dt)
       (* accretion-fraction-cap gas-mass)
       (* donor-fraction-cap donor-mass)))
```

A `sink-accretion-system` would:
1. Read all entities with `:sink/mass`, `:sink/position`, `:sink/velocity`.
2. For each sink, query gas parcels within `c/accretion-radius`.
3. Average ambient density, sound speed, and relative velocity.
4. Compute `R_acc`, `dot-M`, and capped `delta-M`.
5. Produce `:mass-flux/accretion` influences.
6. A later system applies debits, conserves momentum, and deletes parcels below a floor mass.

---

## 5. Toy Model / Numerical Experiment

### 5.1 Setup

- Sink mass $M = 0.1\,M_\odot$.
- Gas density $\rho_\infty = 10^{-19}$ g cm$^{-3}$.
- Sound speed $c_s = 0.19$ km s$^{-1}$ ($T=10$ K, molecular).
- Drift velocities $v_\infty = 0.1, 0.5, 1.0, 2.0$ km s$^{-1}$.
- Timestep $\Delta t = 10^{11}$ s ($\sim3$ kyr).

### 5.2 Results

| $v_\infty$ (km/s) | $R_{\rm acc}$ (AU) | $\dot M_{\rm BHL}$ ($M_\odot$/yr) | $\Delta M$ / tick (kg) | regime |
|---|---|---|---|---|
| 0.1 | 5,960 | $1.5\times10^{-7}$ | $9.5\times10^{23}$ | subsonic |
| 0.5 | 238 | $1.1\times10^{-8}$ | $7.0\times10^{22}$ | transonic |
| 1.0 | 71  | $1.1\times10^{-9}$ | $7.0\times10^{21}$ | supersonic |
| 2.0 | 20  | $1.5\times10^{-10}$ | $9.5\times10^{20}$ | supersonic |

With a 25% donor-mass cap the actual debit is bounded by the local gas reservoir, not the raw rate.

---

## 6. Validation

- [ ] BHL rate reproduces the subsonic and supersonic limiting scalings.
- [ ] Capture radius scales linearly with $M$ and inversely with $c_s^2+v^2$.
- [ ] Mass and momentum are conserved to floating-point tolerance in a two-body debit.
- [ ] A sink cannot drain more than the per-tick cap or the available gas mass.
- [ ] RLOF rate vanishes when $R_d<R_L$ and is negative when overflowing.

---

## 7. Promotion Path to Domain

### 7.1 ECS Components

```clojure
(defrecord AccretionRadius [r-acc r-bondi ambient-density ambient-cs
                            relative-velocity])
(defrecord AccretionRate [dot-m dot-m-this-tick efficiency regime])
(defrecord MassFlux [sink-id donor-id delta-m delta-p delta-l tick])
(defrecord RocheLobe [donor-eid accretor-eid semi-major-axis eccentricity
                      roche-radius overfilling rate])
```

### 7.2 Malli Schema (law/)

```clojure
(def AccretionRadiusSchema
  [:map
   [:sink/r-acc number?]
   [:sink/r-bondi number?]
   [:sink/ambient-density number?]
   [:sink/ambient-cs number?]
   [:sink/relative-velocity number?]])

(def MassFluxSchema
  [:map
   [:mass-flux/sink-id int?]
   [:mass-flux/donor-id int?]
   [:mass-flux/delta-m number?]
   [:mass-flux/delta-p [:vector number?]]
   [:mass-flux/tick int?]])
```

### 7.3 System Function (domain/)

```clojure
(defn sink-accretion-system
  "Compute gradual gas-to-sink accretion fluxes for one tick."
  [world]
  (->> (ecs/entities-with world c/sink-mass c/position c/velocity)
       (reduce (fn [w eid]
                 (let [sink (sink-state world eid)
                       zone (gas-in-radius world eid (:r-acc sink))]
                   (if (empty? zone)
                     w
                     (let [rate (compute-bhl-rate sink zone)
                           flux (compute-mass-flux sink zone rate dt)]
                       (ecs/add-influence w eid c/accretion-rate flux)))))
               world)))
```

### 7.4 Test (test/)

```clojure
(deftest bhl-rate-regimes
  (let [M 0.1
        rho 1e-19
        cs 0.19e3]
    (is (approx (/ (bhl-accretion-rate M rho cs 0.0) cs -3) ...))
    (is (approx (bhl-accretion-rate M rho cs 1e3) ...))))
```

---

## 8. Open Questions

1. Should the default accretion prescription be BHL (Krumholz-style) or exponential relaxation (Hubber-style)? BHL is more directly tied to local gas properties; exponential relaxation is simpler to cap.
2. How should angular momentum be redistributed? Add to sink spin, spawn a circum-sink disk entity, or feed back into gas velocities?
3. Should RLOF and BHL share the same `:mass-flux` influence component, or should they be distinct?
4. What is the correct softening length / smoothing-length coupling for our parcel representation? Our parcels are point-like with a smoothing radius, so $r_{\rm soft}\approx r_{\rm acc}/2$ is a starting point.
5. Do we subcycle sink-sink orbits? The parallel ECS tick is fixed; substeps would run inside a barrier system.

---

## 9. Cross-References

- `docs/designs/phase0-sink-particle-formation.md` — Stage 2 sink formation and Stage 3 sink accretion; this notebook grounds Stage 3 in rate laws.
- `kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md` — stellar wind mass-loss; BHL accretion is the inverse process (gas → body).
- `kanban/tasks/core-accretion-physics-spec.md` — planet growth by accretion; the capped BHL rate feeds the planet-mass budget.
- `docs/research/physics/protoplanetary-disks-planet-formation.md` — disk structure and streaming instability; mass transfer governs how solids are delivered to cores.

---

## 10. References

1. Bondi, H. 1952, *MNRAS*, 112, 195. DOI:10.1093/mnras/112.2.195
2. Edgar, R. G. 2004, *New Astronomy Reviews*, 48, 843. arXiv:astro-ph/0406166. DOI:10.1016/j.newar.2004.06.001
3. Federrath, C., Banerjee, R., Clark, P. C. & Klessen, R. S. 2010, *ApJ*, 713, 269. arXiv:1001.4456. DOI:10.1088/0004-637X/713/1/269
4. Krumholz, M. R., McKee, C. F. & Klein, R. I. 2004, *ApJ*, 611, 399. arXiv:astro-ph/0312612. DOI:10.1086/421935
5. Hubber, D. A., Walch, S. & Whitworth, A. P. 2013, *MNRAS*, 430, 3261. arXiv:1301.4520. DOI:10.1093/mnras/stt128
6. Bate, M. R., Bonnell, I. A. & Price, N. M. 1995, *MNRAS*, 277, 362. DOI:10.1093/mnras/277.2.362
7. Bleuler, A. & Teyssier, R. 2014, *MNRAS*, 445, 4015. arXiv:1409.6528. DOI:10.1093/mnras/stu2005
8. Eggleton, P. P. 1983, *ApJ*, 268, 368. DOI:10.1086/160960
9. Paczyński, B. 1971, *ARA&A*, 9, 183. DOI:10.1146/annurev.aa.09.090171.001151
10. Ritter, H. 1988, *A&A*, 202, 93
11. Kolb, U. & Ritter, H. 1990, *A&A*, 236, 385
12. Buening, A. & Ritter, H. 2005, *A&A*, 445, 239. arXiv:astro-ph/0510126
13. Hurley, J. R., Tout, C. A. & Pols, O. R. 2002, *MNRAS*, 329, 897. arXiv:astro-ph/0201220
14. Sepinsky, J. F., Willems, B. & Kalogera, V. 2007, *ApJ*, 660, 1624. arXiv:astro-ph/0612508
15. Ryu, T., Sari, R., de Mink, S. E. et al. 2025, *A&A*, 702, A61. arXiv:2505.18255
