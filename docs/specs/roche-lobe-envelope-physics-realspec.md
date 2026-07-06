# Roche-Lobe Envelope Physics Realspec (deferred capability)

**Status:** deferred  
**Scope:** the parts of Roche-lobe overflow deliberately left out of `docs/specs/gradual-mass-transfer-realspec.md`: adiabatic-envelope rate laws, spin/orbit co-evolution, outer-Lagrangian ($L_2$) overflow, and circum-sink disk/spin components.  
**Precondition:** `gradual-mass-transfer-realspec.md` landed; binary RLOF is observed in live runs.  
**Trigger:** a binary pair exhibits sustained overflow for many ticks, or the Ritter isothermal rate predicts unphysically fast stripping because the donor envelope is convective/adiabatic.

---

## 1. Why this is deferred

The initial gradual-mass-transfer spec needs a working, testable RLOF channel now so close binaries do not instantly merge. The Ritter (1988) isothermal branch is accurate for radiative envelopes with thin, optically-thin photospheres. It is not accurate when:
- The donor has a deep convective envelope (adiabatic response).
- The donor overfills so much that matter escapes through the outer Lagrangian point $L_2$.
- The accretor cannot swallow the transferred mass and a circumbinary disk forms.
- The spin of either body or the orbit evolves enough to feed back on the overflow rate.

These are real but secondary for Phase 0’s core goal (reasonable planet sizes + life seeding). They become important once binaries are common and long-lived.

---

## 2. Deferred physics

### 2.1 Adiabatic envelope branch

For a convective donor envelope ($\gamma=5/3$), use the Kolb \u0026 Ritter (1990) rate. The scaled form is

$$
\dot{\tilde M}_L = \frac{4\pi}{\sqrt{BC}} \frac{(\gamma-1)^{3/2}}{3\gamma-1} \left[\frac{2}{\gamma+1}\right]^{\frac{\gamma+1}{2(\gamma-1)}}.
$$

Branch selection rule: if the donor’s photosphere lies **inside** the Roche lobe, use Ritter (isothermal); if it lies **outside**, use Kolb \u0026 Ritter (adiabatic).

### 2.2 Donor radius response and stability

Track the donor’s thermal ($\zeta_{\rm eq}$) and adiabatic ($\zeta_{\rm ad}$) mass-radius exponents. The overflow is:
- **dynamically unstable** if $\zeta_{\rm ad} < \zeta_L$ → runaway to common envelope or merger.
- **thermally unstable** if $\zeta_{\rm ad} \ge \zeta_L$ but $\zeta_{\rm eq} < \zeta_L$ → transfer on the Kelvin–Helmholtz timescale.
- **stable** if $\zeta_{\rm eq} \ge \zeta_L$.

For conservative circular transfer,

$$
\zeta_L \approx 2.13\,q - 1.67,
\qquad q=M_d/M_a.
$$

### 2.3 Orbit and spin co-evolution

Transfer changes $a$ and $e$. For conservative circular transfer,

$$
a\,(M_d M_a)^2 = {\rm constant}
\quad\Longrightarrow\quad
\frac{\dot a}{a} = 2\frac{\dot M_d}{M_d}\left(\frac{M_d}{M_a}-1\right).
$$

For non-conservative transfer with accreted fraction $\beta$ and angular-momentum-loss parameter $\gamma$,

$$
\frac{\dot a}{a} = 2\frac{\dot M_d}{M_d}\left[\beta\frac{M_d}{M_a} - 1 - (1-\beta)\frac{\gamma}{2}\left(1+\frac{M_d}{M_a}\right)\right].
$$

Spin angular momentum of accreted material is either added to `c/sink-spin` on the accretor or deposited in a circum-sink disk component.

### 2.4 Outer-Lagrangian ($L_2$) overflow

When $\delta \gtrsim 0.1$–$0.3$ for $q\sim1$, matter escapes through $L_2$ and carries away binary angular momentum. Use the Ryu et al. (2025) correction factor $\mathcal{F}_1(q)$ applied to the base Ritter/Kolb rate, or a dedicated $L_2$ rate if available.

### 2.5 Circum-sink disk / spin component

Introduce `c/sink-spin`:
- `:spin/omega` — angular velocity.
- `:spin/axis` — unit vector.
- `:spin/angular-momentum` — kg m² s⁻¹.

Accreted angular momentum is added to `:spin/angular-momentum`. Excess spin is fed back into nearby gas or a `c/circum-sink-disk` entity on a viscous timescale (Hubber et al. 2013 pattern).

---

## 3. Promotion path

| File | Change |
|---|---|
| `src/law/mass_transfer.clj` | Add Ritter/Kolb branch selectors, $\zeta$ helpers, Ryu $L_2$ factor. |
| `src/domain/mass_transfer.clj` | Extend `roche-lobe-system` with branch selection and spin/orbit evolution. |
| `src/domain/ecs/components.clj` | Add `sink-spin`, `circum-sink-disk`. |
| `src/domain/binary.clj` (or `domain.orbital`) | Orbit evolution under mass/AM flux. |
| `test/domain/mass_transfer_test.clj` | Add stability, orbit-evolution, and spin tests. |

---

## 4. References

1. Ritter, H. 1988, *A\u0026A*, 202, 93.
2. Kolb, U. \u0026 Ritter, H. 1990, *A\u0026A*, 236, 385.
3. Eggleton, P. P. 1983, *ApJ*, 268, 368.
4. Ryu, T., Sari, R., de Mink, S. E. et al. 2025, *A\u0026A*, 702, A61. arXiv:2505.18255.
5. Hurley, J. R., Tout, C. A. \u0026 Pols, O. R. 2002, *MNRAS*, 329, 897.
6. Hubber, D. A., Walch, S. \u0026 Whitworth, A. P. 2013, *MNRAS*, 430, 3261.
