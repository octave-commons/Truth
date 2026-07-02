#!/usr/bin/env python3
"""Toy 3D Barnes-Hut accuracy and timing experiment.

Compares direct O(N^2) gravity with a monopole Barnes-Hut octree for
several opening angles theta.  Outputs a chart to
img/barnes_hut_theta_accuracy.png and prints a markdown table.

This is a research-only toy model; it is not meant to be a production
kernel.
"""

import time
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


# ---------------------------------------------------------------------------
# Direct summation
# ---------------------------------------------------------------------------

def direct_accelerations(pos, mass, G=1.0, eps=1e-3):
    """Return Nx3 accelerations via direct O(N^2) summation."""
    n = pos.shape[0]
    acc = np.zeros_like(pos)
    for i in range(n):
        dx = pos - pos[i]              # Nx3
        r2 = np.einsum('ij,ij->i', dx, dx) + eps * eps
        inv_r3 = G * mass / (r2 * np.sqrt(r2))
        inv_r3[i] = 0.0                # no self-force
        acc[i] = np.einsum('i,ij->j', inv_r3, dx)
    return acc


# ---------------------------------------------------------------------------
# Barnes-Hut octree
# ---------------------------------------------------------------------------

class BHNode:
    __slots__ = ('center', 'half', 'com', 'mass', 'eps2', 'children', 'body')

    def __init__(self, center, half):
        self.center = np.asarray(center, dtype=float)   # 3
        self.half = float(half)                         # half side length
        self.com = np.zeros(3)
        self.mass = 0.0
        self.children = None
        self.body = None


def _octant_index(node, pos):
    return ((pos[0] >= node.center[0]) +
            ((pos[1] >= node.center[1]) << 1) +
            ((pos[2] >= node.center[2]) << 2))


def _insert(node, pos, mass, body_id):
    if node.body is None and node.children is None:
        # empty leaf -> store body
        node.body = (pos.copy(), mass, body_id)
        node.com = pos.copy() * mass
        node.mass = mass
        return

    if node.children is None:
        # subdivide: move existing body into child
        node.children = [None] * 8
        old_pos, old_mass, old_id = node.body
        node.body = None
        _insert_into_child(node, old_pos, old_mass, old_id)

    _insert_into_child(node, pos, mass, body_id)
    node.com += pos * mass
    node.mass += mass


def _insert_into_child(node, pos, mass, body_id):
    idx = _octant_index(node, pos)
    h = node.half * 0.5
    if node.children[idx] is None:
        offset = np.array([
            (1 if (idx & 1) else -1) * h,
            (1 if (idx & 2) else -1) * h,
            (1 if (idx & 4) else -1) * h,
        ])
        node.children[idx] = BHNode(node.center + offset, h)
    _insert(node.children[idx], pos, mass, body_id)


def build_tree(pos, mass):
    minc = pos.min(axis=0)
    maxc = pos.max(axis=0)
    center = (minc + maxc) * 0.5
    half = max((maxc - minc).max() * 0.5, 1e-6)
    root = BHNode(center, half * 1.01)  # slight padding
    for i in range(pos.shape[0]):
        _insert(root, pos[i], mass[i], i)
    return root


def _traverse(node, p, G, eps2, theta2, self_id, acc):
    if node is None:
        return

    dx = node.com / node.mass - p if node.mass > 0 else np.zeros(3)
    d2 = float(np.dot(dx, dx))

    # Leaf with a single body -> direct interaction
    if node.children is None:
        if node.body is None or node.body[2] == self_id:
            return
        bpos, bm, _ = node.body
        dx = bpos - p
        d2 = float(np.dot(dx, dx)) + eps2
        inv_r3 = G * bm / (d2 * np.sqrt(d2))
        acc += dx * inv_r3
        return

    s2 = (2.0 * node.half) ** 2
    if s2 < theta2 * d2 and node.mass > 0:
        d2 = d2 + eps2
        inv_r3 = G * node.mass / (d2 * np.sqrt(d2))
        acc += dx * inv_r3
        return

    for c in node.children:
        _traverse(c, p, G, eps2, theta2, self_id, acc)


def bh_accelerations(pos, mass, theta, G=1.0, eps=1e-3):
    root = build_tree(pos, mass)
    n = pos.shape[0]
    acc = np.zeros_like(pos)
    eps2 = eps * eps
    theta2 = theta * theta
    for i in range(n):
        _traverse(root, pos[i], G, eps2, theta2, i, acc[i])
    return acc, root


# ---------------------------------------------------------------------------
# Experiment
# ---------------------------------------------------------------------------

def run_experiment(n=500, seed=42, eps=1e-3, thetas=None, repeats=5):
    rng = np.random.default_rng(seed)
    pos = rng.normal(0.0, 1.0, (n, 3))
    mass = np.ones(n) / n

    t0 = time.perf_counter()
    acc_direct = direct_accelerations(pos, mass, eps=eps)
    t_direct = time.perf_counter() - t0

    if thetas is None:
        thetas = [0.3, 0.5, 0.7, 1.0, 1.3]

    results = []
    for theta in thetas:
        acc_bh = None
        times = []
        for _ in range(repeats):
            t0 = time.perf_counter()
            acc_bh, _ = bh_accelerations(pos, mass, theta, eps=eps)
            times.append(time.perf_counter() - t0)
        t_bh = min(times)  # best-of to reduce jitter
        diff = acc_bh - acc_direct
        rel_rms = float(np.sqrt(np.mean(diff * diff)) /
                        np.sqrt(np.mean(acc_direct * acc_direct)))
        rel_max = float(np.max(np.linalg.norm(diff, axis=1) /
                               np.linalg.norm(acc_direct, axis=1)))
        results.append({
            'theta': theta,
            't_bh_ms': t_bh * 1000.0,
            'rel_rms': rel_rms,
            'rel_max': rel_max,
        })

    return {
        'n': n,
        'eps': eps,
        't_direct_ms': t_direct * 1000.0,
        'results': results,
    }


def plot(results, out_path):
    thetas = [r['theta'] for r in results['results']]
    rel_rms = [r['rel_rms'] for r in results['results']]
    rel_max = [r['rel_max'] for r in results['results']]
    t_bh = [r['t_bh_ms'] for r in results['results']]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11, 4.5))

    ax1.loglog(thetas, rel_rms, 'o-', label='RMS force error')
    ax1.loglog(thetas, rel_max, 's--', label='max force error')
    ax1.set_xlabel(r'Opening angle $\theta$')
    ax1.set_ylabel('Relative acceleration error')
    ax1.set_title(rf"Barnes-Hut accuracy (N={results['n']}, $\epsilon$={results['eps']:.0e})")
    ax1.legend()
    ax1.grid(True, which='both', ls='--', lw=0.5)

    ax2.semilogy(thetas, t_bh, 'o-', color='tab:green', label='Barnes-Hut')
    ax2.axhline(results['t_direct_ms'], color='tab:red', ls='--',
                label=f"Direct O(N²) = {results['t_direct_ms']:.1f} ms")
    ax2.set_xlabel(r'Opening angle $\theta$')
    ax2.set_ylabel('Time per force evaluation (ms)')
    ax2.set_title(f"Timing on this machine (best of {len(results['results'])} runs)")
    ax2.legend()
    ax2.grid(True, which='both', ls='--', lw=0.5)

    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    print(f"Saved chart to {out_path}")


def main():
    out_dir = __import__('pathlib').Path(__file__).parent / 'img'
    out_dir.mkdir(exist_ok=True)
    out_png = out_dir / 'barnes_hut_theta_accuracy.png'

    res = run_experiment(n=500, eps=1e-3, thetas=[0.3, 0.5, 0.7, 1.0, 1.3],
                         repeats=5)
    plot(res, out_png)

    print(f"\nN = {res['n']}, softening eps = {res['eps']:.1e}")
    print(f"Direct O(N^2) time = {res['t_direct_ms']:.2f} ms\n")
    print("| theta | t_BH (ms) | rel RMS error | rel max error |")
    print("|------:|----------:|--------------:|--------------:|")
    for r in res['results']:
        print(f"| {r['theta']:.2f} | {r['t_bh_ms']:.3f} | "
              f"{r['rel_rms']:.3e} | {r['rel_max']:.3e} |")


if __name__ == '__main__':
    main()
