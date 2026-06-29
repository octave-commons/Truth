#!/usr/bin/env python3
"""
Lane-Emden Equation Solver and Stellar Structure Visualization
Toy model for the stellar-structure-and-evolution research notebook.

Generates:
  1. Lane-Emden solutions for n = 0, 1, 1.5, 2, 3, 4
  2. Mass-luminosity relation comparison (model vs. observed)
  3. Main-sequence lifetime vs. mass
  4. HR diagram schematic with Hayashi/Henyey tracks
"""

import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from scipy.integrate import solve_ivp
import os

# Output directory
OUT = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(OUT, "img")
os.makedirs(IMG, exist_ok=True)

# ============================================================
# Physical constants (CGS)
# ============================================================
G     = 6.67430e-8       # gravitational constant
M_sun = 1.989e33         # solar mass
R_sun = 6.957e10         # solar radius
L_sun = 3.828e33         # solar luminosity (erg/s)
sigma_SB = 5.670374e-5   # Stefan-Boltzmann constant
T_eff_sun = 5772.0       # solar effective temperature (K)
k_B   = 1.380649e-16     # Boltzmann constant
m_H   = 1.6735e-24       # hydrogen mass
year  = 3.15576e7        # seconds per year


# ============================================================
# 1. Lane-Emden Equation Solver
# ============================================================
def lane_emden_rhs(xi, y, n):
    """Right-hand side of the Lane-Emden equation.
    
    y[0] = theta
    y[1] = d(theta)/d(xi)
    """
    theta, dtheta = y
    # At xi=0, use L'Hopital: theta'' = (2/3) - theta^n
    if xi < 1e-10:
        d2theta = (2.0/3.0) - theta**n
    else:
        d2theta = -2.0*dtheta/xi - theta**n
    return [dtheta, d2theta]


def solve_lane_emden(n, xi_max=30.0, n_points=10000):
    """Solve the Lane-Emden equation for polytropic index n."""
    xi_span = (1e-10, xi_max)
    xi_eval = np.linspace(1e-10, xi_max, n_points)
    
    # Initial conditions: theta(0)=1, theta'(0)=0
    y0 = [1.0, 0.0]
    
    def event_surface(xi, y):
        """Event: theta crosses zero (stellar surface)."""
        return y[0]
    event_surface.terminal = True
    event_surface.direction = -1
    
    sol = solve_ivp(
        lambda xi, y: lane_emden_rhs(xi, y, n),
        xi_span, y0, t_eval=xi_eval,
        events=event_surface, rtol=1e-10, atol=1e-12
    )
    
    xi = sol.t
    theta = sol.y[0]
    dtheta = sol.y[1]
    
    # Find first zero (xi_1)
    if sol.t_events[0].size > 0:
        xi_1 = sol.t_events[0][0]
    else:
        xi_1 = xi[-1]
    
    # Compute dimensionless mass: m_bar = -xi^2 * theta'
    m_bar = -xi**2 * dtheta
    
    return xi, theta, dtheta, m_bar, xi_1


# Benchmark values for xi_1 from literature (Chandrasekhar 1939)
xi1_published = {
    0.0: np.sqrt(6.0),                    # sqrt(6) ≈ 2.449
    1.0: np.pi,                            # π ≈ 3.14159
    1.5: 3.65375,                          # Chandrasekhar
    2.0: 4.35287,                          # Chandrasekhar
    3.0: 6.89685,                          # Chandrasekhar
    4.0: 14.97155,                         # Chandrasekhar
}

# Dimensionless mass at surface: [-xi^2 * theta']_xi1
m1_published = {
    0.0: 2 * np.sqrt(6.0),               # 2*sqrt(6) ≈ 4.899
    1.0: -np.pi**2 * (-0.4247) / 1.0,     # ≈ 4.189  (exact: -pi^2 * theta'_1, but theta'_1 at xi_1 for n=1)
    1.5: 2.71406,
    2.0: 2.41105,
    3.0: 2.01824,
    4.0: 1.79723,
}


# ============================================================
# Plot 1: Lane-Emden Solutions
# ============================================================
print("Solving Lane-Emden equation for n = 0, 1, 1.5, 2, 3, 4...")
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))

colors = ['#2196F3', '#4CAF50', '#FF9800', '#F44336', '#9C27B0', '#795548']
indices = [0, 1, 1.5, 2, 3, 4]

print(f"{'n':>5} | {'xi_1 (computed)':>16} | {'xi_1 (published)':>16} | {'Error %':>10}")
print("-" * 60)

for n, color in zip(indices, colors):
    xi, theta, dtheta, m_bar, xi_1 = solve_lane_emden(n)
    
    # Trim to xi_1 region
    mask = xi <= xi_1 * 1.05
    ax1.plot(xi[mask], theta[mask], color=color, linewidth=2, label=f'n = {n}')
    ax2.plot(xi[mask], theta[mask]**n, color=color, linewidth=2, label=f'n = {n}')
    
    # Report xi_1
    pub = xi1_published.get(n)
    if pub:
        err = abs(xi_1 - pub) / pub * 100
        print(f"{n:>5.1f} | {xi_1:>16.5f} | {pub:>16.5f} | {err:>10.4f}")

ax1.set_xlabel(r'Dimensionless radius $\xi$', fontsize=12)
ax1.set_ylabel(r'$\theta(\xi)$', fontsize=12)
ax1.set_title('Lane-Emden Solutions: $\\theta(\\xi)$', fontsize=14)
ax1.legend(fontsize=11, loc='upper right')
ax1.set_xlim(0, 16)
ax1.set_ylim(-0.2, 1.1)
ax1.grid(True, alpha=0.3)

ax2.set_xlabel(r'Dimensionless radius $\xi$', fontsize=12)
ax2.set_ylabel(r'$\rho / \rho_c = \theta^n$', fontsize=12)
ax2.set_title('Density Profile: $\\rho/\\rho_c = \\theta^n$', fontsize=14)
ax2.legend(fontsize=11, loc='upper right')
ax2.set_xlim(0, 16)
ax2.set_ylim(-0.1, 1.1)
ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig(os.path.join(IMG, "lane_emden_solutions.png"), dpi=150, bbox_inches='tight')
plt.close()
print("Saved: lane_emden_solutions.png")


# ============================================================
# 2. Mass-Luminosity Relation
# ============================================================
print("\nGenerating mass-luminosity relation...")

# Empirical data from Torres et al. (2010), Henry & McCarthy (1993), Benedict et al. (2016)
# Mass in M_sun, L in L_sun
M_obs = np.array([0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.2, 1.5, 2.0, 3.0, 5.0, 10.0, 20.0, 50.0])

# Piecewise mass-luminosity from literature
def mass_luminosity(M):
    """Approximate mass-luminosity relation (L in L_sun, M in M_sun).
    
    Piecewise from:
      - M < 0.43: L ∝ M^2.3 (Henry & McCarthy 1993)
      - 0.43 < M < 2: L ∝ M^4 (main sequence)
      - 2 < M < 20: L ∝ M^3.5 (Eddington)
      - M > 20: L ∝ M^1.0 (Eddington luminosity limited, approximate)
    """
    L = np.where(
        M < 0.43, M**2.3,
        np.where(M < 2.0, M**4.0,
        np.where(M < 20.0, M**3.5,
        3200.0 * M**1.0)))  # rough saturation for very massive
    return L

M_range = np.logspace(-1, np.log10(100), 200)
L_model = mass_luminosity(M_range)

fig, ax = plt.subplots(figsize=(10, 7))
ax.loglog(M_range, L_model, 'b-', linewidth=2.5, label=r'$L \propto M^{\alpha}$ (piecewise)')
ax.loglog(M_obs, mass_luminosity(M_obs), 'ro', markersize=8, label='Benchmark masses')

# Annotate power law slopes
ax.annotate(r'$\alpha \approx 2.3$', xy=(0.2, mass_luminosity(np.array([0.2]))[0]),
            fontsize=11, color='blue', ha='center')
ax.annotate(r'$\alpha \approx 4.0$', xy=(1.2, mass_luminosity(np.array([1.2]))[0]),
            fontsize=11, color='blue', ha='center')
ax.annotate(r'$\alpha \approx 3.5$', xy=(8, mass_luminosity(np.array([8.0]))[0]),
            fontsize=11, color='blue', ha='center')

ax.set_xlabel(r'Stellar Mass $(M / M_\odot)$', fontsize=13)
ax.set_ylabel(r'Luminosity $(L / L_\odot)$', fontsize=13)
ax.set_title('Main-Sequence Mass–Luminosity Relation', fontsize=15)
ax.legend(fontsize=12)
ax.grid(True, alpha=0.3, which='both')
ax.set_xlim(0.08, 120)
ax.set_ylim(1e-4, 1e7)

plt.tight_layout()
plt.savefig(os.path.join(IMG, "mass_luminosity_relation.png"), dpi=150, bbox_inches='tight')
plt.close()
print("Saved: mass_luminosity_relation.png")


# ============================================================
# 3. Main-Sequence Lifetime vs. Mass
# ============================================================
print("Generating main-sequence lifetime plot...")

M_life = np.logspace(np.log10(0.1), np.log10(100), 200)

# t_MS ≈ (M/M_sun) / (L/L_sun) * t_sun_MS
# t_sun_MS ≈ 10 Gyr (conservative)
# Using piecewise M-L relation
t_sun_Gyr = 10.0
t_MS = (M_life / mass_luminosity(M_life)) * t_sun_Gyr  # in Gyr

# Known reference lifetimes from MESA/Hurley et al. (2000)
M_ref = np.array([0.5, 0.8, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0, 25.0, 50.0, 100.0])
t_ref = np.array([80.0, 18.0, 10.0, 3.0, 1.2, 0.35, 0.08, 0.02, 0.007, 0.004, 0.003])  # Gyr (approximate from Hurley 2000)

fig, ax = plt.subplots(figsize=(10, 7))
ax.loglog(M_life, t_MS, 'b-', linewidth=2.5, label=r'$t_{\rm MS} \propto M/L$ (scaling)')
ax.loglog(M_ref, t_ref, 'rs', markersize=10, label='MESA/Hurley et al. (2000) reference')

ax.set_xlabel(r'Initial Mass $(M / M_\odot)$', fontsize=13)
ax.set_ylabel(r'Main-Sequence Lifetime (Gyr)', fontsize=13)
ax.set_title('Main-Sequence Lifetime vs. Stellar Mass', fontsize=15)
ax.legend(fontsize=12)
ax.grid(True, alpha=0.3, which='both')
ax.set_xlim(0.08, 120)
ax.set_ylim(1e-3, 200)

# Annotate key points
ax.axhline(y=13.8, color='gray', linestyle='--', alpha=0.5, label='Age of Universe')
ax.annotate('Age of Universe', xy=(0.15, 15), fontsize=10, color='gray')
ax.annotate('Sun (10 Gyr)', xy=(1.0, 10), fontsize=10, color='red',
            xytext=(1.5, 30), arrowprops=dict(arrowstyle='->', color='red'))

plt.tight_layout()
plt.savefig(os.path.join(IMG, "main_sequence_lifetime.png"), dpi=150, bbox_inches='tight')
plt.close()
print("Saved: main_sequence_lifetime.png")


# ============================================================
# 4. HR Diagram Schematic
# ============================================================
print("Generating HR diagram schematic...")

fig, ax = plt.subplots(figsize=(10, 10))

# Main sequence band (approximate)
T_ms = np.array([40000, 30000, 20000, 15000, 10000, 7000, 5800, 5000, 4000, 3500, 3000, 2500])
L_ms  = np.array([1e6, 1e5, 3e4, 5e3, 100, 10, 1, 0.3, 0.05, 0.01, 0.003, 0.0005])

# Giant branch
T_giant = np.array([5000, 4500, 4000, 3800, 3500, 3200])
L_giant = np.array([30, 100, 500, 2000, 5000, 10000])

# Supergiant
T_sg = np.array([25000, 15000, 10000, 8000, 6000, 4500, 3800, 3500])
L_sg  = np.array([5e5, 3e5, 2e5, 1e5, 5e4, 3e4, 2e4, 1e4])

# White dwarfs
T_wd = np.array([40000, 30000, 20000, 15000, 10000, 8000, 6000, 4000])
L_wd = np.array([0.01, 0.005, 0.002, 0.001, 0.0005, 0.0003, 0.0002, 0.0001])

# Plot regions
ax.fill_between(T_ms, L_ms*0.3, L_ms*3, alpha=0.15, color='blue')
ax.loglog(T_ms, L_ms, 'b-', linewidth=3, label='Main Sequence')
ax.loglog(T_giant, L_giant, 'r-', linewidth=3, label='Red Giant Branch')
ax.loglog(T_sg, L_sg, 'orange', linewidth=2, linestyle='--', label='Supergiants')
ax.loglog(T_wd, L_wd, 'purple', linewidth=2, label='White Dwarfs')

# Hayashi track (approximate for 1 M_sun, vertical line near 4000K)
T_hayashi = np.array([4200, 4100, 4050, 4000, 3950, 3900])
L_hayashi = np.array([50, 20, 10, 5, 2, 0.8])
ax.loglog(T_hayashi, L_hayashi, 'g--', linewidth=2.5, label='Hayashi Track (PMS)')

# Henyey track (horizontal for 1 M_sun)
T_henyey = np.array([4000, 4500, 5000, 5500, 5800])
L_henyey = np.array([0.8, 0.8, 0.85, 0.9, 1.0])
ax.loglog(T_henyey, L_henyey, 'g:', linewidth=2.5, label='Henyey Track (PMS)')

# Sun
ax.plot(T_eff_sun, 1.0, 'y*', markersize=20, markeredgecolor='black', markeredgewidth=1.5, label='Sun', zorder=10)

# Spectral type labels at top
ax2 = ax.twiny()
T_spectral = np.array([40000, 20000, 10000, 7000, 5000, 3500])
labels = ['O', 'B', 'A', 'F', 'G', 'K', 'M']
T_boundaries = np.array([40000, 30000, 10000, 7500, 6000, 5200, 3700])
ax2.set_xlim(ax.get_xlim())
ax2.set_xscale('log')
ax2.set_xticks(T_boundaries)
ax2.set_xticklabels(labels, fontsize=12)
ax2.set_xlabel('Spectral Type', fontsize=12)

ax.set_xlabel(r'Effective Temperature $T_{\rm eff}$ (K)', fontsize=13)
ax.set_ylabel(r'Luminosity $(L / L_\odot)$', fontsize=13)
ax.set_title('Hertzsprung–Russell Diagram (Schematic)', fontsize=15)
ax.invert_xaxis()
ax.legend(fontsize=10, loc='lower left')
ax.grid(True, alpha=0.2, which='both')
ax.set_xlim(50000, 2000)
ax.set_ylim(1e-5, 1e7)

plt.tight_layout()
plt.savefig(os.path.join(IMG, "hr_diagram_schematic.png"), dpi=150, bbox_inches='tight')
plt.close()
print("Saved: hr_diagram_schematic.png")


# ============================================================
# 5. Polytrope Structure comparison (M/R vs n)
# ============================================================
print("Generating polytrope structure table...")

# Compute xi_1 and -xi_1^2 * theta'(xi_1) for each n
results = []
for n in indices:
    xi, theta, dtheta, m_bar, xi_1 = solve_lane_emden(n)
    # Find index closest to xi_1
    idx = np.searchsorted(xi, xi_1)
    if idx >= len(xi):
        idx = len(xi) - 1
    xi1 = xi[idx]
    m_bar_surface = m_bar[idx]
    
    # Physical radius and mass (solar units) for a 1 M_sun star with n=3 (Eddington)
    # R = alpha * xi_1, M = alpha^3 * rho_c * 4pi * (-xi^2 theta')_xi1
    # For n=3, alpha = (K/G)^(1/2) * (4pi*rho_c)^((1-n)/(2n)) * rho_c^((1-3n)/(2n))
    # Simplified: report dimensionless quantities
    
    results.append({
        'n': n,
        'xi_1': xi1,
        'm_bar': m_bar_surface,
        'xi_1_pub': xi1_published.get(n, 0),
    })

print(f"\n{'n':>5} | {'xi_1':>10} | {'-xi_1^2 theta1':>15} | {'xi_1 (pub)':>10}")
print("-" * 50)
for r in results:
    pub_str = f"{r['xi_1_pub']:.5f}" if r['xi_1_pub'] else "N/A"
    print(f"{r['n']:>5.1f} | {r['xi_1']:>10.5f} | {r['m_bar']:>15.5f} | {pub_str:>10}")

print("\nAll plots saved to:", IMG)
print("Done.")
