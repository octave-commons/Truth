#!/usr/bin/env python3
"""
RAF Theory Phase Transition and Autocatalytic Set Emergence

Generates visualizations for the abiogenesis research notebook:
1. Phase transition in catalytic probability (percolation threshold)
2. Nested autocatalytic set hierarchy
3. Abiogenesis landscape: energy vs molecular diversity
"""
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.gridspec import GridSpec
from matplotlib.patches import FancyArrowPatch

fig = plt.figure(figsize=(16, 12))
gs = GridSpec(2, 2, figure=fig, hspace=0.35, wspace=0.3)

# ── Panel A: Phase transition in catalytic probability ──
ax1 = fig.add_subplot(gs[0, 0])

# Binary polymer model: probability of RAF existence vs catalysis probability p
# For polymer length n, the critical catalysis probability scales as p_c ~ 2^{-n}
n_values = [5, 8, 12, 20]
p = np.linspace(0, 0.3, 1000)

for n in n_values:
    # Approximate RAF probability using percolation-like transition
    # The transition sharpens with n. Using a sigmoid approximation:
    # P(RAF) ~ 1 - exp(-C * (p/p_c)^2) for p > p_c
    p_c = 1.0 / (2 ** (n / 2))  # approximate scaling
    # Smooth transition using logistic function
    sharpness = n * 10
    prob = 1.0 / (1.0 + np.exp(-sharpness * (p - p_c) / p_c))
    # Below p_c, probability is essentially 0
    prob = np.where(p < p_c * 0.5, 0, prob)
    ax1.plot(p, prob, linewidth=2.5, label=f'n={n}')

ax1.set_xlabel('Catalysis probability p', fontsize=12)
ax1.set_ylabel('P(RAF exists)', fontsize=12)
ax1.set_title('A. Phase Transition in Autocatalytic Sets', fontsize=13, fontweight='bold')
ax1.legend(fontsize=10, title='Polymer length')
ax1.set_xlim(0, 0.3)
ax1.set_ylim(-0.05, 1.05)
ax1.grid(True, alpha=0.3)

# Annotate
ax1.annotate('Percolation\nthreshold', xy=(0.02, 0.5), fontsize=10,
             ha='center', color='red', fontweight='bold',
             arrowprops=dict(arrowstyle='->', color='red'),
             xytext=(0.08, 0.3))

# ── Panel B: Nested autocatalytic set hierarchy ──
ax2 = fig.add_subplot(gs[0, 1])
ax2.set_xlim(0, 10)
ax2.set_ylim(0, 10)
ax2.set_aspect('equal')
ax2.axis('off')
ax2.set_title('B. Nested RAF Hierarchy (Cascade)', fontsize=13, fontweight='bold')

# Draw nested circles representing RAF sets
from matplotlib.patches import Circle

# Outer RAF (largest, least stable alone)
c3 = Circle((5, 5), 4.5, fill=False, edgecolor='red', linewidth=2, linestyle='--', label='RAF₃ (large catalysts)')
ax2.add_patch(c3)
ax2.text(5, 9.7, 'RAF₃: 400+ monomers', ha='center', fontsize=10, color='red', fontweight='bold')

# Middle RAF
c2 = Circle((5, 5), 3.0, fill=False, edgecolor='blue', linewidth=2, label='RAF₂ (medium)')
ax2.add_patch(c2)
ax2.text(5, 8.3, 'RAF₂: ~50 monomers', ha='center', fontsize=10, color='blue', fontweight='bold')

# Inner RAF (smallest, most stable)
c1 = Circle((5, 5), 1.5, fill=True, facecolor='green', alpha=0.3, edgecolor='green', linewidth=2.5, label='RAF₁ (small catalysts)')
ax2.add_patch(c1)
ax2.text(5, 6.8, 'RAF₁: ~10 monomers', ha='center', fontsize=10, color='green', fontweight='bold')

# Center dot
ax2.plot(5, 5, 'ko', markersize=8)
ax2.text(5, 4.3, 'Food set\n(monomers)', ha='center', fontsize=9, fontweight='bold')

# Arrows showing reinforcement
ax2.annotate('', xy=(5, 3.5), xytext=(5, 2),
             arrowprops=dict(arrowstyle='->', color='green', lw=2))
ax2.text(6.5, 2.7, 'Reinforces', fontsize=9, color='green', style='italic')

ax2.annotate('', xy=(5, 2.5), xytext=(7, 1),
             arrowprops=dict(arrowstyle='->', color='blue', lw=2))
ax2.text(7.2, 0.5, 'Catalytic\nstrength ∝ size', fontsize=9, color='blue', style='italic')

ax2.text(5, -0.3, 'Nested ACS: small RAFs bootstrap larger ones', 
         ha='center', fontsize=10, fontweight='bold', style='italic')

# ── Panel C: Abiogenesis landscape - three scenarios ──
ax3 = fig.add_subplot(gs[1, 0])

# Schematic showing the three main hypotheses
x = np.linspace(0, 10, 200)

# RNA World: high barrier, sharp transition
rna_barrier = 3 * np.exp(-0.5 * (x - 5)**2) + 0.5 * np.sin(x * 0.8)
ax3.plot(x, rna_barrier + 2, 'b-', linewidth=2.5, label='RNA World')

# Metabolism First: gradual rise
metab_barrier = 1.5 * (1 - np.exp(-0.3 * x)) + 0.3 * np.sin(x * 1.2)
ax3.plot(x, metab_barrier, 'g-', linewidth=2.5, label='Metabolism First (Iron-Sulfur)')

# Hydrothermal Vents: moderate barrier with oscillations
vent_barrier = 2 * (1 - np.exp(-0.5 * x)) + 0.8 * np.sin(x * 1.5) * np.exp(-0.1 * x)
ax3.plot(x, vent_barrier, 'r-', linewidth=2.5, label='Hydrothermal Vents')

ax3.set_xlabel('Molecular diversity / Time', fontsize=12)
ax3.set_ylabel('System complexity / Free energy', fontsize=12)
ax3.set_title('C. Abiogenesis Landscape: Three Scenarios', fontsize=13, fontweight='bold')
ax3.legend(fontsize=10)
ax3.grid(True, alpha=0.3)

# Annotate phase transitions
ax3.annotate('Error\ncatastrophe\nbarrier', xy=(5, 5.5), fontsize=9, 
             ha='center', color='blue', fontweight='bold')
ax3.annotate('Catalytic\nclosure\nthreshold', xy=(7, 1.8), fontsize=9,
             ha='center', color='green', fontweight='bold')
ax3.annotate('Proton\ngradient\nexploitation', xy=(3, 2.5), fontsize=9,
             ha='center', color='red', fontweight='bold')

# ── Panel D: Thermodynamic Abiogenesis Likelihood Model (TALM) ──
ax4 = fig.add_subplot(gs[1, 1])

# Energy input vs persistence probability
energy = np.linspace(0, 5, 200)
# TALM: P(persistence) = 1 - exp(-R_n * (E_in - E_min) / kT)
# where R_n is network resilience
R_n_values = [0.5, 1.0, 2.0, 5.0]
E_min = 1.0  # minimum energy threshold
kT = 1.0

for R_n in R_n_values:
    # Shifted sigmoid
    exponent = -R_n * np.maximum(energy - E_min, 0) / kT
    prob = 1 - np.exp(exponent)
    prob = np.where(energy >= E_min, prob, 0)
    ax4.plot(energy, prob, linewidth=2.5, label=f'$R_n$={R_n}')

ax4.axvline(E_min, color='gray', linestyle=':', alpha=0.5)
ax4.text(E_min + 0.1, 0.1, '$E_{min}$', fontsize=12, color='gray')

ax4.set_xlabel('Environmental energy input $E_{in}$', fontsize=12)
ax4.set_ylabel('Persistence probability', fontsize=12)
ax4.set_title('D. TALM: Thermodynamic Abiogenesis Likelihood', fontsize=13, fontweight='bold')
ax4.legend(fontsize=10, title='Network resilience')
ax4.set_xlim(0, 5)
ax4.set_ylim(-0.05, 1.05)
ax4.grid(True, alpha=0.3)

fig.suptitle('Abiogenesis: Phase Transitions, Autocatalysis, and Thermodynamic Selection', 
             fontsize=16, fontweight='bold', y=1.02)
plt.tight_layout()
plt.savefig('/home/err/spaces/Truth/docs/research/biology/img/raf_phase_transition.png',
            dpi=150, bbox_inches='tight')
print("Saved: raf_phase_transition.png")
