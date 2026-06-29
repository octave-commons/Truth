"""
Sugarscape Model: Wealth Inequality (Gini Coefficient) Dynamics
Shows how inequality emerges from identical agents on equal-opportunity landscapes.

Based on:
- Epstein & Axtell (1996) "Growing Artificial Societies"
- Stevenson (2022) "Dynamics of wealth inequality in simple artificial societies"
- Masumori & Ikegami (2025) "LLM Agents in Sugarscape-Style Simulation"
"""
import numpy as np
import matplotlib.pyplot as plt

np.random.seed(42)

def sugarscape_sim(N_agents=200, grid_size=50, vision_range=4, metabolism=3,
                   sugar_growth_rate=1, max_sugar=4, n_steps=500):
    """Minimal Sugarscape simulation."""
    # Initialize landscape with sugar peaks
    grid = np.zeros((grid_size, grid_size))
    # Two sugar peaks (like original Sugarscape)
    for cx, cy, radius in [(15, 15, 12), (35, 35, 12)]:
        for x in range(grid_size):
            for y in range(grid_size):
                dist = np.sqrt((x - cx)**2 + (y - cy)**2)
                grid[x, y] += max(0, max_sugar - int(dist * max_sugar / radius))
    grid = np.clip(grid, 0, max_sugar)
    max_grid = grid.copy()

    # Place agents randomly
    positions = []
    wealth = []
    for _ in range(N_agents):
        while True:
            x, y = np.random.randint(0, grid_size, 2)
            if (x, y) not in positions:
                break
        positions.append((x, y))
        wealth.append(np.random.randint(5, 25))

    gini_history = []
    wealth_distributions = []

    for step in range(n_steps):
        # Random activation order
        order = np.random.permutation(N_agents)
        for idx in order:
            x, y = positions[idx]
            # Look around within vision
            best_val = -1
            best_pos = (x, y)
            for dx in range(-vision_range, vision_range + 1):
                for dy in range(-vision_range, vision_range + 1):
                    if dx == 0 and dy == 0:
                        continue
                    nx, ny = (x + dx) % grid_size, (y + dy) % grid_size
                    if grid[nx, ny] > best_val:
                        best_val = grid[nx, ny]
                        best_pos = (nx, ny)

            # Move to best location
            old_x, old_y = positions[idx]
            grid[old_x, old_y] = 0  # consume sugar at old location
            positions[idx] = best_pos
            wealth[idx] += grid[best_pos[0], best_pos[1]] - metabolism
            grid[best_pos[0], best_pos[1]] = 0

        # Regrow sugar
        grid = np.minimum(grid + sugar_growth_rate, max_grid)

        # Calculate Gini coefficient
        w = np.array([max(0, wi) for wi in wealth])
        w_sorted = np.sort(w)
        n = len(w_sorted)
        cumsum = np.cumsum(w_sorted)
        gini = (2 * np.sum((np.arange(1, n+1) * w_sorted)) - (n + 1) * np.sum(w_sorted)) / (n * np.sum(w_sorted))
        gini_history.append(gini)

        if step % 100 == 0 or step == n_steps - 1:
            wealth_distributions.append(w.copy())

    return gini_history, wealth_distributions

# Run simulation
gini_hist, wealth_dists = sugarscape_sim()

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Left panel: Gini coefficient over time
ax = axes[0]
ax.plot(gini_hist, color='#b2182b', linewidth=2)
ax.set_xlabel('Time (ticks)', fontsize=12)
ax.set_ylabel('Gini coefficient', fontsize=12)
ax.set_title('Wealth Inequality Emergence\n(Sugarscape, $N=200$, equal agents)', fontsize=13)
ax.set_ylim(0, 1)
ax.axhline(y=0.4, color='gray', linestyle=':', alpha=0.5, label='Typical developed economy')
ax.legend(fontsize=9)
ax.grid(True, alpha=0.3)

# Right panel: Wealth distribution at different times
ax2 = axes[1]
colors = ['#2166ac', '#67a9cf', '#ef8a62', '#b2182b']
time_labels = ['t=0', 't=100', 't=200', 't=499']
for i, (wd, label) in enumerate(zip(wealth_dists, time_labels)):
    ax2.hist(wd, bins=20, alpha=0.5, color=colors[i], label=label, density=True)

ax2.set_xlabel('Wealth', fontsize=12)
ax2.set_ylabel('Density', fontsize=12)
ax2.set_title('Wealth Distribution Evolution\n(Equal agents, equal opportunity)', fontsize=13)
ax2.legend(fontsize=9)

plt.tight_layout()
plt.savefig('/home/err/spaces/Truth/docs/research/culture/img/sugarscape_inequality.png', dpi=150, bbox_inches='tight')
print("Saved: sugarscape_inequality.png")
