import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path

outdir = Path("docs/research/physics")
outdir.mkdir(parents=True, exist_ok=True)

# Eggleton 1983 Roche-lobe radius fraction
def roche_fraction(q):
    """R_L/a for a star of mass ratio q = M_donor/M_companion."""
    return 0.49 * q**(2/3) / (0.6 * q**(2/3) + np.log(1 + q**(1/3)))

q = np.logspace(-2, 2, 400)
rl_a = roche_fraction(q)

fig, ax = plt.subplots(figsize=(7, 4.5))
ax.semilogx(q, rl_a, 'k-', lw=2)
ax.set_xlabel(r'Mass ratio $q = M_d / M_a$', fontsize=11)
ax.set_ylabel(r'Roche-lobe radius fraction $R_L / a$', fontsize=11)
ax.set_title('Eggleton (1983) Roche-lobe radius vs. mass ratio', fontsize=12)
ax.grid(True, which='both', ls='--', alpha=0.4)
ax.set_xlim(1e-2, 1e2)
ax.set_ylim(0, 0.55)
# annotate the equal-mass point
ax.plot(1.0, roche_fraction(1.0), 'ro', label=r'$q=1: R_L/a \approx 0.38$')
ax.legend(loc='upper left')
fig.tight_layout()
fig.savefig(outdir / 'stellar-mergers-roche-lobe-radius.png', dpi=150)
print("Saved Roche-lobe chart")

# Synthetic entropy-sorting + shock-heating toy model
# Represent a merged star as a set of mass shells sorted by entropy.
# Entropy sorting: s monotonically increasing outward.
# Shock heating adds a bump in the outer regions.
mass_shells = np.linspace(0, 1, 200)
# specific entropy profile: core low, envelope high
s_base = 1.0 + 2.5 * mass_shells + 0.5 * np.sin(4 * np.pi * mass_shells)
# Shock heating: localized at the interface, roughly the pressure-weighted heating term
shock = 0.8 * np.exp(-((mass_shells - 0.35)**2) / 0.02)
# composition: core He-rich (low X_H), envelope H-rich (high X_H)
X_H = 0.1 + 0.7 * mass_shells

fig, axes = plt.subplots(1, 2, figsize=(10, 4.5))

ax1 = axes[0]
ax1.plot(mass_shells, s_base, 'b-', lw=1.5, label='Entropy sorting (ES)')
ax1.plot(mass_shells, s_base + shock, 'r-', lw=1.5, label='ES + shock heating (PM)')
ax1.set_xlabel(r'Enclosed mass fraction $m/M$', fontsize=11)
ax1.set_ylabel(r'Specific entropy $s$ (arb. units)', fontsize=11)
ax1.set_title('Merger remnant entropy profile')
ax1.legend(loc='lower right')
ax1.grid(True, ls='--', alpha=0.4)

ax2 = axes[1]
ax2.plot(mass_shells, X_H, 'g-', lw=1.5)
ax2.set_xlabel(r'Enclosed mass fraction $m/M$', fontsize=11)
ax2.set_ylabel(r'Hydrogen mass fraction $X_H$', fontsize=11)
ax2.set_title('Post-merger composition (synthetic)')
ax2.grid(True, ls='--', alpha=0.4)

fig.tight_layout()
fig.savefig(outdir / 'stellar-mergers-entropy-sorting-toy.png', dpi=150)
print("Saved entropy-sorting toy chart")
