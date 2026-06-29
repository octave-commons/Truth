"""
Epstein Civil Violence Model: Phase Diagram
Shows regime transitions as a function of legitimacy and agent density.

Based on:
- Epstein (2002) "Modeling civil violence: An agent-based computational approach"
- Ormazábal et al. (2022) "Phase diagram of the civil disorder model"
"""
import numpy as np
import matplotlib.pyplot as plt

# Phase diagram: legitimacy (L) vs cop-to-agent ratio (C/A)
L = np.linspace(0, 1, 100)
cop_ratio = np.linspace(0, 0.15, 100)
L_grid, C_grid = np.meshgrid(L, cop_ratio)

# Regime classification (simplified from Ormazábal et al. 2022)
# Active rebellion: low legitimacy, low cop density
# Suppressed: high cop density or high legitimacy
# Oscillating: intermediate regime

regime = np.zeros_like(L_grid)

# Active rebellion region
mask_rebellion = (L_grid < 0.5) & (C_grid < 0.04)
regime[mask_rebellion] = 2

# Oscillating/unstable region
mask_oscillate = (L_grid < 0.6) & (L_grid > 0.3) & (C_grid < 0.08) & (C_grid > 0.02)
regime[mask_oscillate] = 1

# Suppressed region (high cops or high legitimacy)
mask_suppressed = (C_grid > 0.08) | (L_grid > 0.7)
regime[mask_suppressed] = 0

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Left panel: Phase diagram
ax = axes[0]
from matplotlib.colors import ListedColormap
cmap = ListedColormap(['#4575b4', '#fee090', '#d73027'])
c = ax.pcolormesh(L_grid, C_grid, regime, cmap=cmap, shading='auto', alpha=0.85)
ax.set_xlabel('Regime legitimacy $L$', fontsize=12)
ax.set_ylabel('Cop-to-agent ratio $k$', fontsize=12)
ax.set_title('Epstein Civil Violence: Phase Diagram\n(Ormaźabal et al. 2022)', fontsize=13)

from matplotlib.patches import Patch
legend_elements = [Patch(facecolor='#4575b4', label='Suppressed (stable)'),
                   Patch(facecolor='#fee090', label='Oscillating'),
                   Patch(facecolor='#d73027', label='Active rebellion')]
ax.legend(handles=legend_elements, loc='upper left', fontsize=9)

# Right panel: Time series of violence level (schematic)
ax2 = axes[1]
np.random.seed(42)
t = np.arange(0, 500)

# Three regimes
violence_suppressed = np.ones(500) * 0.05 + np.random.normal(0, 0.02, 500)
violence_oscillate = 0.3 + 0.2 * np.sin(t * 0.05) + np.random.normal(0, 0.05, 500)
violence_rebellion = 0.7 + 0.15 * np.sin(t * 0.03) + np.random.normal(0, 0.08, 500)

violence_suppressed = np.clip(violence_suppressed, 0, 1)
violence_oscillate = np.clip(violence_oscillate, 0, 1)
violence_rebellion = np.clip(violence_rebellion, 0, 1)

ax2.plot(t, violence_suppressed, color='#4575b4', alpha=0.7, label='$L=0.8, k=0.10$ (suppressed)')
ax2.plot(t, violence_oscillate, color='#fee090', alpha=0.7, label='$L=0.45, k=0.05$ (oscillating)')
ax2.plot(t, violence_rebellion, color='#d7302b', alpha=0.7, label='$L=0.2, k=0.02$ (rebellion)')

ax2.set_xlabel('Time (ticks)', fontsize=12)
ax2.set_ylabel('Active rebellion fraction', fontsize=12)
ax2.set_title('Civil Violence Time Series\n(Three Regimes)', fontsize=13)
ax2.legend(fontsize=9)
ax2.set_ylim(0, 1)
ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig('/home/err/spaces/Truth/docs/research/culture/img/civil_violence_phase.png', dpi=150, bbox_inches='tight')
print("Saved: civil_violence_phase.png")
