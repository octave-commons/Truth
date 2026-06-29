#!/usr/bin/env python3
"""
Eigen Error Threshold Phase Portrait

Generates a visualization of the quasispecies model showing:
1. Master sequence fraction vs mutation rate (error threshold)
2. Phase portrait of the Eigen model near the threshold

For the abiogenesis research notebook.
"""
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.gridspec import GridSpec

# ── Eigen quasispecies model (single-peak landscape) ──
def eigen_master_fraction(mu, L, sigma):
    """
    Fraction of master sequence in quasispecies model.
    mu: per-nucleotide mutation rate
    L:  genome length
    sigma: selective advantage (f_master / f_other)
    """
    q = (1 - mu) ** L  # fraction of error-free copies
    # For single-peak landscape, master fraction is:
    # x_m = (sigma * q - 1) / (sigma - 1)  when sigma * q > 1
    # x_m = 0  when sigma * q <= 1
    numerator = sigma * q - 1
    denominator = sigma - 1
    result = np.where(numerator > 0, numerator / denominator, 0.0)
    return np.clip(result, 0, 1)


def error_threshold_mu(L, sigma):
    """Critical mutation rate: mu_c = ln(sigma) / L"""
    return np.log(sigma) / L


# ── Figure 1: Master fraction vs mutation rate for various genome lengths ──
fig = plt.figure(figsize=(14, 10))
gs = GridSpec(2, 2, figure=fig, hspace=0.3, wspace=0.3)

# Panel A: Master fraction vs mu for different L
ax1 = fig.add_subplot(gs[0, 0])
sigma = 2.0  # selective advantage
L_values = [10, 50, 100, 200, 500]
mu = np.linspace(0, 0.1, 1000)

for L in L_values:
    frac = eigen_master_fraction(mu, L, sigma)
    mu_c = error_threshold_mu(L, sigma)
    ax1.plot(mu, frac, label=f'L={L}', linewidth=2)
    ax1.axvline(mu_c, linestyle='--', alpha=0.4, linewidth=1)

ax1.set_xlabel('Per-nucleotide mutation rate μ', fontsize=12)
ax1.set_ylabel('Master sequence fraction $x_m$', fontsize=12)
ax1.set_title('A. Error Threshold: Quasispecies Collapse', fontsize=13, fontweight='bold')
ax1.legend(fontsize=10)
ax1.set_xlim(0, 0.1)
ax1.set_ylim(-0.05, 1.05)
ax1.grid(True, alpha=0.3)

# Panel B: Critical mutation rate vs genome length
ax2 = fig.add_subplot(gs[0, 1])
L_range = np.arange(10, 1001, 1)
sigma_values = [1.5, 2.0, 5.0, 10.0]

for sigma_val in sigma_values:
    mu_c = error_threshold_mu(L_range, sigma_val)
    ax2.plot(L_range, mu_c * 100, label=f'σ={sigma_val}', linewidth=2)

ax2.set_xlabel('Genome length L (nucleotides)', fontsize=12)
ax2.set_ylabel('Critical mutation rate μ_c (%)', fontsize=12)
ax2.set_title('B. Error Threshold Constraint: μ_c = ln(σ)/L', fontsize=13, fontweight='bold')
ax2.legend(fontsize=10)
ax2.set_xlim(10, 1000)
ax2.set_ylim(0, 15)
ax2.grid(True, alpha=0.3)

# Annotate the RNA virus / DNA organism transition
ax2.axvline(10000, color='red', linestyle=':', alpha=0.5)
ax2.annotate('RNA viruses\n(~10⁴ nt)', xy=(10000, 0.015), fontsize=9, color='red',
             ha='center', va='bottom')

# Panel C: Phase diagram - mu vs L with regions
ax3 = fig.add_subplot(gs[1, 0])
L_grid = np.linspace(10, 2000, 500)
mu_grid = np.linspace(0, 0.05, 500)
L_mesh, mu_mesh = np.meshgrid(L_grid, mu_grid)

sigma = 2.0
q_mesh = (1 - mu_mesh) ** L_mesh
order_param = np.where(sigma * q_mesh > 1, (sigma * q_mesh - 1) / (sigma - 1), 0.0)

contour = ax3.contourf(L_mesh, mu_mesh, order_param, levels=50, cmap='viridis')
plt.colorbar(contour, ax=ax3, label='Master fraction $x_m$')

# Plot the critical line
L_crit = np.linspace(10, 2000, 200)
mu_crit = np.log(sigma) / L_crit
ax3.plot(L_crit, mu_crit, 'r-', linewidth=2.5, label='Error threshold')
ax3.fill_between(L_crit, mu_crit, 0.05, alpha=0.15, color='red', label='Error catastrophe')
ax3.fill_between(L_crit, 0, mu_crit, alpha=0.1, color='blue', label='Quasispecies')

ax3.set_xlabel('Genome length L', fontsize=12)
ax3.set_ylabel('Mutation rate μ', fontsize=12)
ax3.set_title('C. Phase Diagram: Quasispecies vs Error Catastrophe', fontsize=13, fontweight='bold')
ax3.legend(fontsize=10, loc='upper left')
ax3.set_xlim(10, 2000)
ax3.set_ylim(0, 0.05)

# Panel D: Information capacity constraint
ax4 = fig.add_subplot(gs[1, 1])
# Show the tradeoff: max genome length vs replication fidelity
fidelity = np.linspace(0.95, 1.0, 1000)  # per-nucleotide fidelity
mu_vals = 1 - fidelity

for sigma_val in [1.5, 2.0, 5.0]:
    L_max = np.log(sigma_val) / mu_vals
    # Cap at reasonable values
    L_max = np.minimum(L_max, 50000)
    ax4.plot(fidelity * 100, L_max, label=f'σ={sigma_val}', linewidth=2)

ax4.axhline(100, color='gray', linestyle=':', alpha=0.5)
ax4.annotate('Short ribozymes', xy=(99.7, 120), fontsize=9, color='gray')
ax4.axhline(5000, color='gray', linestyle=':', alpha=0.5)
ax4.annotate('RNA viruses', xy=(99.7, 5500), fontsize=9, color='gray')
ax4.axhline(600000, color='gray', linestyle=':', alpha=0.5)
ax4.annotate('Minimal bacteria', xy=(99.7, 700000), fontsize=9, color='gray')

ax4.set_xlabel('Per-nucleotide fidelity (%)', fontsize=12)
ax4.set_ylabel('Maximum genome length L', fontsize=12)
ax4.set_title('D. Eigen\'s Paradox: Fidelity vs Complexity', fontsize=13, fontweight='bold')
ax4.set_yscale('log')
ax4.legend(fontsize=10)
ax4.set_xlim(99.5, 100.0)
ax4.grid(True, alpha=0.3)

fig.suptitle('Eigen Quasispecies Model and the Error Threshold', fontsize=16, fontweight='bold', y=1.02)
plt.tight_layout()
plt.savefig('/home/err/spaces/Truth/docs/research/biology/img/error_threshold_phase_portrait.png', 
            dpi=150, bbox_inches='tight')
print("Saved: error_threshold_phase_portrait.png")
