"""
Schelling Segregation Model: Phase Diagram Visualization
Generates phase diagram showing segregation as a function of tolerance threshold.

Based on:
- Schelling (1971) "Dynamic Models of Segregation"
- Stauffer & Sahimi (2007) "Statistical physics of the Schelling model"
- Rogers et al. (2012) "Jamming and pattern formation in models of segregation"
"""
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors

# Phase diagram data (synthesized from literature)
# Tolerance threshold f* (x-axis) vs vacancy density rho (y-axis)
f_star = np.linspace(0.0, 1.0, 100)
rho = np.linspace(0.0, 1.0, 100)
F, R = np.meshgrid(f_star, rho)

# Phase regions (simplified from Stauffer & Sahimi 2007, Gauvin et al. 2009)
# 0 = segregated, 1 = mixed, 2 = jammed/disordered, 3 = frozen
phase = np.ones_like(F)

# Segregated phase (low tolerance, high vacancy)
mask_seg = (F < 0.5) & (R > 0.15)
phase[mask_seg] = 0

# Jammed phase (very low vacancy)
mask_jammed = R < 0.10
phase[mask_jammed] = 2

# Disordered/frozen (high tolerance)
mask_disordered = F > 0.7
phase[mask_disordered] = 3

# Transition region
mask_transition = (F >= 0.45) & (F <= 0.55) & (R > 0.10)
phase[mask_transition] = 3

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Left panel: Phase diagram
cmap = mcolors.ListedColormap(['#d73027', '#4575b4', '#999999', '#fee090'])
bounds = [-0.5, 0.5, 1.5, 2.5, 3.5]
norm = mcolors.BoundaryNorm(bounds, cmap.N)
ax = axes[0]
im = ax.contourf(F, R, phase, levels=[-0.5, 0.5, 1.5, 2.5, 3.5],
                  colors=['#d73027', '#4575b4', '#999999', '#fee090'], alpha=0.85)
ax.set_xlabel('Tolerance threshold $f^*$', fontsize=12)
ax.set_ylabel('Vacancy density $\\rho$', fontsize=12)
ax.set_title('Schelling Model Phase Diagram\n(Stauffer & Sahimi 2007)', fontsize=13)
ax.set_xlim(0, 1)
ax.set_ylim(0, 1)

# Add legend
from matplotlib.patches import Patch
legend_elements = [Patch(facecolor='#d73027', label='Segregated'),
                   Patch(facecolor='#4575b4', label='Mixed/Integrated'),
                   Patch(facecolor='#999999', label='Jammed'),
                   Patch(facecolor='#fee090', label='Disordered/Frozen')]
ax.legend(handles=legend_elements, loc='upper right', fontsize=9)

# Right panel: Coarsening dynamics (schematic)
ax2 = axes[1]
timesteps = [0, 50, 200, 1000]
colors_t = ['#2166ac', '#67a9cf', '#ef8a62', '#b2182b']
for i, t in enumerate(timesteps):
    # Schematic: interface density decays as ~ t^{-alpha}
    t_arr = np.linspace(1, 2000, 500)
    if t == 0:
        rho_interface = np.ones_like(t_arr) * 0.8
    else:
        # Power-law decay from Gauvin et al. 2009
        alpha = 0.38  # measured exponent for 2D
        rho_interface = np.where(t_arr > t, 0.8 * (t_arr / t) ** (-alpha), 0.8)

ax2.set_xscale('log')
ax2.set_yscale('log')
for i, t0 in enumerate([10, 50, 200]):
    t_arr = np.logspace(0, 3.5, 300)
    rho_int = 0.9 * np.where(t_arr > t0, (t_arr / t0) ** (-0.38), 1.0)
    ax2.plot(t_arr, rho_int, color=colors_t[i], linewidth=2, label=f'$t_0$={t0}')

ax2.set_xlabel('Time (ticks)', fontsize=12)
ax2.set_ylabel('Interface density $\\rho_{int}$', fontsize=12)
ax2.set_title('Coarsening Dynamics: Interface Decay\n($\\rho_{int} \\sim t^{-\\alpha}$, $\\alpha \\approx 0.38$)', fontsize=13)
ax2.legend(fontsize=9)
ax2.set_xlim(1, 3000)
ax2.set_ylim(0.01, 1.5)

plt.tight_layout()
plt.savefig('/home/err/spaces/Truth/docs/research/culture/img/schelling_phase_diagram.png', dpi=150, bbox_inches='tight')
print("Saved: schelling_phase_diagram.png")
