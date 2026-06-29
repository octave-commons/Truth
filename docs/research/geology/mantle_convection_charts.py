#!/usr/bin/env python3
"""
Charts for mantle convection research notebook.
Generates validation plots for Rayleigh-Bénard convection parameters.
"""

import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import rcParams

# Style
rcParams['figure.figsize'] = (10, 7)
rcParams['font.size'] = 12
rcParams['axes.labelsize'] = 14
rcParams['axes.titlesize'] = 15
rcParams['legend.fontsize'] = 11

OUTPUT = "docs/research/geology/img/"

# ============================================================
# Physical constants (SI)
# ============================================================
rho   = 4000.0     # kg/m³, bulk mantle density
g     = 9.8        # m/s²
alpha = 2e-5       # K⁻¹, thermal expansivity
kappa = 1e-6       # m²/s, thermal diffusivity
R_gas = 8.314      # J/(mol·K)

# ============================================================
# Chart 1: Critical Rayleigh Number vs Wavelength Ratio
# ============================================================
def critical_ra(wavelength_ratio):
    """Ra_c as function of λ/b (wavelength / depth).
    From Turcotte & Schubert 2014, eq. 6.319:
    Ra_c = (π² + 4π²/η²)³ / (4π²/η²)  where η = λ/b
    """
    eta = wavelength_ratio
    term = 4 * np.pi**2 / eta**2
    return (np.pi**2 + term)**3 / term

fig, ax = plt.subplots(figsize=(10, 7))
lam_b = np.linspace(0.5, 5.0, 500)
Ra_c = critical_ra(lam_b)

ax.plot(lam_b, Ra_c, 'b-', linewidth=2.5, label=r'$Ra_c(\lambda/b)$')
ax.axhline(y=657.5, color='r', linestyle='--', linewidth=1.5,
           label=r'Min $Ra_c = 657.5$ at $\lambda/b \approx 2.0$')

# Find minimum
idx_min = np.argmin(Ra_c)
ax.plot(lam_b[idx_min], Ra_c[idx_min], 'ro', markersize=10, zorder=5)
ax.annotate(f'Min: Ra_c = {Ra_c[idx_min]:.1f}\nat λ/b = {lam_b[idx_min]:.2f}',
            xy=(lam_b[idx_min], Ra_c[idx_min]),
            xytext=(lam_b[idx_min]+0.6, Ra_c[idx_min]+2000),
            fontsize=11, arrowprops=dict(arrowstyle='->', color='gray'),
            bbox=dict(boxstyle='round,pad=0.3', facecolor='lightyellow'))

ax.set_xlabel(r'Wavelength ratio $\lambda / b$')
ax.set_ylabel(r'Critical Rayleigh number $Ra_c$')
ax.set_title('Critical Rayleigh Number for Onset of Convection\n(Turcotte & Schubert 2014, §6.19)')
ax.set_ylim([0, 50000])
ax.legend(loc='upper right')
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig(f"{OUTPUT}critical_rayleigh_vs_wavelength.png", dpi=150)
plt.close()
print("✓ Chart 1: Critical Rayleigh number vs wavelength")

# ============================================================
# Chart 2: Arrhenius Viscosity Profile vs Depth
# ============================================================
def arrhenius_viscosity(T, E, V=0, T_ref=1600, P=0, eta_ref=1e20):
    """Arrhenius viscosity: η = η_ref * exp[(E + PV)/(R*T) - (E + PV_ref)/(R*T_ref)]
    Simplified: η = η_ref * exp[E/R * (1/T - 1/T_ref) + V*P/(R*T)]
    """
    return eta_ref * np.exp(E / R_gas * (1.0/T - 1.0/T_ref) + V * P / (R_gas * T))

# Temperature profile: simple conductive + adiabatic
depths = np.linspace(0, 2890e3, 200)  # meters
T_surface = 300  # K
T_CMB = 4000     # K (approximate CMB temperature)
# Simplified geotherm: linear + slight adiabatic gradient
T_profile = T_surface + (T_CMB - T_surface) * (depths / 2890e3)**0.6

# Pressure profile: hydrostatic
P_profile = rho * g * depths  # Pa

# Viscosity profiles for different activation energies
fig, axes = plt.subplots(1, 2, figsize=(14, 7))

# Panel (a): Varying activation energy, no activation volume
ax = axes[0]
for E_kJ in [200, 300, 400, 500]:
    E = E_kJ * 1e3  # J/mol
    eta = arrhenius_viscosity(T_profile, E, V=0, T_ref=1600, eta_ref=1e20)
    ax.semilogy(depths/1e3, eta, linewidth=2, label=f'E = {E_kJ} kJ/mol')

ax.set_xlabel('Depth (km)')
ax.set_ylabel('Viscosity (Pa·s)')
ax.set_title(r'Viscosity vs Depth (V* = 0)')
ax.set_ylim([1e17, 1e28])
ax.legend()
ax.grid(True, alpha=0.3, which='both')
ax.invert_yaxis()
ax.axhline(y=1e21, color='gray', linestyle=':', alpha=0.5, label='Reference 10²¹ Pa·s')

# Panel (b): E=300 kJ/mol, varying activation volume
ax = axes[1]
E = 300e3  # 300 kJ/mol
for V_cm3 in [0, 5, 10, 15]:
    V = V_cm3 * 1e-6  # m³/mol
    eta = arrhenius_viscosity(T_profile, E, V=V, T_ref=1600, P=P_profile, eta_ref=1e20)
    ax.semilogy(depths/1e3, eta, linewidth=2, label=f'V* = {V_cm3} cm³/mol')

ax.set_xlabel('Depth (km)')
ax.set_ylabel('Viscosity (Pa·s)')
ax.set_title(r'Viscosity vs Depth (E = 300 kJ/mol)')
ax.set_ylim([1e17, 1e28])
ax.legend()
ax.grid(True, alpha=0.3, which='both')
ax.invert_yaxis()

fig.suptitle('Arrhenius Viscosity Profiles for Earth\'s Mantle', fontsize=16, y=1.02)
fig.tight_layout()
fig.savefig(f"{OUTPUT}arrhenius_viscosity_profiles.png", dpi=150, bbox_inches='tight')
plt.close()
print("✓ Chart 2: Arrhenius viscosity profiles")

# ============================================================
# Chart 3: Nusselt-Rayleigh Scaling Laws
# ============================================================
fig, ax = plt.subplots(figsize=(10, 7))
Ra_range = np.logspace(4, 9, 200)

# Different scaling laws
ax.loglog(Ra_range, 0.284 * Ra_range**0.294, 'b-', linewidth=2.5,
          label=r'$Nu = 0.284\,Ra^{0.294}$ (Wolstencroft et al. 2009, basally heated)')
ax.loglog(Ra_range, 0.20 * Ra_range**0.337, 'r--', linewidth=2.5,
          label=r'$Nu = 0.20\,Ra^{0.337}$ (Wolstencroft et al. 2009, internally heated)')
ax.loglog(Ra_range, 0.197 * Ra_range**(1.0/3.0), 'g-.', linewidth=2,
          label=r'$Nu = 0.197\,Ra^{1/3}$ (Jimenez & Zufiria 1987, BL theory)')
ax.loglog(Ra_range, 0.27 * Ra_range**0.25 + 0.038 * Ra_range**(1.0/3.0),
          'k:', linewidth=2.5,
          label=r'$Nu = 0.27\,Ra^{1/4} + 0.038\,Ra^{1/3}$ (Grossmann & Lohse 2000)')

# Earth's mantle Ra
Ra_earth = 1e8
ax.axvline(x=Ra_earth, color='orange', linewidth=2, alpha=0.7,
           label=f"Earth's mantle Ra ≈ {Ra_earth:.0e}")
Nu_earth = 0.284 * Ra_earth**0.294
ax.plot(Ra_earth, Nu_earth, 'o', color='orange', markersize=12, zorder=5)

ax.set_xlabel('Rayleigh Number $Ra$')
ax.set_ylabel('Nusselt Number $Nu$')
ax.set_title('Nusselt–Rayleigh Scaling Laws for Mantle Convection')
ax.legend(loc='lower right', fontsize=10)
ax.grid(True, alpha=0.3, which='both')
ax.set_xlim([1e4, 1e9])
ax.set_ylim([1, 1000])
fig.tight_layout()
fig.savefig(f"{OUTPUT}nusselt_rayleigh_scaling.png", dpi=150)
plt.close()
print("✓ Chart 3: Nusselt-Rayleigh scaling laws")

# ============================================================
# Chart 4: Convective Regime Diagram (Viscosity Contrast vs Ra)
# ============================================================
fig, ax = plt.subplots(figsize=(10, 7))

# Regime boundaries from Solomatov (1995) and Solomatov & Moresi (1997)
# For Newtonian (n=1):
#   Small viscosity contrast: Δη < ~10
#   Transitional: 10 < Δη < ~100-1000
#   Stagnant lid: Δη > ~100-1000 (depends on Ra)

Ra_regime = np.logspace(3, 8, 100)

# Approximate boundaries
delta_eta_transitional = 10 * (Ra_regime / 1e4)**0.15  # rough scaling
delta_eta_stagnant = 100 * (Ra_regime / 1e4)**0.2

ax.loglog(Ra_regime, delta_eta_transitional, 'b-', linewidth=2.5,
          label='Mobile → Transitional')
ax.loglog(Ra_regime, delta_eta_stagnant, 'r-', linewidth=2.5,
          label='Transitional → Stagnant Lid')

# Fill regimes
ax.fill_between(Ra_regime, 1, delta_eta_transitional, alpha=0.15, color='green',
                label='Mobile lid')
ax.fill_between(Ra_regime, delta_eta_transitional, delta_eta_stagnant, alpha=0.15,
                color='yellow', label='Transitional')
ax.fill_between(Ra_regime, delta_eta_stagnant, 1e10, alpha=0.15, color='red',
                label='Stagnant lid')

# Earth markers
ax.plot(1e7, 1e5, 'ko', markersize=12, zorder=5)
ax.annotate('Earth (present)', xy=(1e7, 1e5), xytext=(2e7, 3e5),
            fontsize=12, fontweight='bold',
            arrowprops=dict(arrowstyle='->', color='black'))
ax.plot(1e6, 1e3, 'k^', markersize=10, zorder=5, label='Early Earth (est.)')

ax.set_xlabel('Rayleigh Number $Ra$')
ax.set_ylabel(r'Viscosity Contrast $\Delta\eta$')
ax.set_title('Convective Regime Diagram\n(Solomatov 1995; Solomatov & Moresi 1997)')
ax.set_xlim([1e3, 1e8])
ax.set_ylim([1, 1e8])
ax.legend(loc='upper left', fontsize=10)
ax.grid(True, alpha=0.3, which='both')
fig.tight_layout()
fig.savefig(f"{OUTPUT}convective_regime_diagram.png", dpi=150)
plt.close()
print("✓ Chart 4: Convective regime diagram")

# ============================================================
# Chart 5: Thermal Boundary Layer Structure
# ============================================================
fig, ax = plt.subplots(figsize=(10, 7))

# Schematic temperature profile through convecting layer
y = np.linspace(0, 1, 200)
# Bottom thermal boundary layer
T_bot = 1.0 - 0.7 * np.exp(-y / 0.05)
# Top thermal boundary layer
T_top = T_bot * (1 - 0.7 * np.exp(-(1 - y) / 0.08))
# Interior (well-mixed)
T_interior = 0.5 * np.ones_like(y)
# Combined
T_combined = np.where(y < 0.1, 1.0 - 0.5 * (y / 0.1),
                       np.where(y > 0.88, 0.5 * (1 - (y - 0.88) / 0.12), 0.5))
# Smooth version
T_smooth = 0.5 + 0.5 * np.tanh((y - 0.1) / 0.03) - 0.5 * np.tanh((y - 0.9) / 0.04)
T_smooth = T_smooth / T_smooth.max()

ax.plot(T_smooth, y, 'r-', linewidth=3, label='Temperature profile')
ax.axhline(y=0.1, color='blue', linestyle='--', linewidth=1.5, alpha=0.7,
           label=r'Bottom BL thickness $\delta_b$')
ax.axhline(y=0.9, color='blue', linestyle='--', linewidth=1.5, alpha=0.7,
           label=r'Top BL thickness $\delta_t$')

# Annotations
ax.annotate('Bottom thermal\nboundary layer', xy=(0.8, 0.05), fontsize=11,
            color='blue', fontweight='bold')
ax.annotate('Well-mixed\ninterior', xy=(0.5, 0.5), fontsize=12,
            ha='center', color='gray', fontweight='bold')
ax.annotate('Top thermal\nboundary layer', xy=(0.15, 0.93), fontsize=11,
            color='blue', fontweight='bold')

ax.set_xlabel('Dimensionless Temperature $T\'$')
ax.set_ylabel('Depth $z / d$')
ax.set_title('Thermal Boundary Layer Structure in Rayleigh-Bénard Convection')
ax.set_xlim([0, 1.1])
ax.set_ylim([0, 1])
ax.invert_yaxis()
ax.legend(loc='lower left', fontsize=11)
ax.grid(True, alpha=0.3)

# Add velocity arrows
for yy in [0.15, 0.3, 0.5, 0.7, 0.85]:
    direction = 1 if yy < 0.5 else -1
    ax.annotate('', xy=(1.05 + direction*0.05, yy), xytext=(1.05, yy),
                arrowprops=dict(arrowstyle='->', color='green', lw=2))

ax.text(1.08, 0.3, 'Upflow', fontsize=10, color='green', ha='center', fontweight='bold')
ax.text(1.08, 0.7, 'Downflow', fontsize=10, color='green', ha='center', fontweight='bold')

fig.tight_layout()
fig.savefig(f"{OUTPUT}thermal_boundary_layer.png", dpi=150)
plt.close()
print("✓ Chart 5: Thermal boundary layer structure")

# ============================================================
# Summary
# ============================================================
print("\n=== Earth's Mantle Convection Parameters ===")
print(f"Density:                ρ = {rho} kg/m³")
print(f"Gravity:                g = {g} m/s²")
print(f"Thermal expansivity:    α = {alpha} K⁻¹")
print(f"Thermal diffusivity:    κ = {kappa} m²/s")
print(f"Layer thickness:        d = 2890 km")
print(f"Temperature contrast:   ΔT = 2500 K")
print(f"Reference viscosity:    η = 10²¹ Pa·s")
Ra = rho * g * alpha * 2500 * (2890e3)**3 / (1e21 * kappa)
print(f"Rayleigh number:        Ra = {Ra:.2e}")
Nu = 0.284 * Ra**0.294
print(f"Nusselt number:         Nu ≈ {Nu:.1f}")
print(f"Surface heat flow:      q ≈ {Nu * 4.0 * 2500 / 2890e3 * 1e3:.0f} mW/m²")
