"""
Opinion Dynamics: Bifurcation Diagram for Bounded Confidence Models
Shows cluster count as a function of confidence bound epsilon.

Based on:
- Deffuant et al. (2000) "Mixing beliefs among interacting agents"
- Hegselmann & Krause (2002) "Opinion Dynamics and Bounded Confidence"
- Lorenz (2007) "Continuous Opinion Dynamics under Bounded Confidence: A Survey"
"""
import numpy as np
import matplotlib.pyplot as plt

# Bifurcation diagram data
# For uniform initial opinions on [0,1], the number of clusters is:
# N_clusters = floor(1 / (2*epsilon)) + 1  (for epsilon < 0.5)
# At epsilon = 0.5, consensus is reached (1 cluster)

epsilon = np.linspace(0.05, 0.55, 200)
n_clusters = np.floor(1.0 / (2.0 * epsilon)) + 1
n_clusters = np.clip(n_clusters, 1, 20)

# Critical threshold for consensus
eps_consensus = 0.5  # (approximately, for DW model; HK model ~0.19 for N=100)

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Left panel: Bifurcation diagram
ax = axes[0]
ax.step(epsilon, n_clusters, where='mid', color='#2166ac', linewidth=2.5)
ax.axvline(x=0.5, color='#b2182b', linestyle='--', linewidth=1.5, label='$\\epsilon_c \\approx 0.5$ (consensus threshold)')
ax.set_xlabel('Confidence bound $\\epsilon$', fontsize=12)
ax.set_ylabel('Number of opinion clusters', fontsize=12)
ax.set_title('Bifurcation: Deffuant-Weisbuch Model\n(Uniform initial opinions)', fontsize=13)
ax.set_xlim(0.05, 0.55)
ax.set_ylim(0, 22)
ax.legend(fontsize=10)
ax.grid(True, alpha=0.3)

# Add annotation
ax.annotate('Consensus\n(1 cluster)', xy=(0.5, 1), xytext=(0.42, 5),
            arrowprops=dict(arrowstyle='->', color='#b2182b'),
            fontsize=10, color='#b2182b')

# Right panel: Schematic opinion trajectories for different epsilon
ax2 = axes[1]
np.random.seed(42)
N_agents = 50
x0 = np.sort(np.random.uniform(0, 1, N_agents))

for eps_val, color, label in [(0.1, '#d73027', '$\\epsilon=0.1$ (fragmented)'),
                                (0.3, '#4575b4', '$\\epsilon=0.3$ (few clusters)'),
                                (0.5, '#1a9850', '$\\epsilon=0.5$ (consensus)')]:
    # Simulate DW model for a few steps
    x = x0.copy()
    trajectory = [x.copy()]
    for step in range(200):
        i, j = np.random.choice(N_agents, 2, replace=False)
        if abs(x[i] - x[j]) < eps_val:
            mu = 0.5  # convergence parameter
            x[i] += mu * (x[j] - x[i])
            x[j] += mu * (x[i] - x[j])
        trajectory.append(x.copy())

    # Plot final distribution
    ax2.hist(trajectory[-1], bins=30, alpha=0.5, color=color, label=label, density=True)

ax2.set_xlabel('Opinion value', fontsize=12)
ax2.set_ylabel('Density', fontsize=12)
ax2.set_title('Final Opinion Distributions\n(Deffuant-Weisbuch, $\\mu=0.5$, $N=50$)', fontsize=13)
ax2.legend(fontsize=9)
ax2.set_xlim(0, 1)

plt.tight_layout()
plt.savefig('/home/err/spaces/Truth/docs/research/culture/img/opinion_dynamics_bifurcation.png', dpi=150, bbox_inches='tight')
print("Saved: opinion_dynamics_bifurcation.png")
