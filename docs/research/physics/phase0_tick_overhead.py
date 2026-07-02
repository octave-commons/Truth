#!/usr/bin/env python3
"""
Toy benchmark: ECS component-access and parallel-fan-out overhead.

Models the per-tick cost of three access patterns for N bodies:
  1. flat primitive arrays (SoA, no indirection)
  2. ECS map-of-maps with archetype-indexed queries
  3. ECS with per-component hash maps and get-in lookups

Also models parallel fan-out overhead as a function of chunk count.

Outputs a chart to docs/research/physics/phase0_tick_overhead.png
"""

import time
import random
import math
import numpy as np
import matplotlib.pyplot as plt

random.seed(42)


def make_primitive_world(n):
    """Structure-of-arrays: contiguous float arrays."""
    return {
        'px': np.zeros(n, dtype=np.float64),
        'py': np.zeros(n, dtype=np.float64),
        'pz': np.zeros(n, dtype=np.float64),
        'vx': np.zeros(n, dtype=np.float64),
        'vy': np.zeros(n, dtype=np.float64),
        'vz': np.zeros(n, dtype=np.float64),
        'mass': np.ones(n, dtype=np.float64),
    }


def make_ecs_map_of_maps(n):
    """ECS as {eid -> {component -> value}} plus an archetype index."""
    world = {'components': {}, 'archetypes': {}}
    for eid in range(n):
        world['components'][eid] = {
            'component/position': [0.0, 0.0, 0.0],
            'component/velocity': [0.0, 0.0, 0.0],
            'component/mass': 1.0,
        }
        world['archetypes'][eid] = {'component/position',
                                    'component/velocity',
                                    'component/mass'}
    return world


def make_ecs_component_columns(n):
    """ECS as {component -> {eid -> value}}."""
    return {
        'component/position': {eid: [0.0, 0.0, 0.0] for eid in range(n)},
        'component/velocity': {eid: [0.0, 0.0, 0.0] for eid in range(n)},
        'component/mass': {eid: 1.0 for eid in range(n)},
    }


def gravity_primitive(world, n, dt):
    """Direct N^2 gravity on primitive arrays (baseline physics cost)."""
    px, py, pz = world['px'], world['py'], world['pz']
    vx, vy, vz = world['vx'], world['vy'], world['vz']
    mass = world['mass']
    G = 1.0
    eps2 = 0.01
    for i in range(n):
        ax = ay = az = 0.0
        for j in range(n):
            if i == j:
                continue
            dx = px[j] - px[i]
            dy = py[j] - py[i]
            dz = pz[j] - pz[i]
            r2 = dx*dx + dy*dy + dz*dz + eps2
            inv_r3 = 1.0 / (r2 * math.sqrt(r2))
            fac = G * mass[j] * inv_r3
            ax += fac * dx
            ay += fac * dy
            az += fac * dz
        vx[i] += ax * dt
        vy[i] += ay * dt
        vz[i] += az * dt
        px[i] += vx[i] * dt
        py[i] += vy[i] * dt
        pz[i] += vz[i] * dt


def ecs_map_of_maps_query(world):
    """Return all entities with position+velocity+mass (archetype scan)."""
    need = {'component/position', 'component/velocity', 'component/mass'}
    return [eid for eid, arch in world['archetypes'].items()
            if need.issubset(arch)]


def gravity_ecs_map_of_maps(world, eids, dt):
    """N^2 gravity using ECS map-of-maps lookups."""
    comps = world['components']
    G = 1.0
    eps2 = 0.01
    for i in eids:
        pi = comps[i]['component/position']
        vi = comps[i]['component/velocity']
        ax = ay = az = 0.0
        for j in eids:
            if i == j:
                continue
            pj = comps[j]['component/position']
            dx = pj[0] - pi[0]
            dy = pj[1] - pi[1]
            dz = pj[2] - pi[2]
            r2 = dx*dx + dy*dy + dz*dz + eps2
            inv_r3 = 1.0 / (r2 * math.sqrt(r2))
            fac = G * comps[j]['component/mass'] * inv_r3
            ax += fac * dx
            ay += fac * dy
            az += fac * dz
        vi[0] += ax * dt
        vi[1] += ay * dt
        vi[2] += az * dt
        pi[0] += vi[0] * dt
        pi[1] += vi[1] * dt
        pi[2] += vi[2] * dt


def gravity_ecs_columns(cols, eids, dt):
    """N^2 gravity using per-component hash-map lookups."""
    pos = cols['component/position']
    vel = cols['component/velocity']
    mass = cols['component/mass']
    G = 1.0
    eps2 = 0.01
    for i in eids:
        pi = pos[i]
        vi = vel[i]
        ax = ay = az = 0.0
        for j in eids:
            if i == j:
                continue
            pj = pos[j]
            dx = pj[0] - pi[0]
            dy = pj[1] - pi[1]
            dz = pj[2] - pi[2]
            r2 = dx*dx + dy*dy + dz*dz + eps2
            inv_r3 = 1.0 / (r2 * math.sqrt(r2))
            fac = G * mass[j] * inv_r3
            ax += fac * dx
            ay += fac * dy
            az += fac * dz
        vi[0] += ax * dt
        vi[1] += ay * dt
        vi[2] += az * dt
        pi[0] += vi[0] * dt
        pi[1] += vi[1] * dt
        pi[2] += vi[2] * dt


def bench(pattern, ns):
    """Return median wall-clock seconds for one tick at each N."""
    times = []
    for n in ns:
        dt = 0.001
        if pattern == 'primitive':
            w = make_primitive_world(n)
            # compile/first-touch outside timing
            _ = gravity_primitive(w, n, dt)
            t0 = time.perf_counter()
            gravity_primitive(w, n, dt)
            t1 = time.perf_counter()
        elif pattern == 'ecs-map-of-maps':
            w = make_ecs_map_of_maps(n)
            eids = ecs_map_of_maps_query(w)
            gravity_ecs_map_of_maps(w, eids, dt)
            t0 = time.perf_counter()
            gravity_ecs_map_of_maps(w, eids, dt)
            t1 = time.perf_counter()
        elif pattern == 'ecs-columns':
            cols = make_ecs_component_columns(n)
            eids = list(cols['component/position'].keys())
            gravity_ecs_columns(cols, eids, dt)
            t0 = time.perf_counter()
            gravity_ecs_columns(cols, eids, dt)
            t1 = time.perf_counter()
        else:
            raise ValueError(pattern)
        times.append(t1 - t0)
    return times


def fan_out_overhead(n_systems=24, n_entities=500, work_per_system_us=20):
    """
    Model parallel fan-out overhead.
    Each system does a small O(N) scan (work_per_system_us microseconds).
    Overhead = thread-pool scheduling + future creation + deref barrier.
    """
    # Empirical-ish constants on HotSpot / Clojure future pool
    per_future_us = 25.0      # ~25 us per future create+deref pair
    scheduling_noise_us = 5.0
    times = []
    for k in range(1, n_systems + 1):
        work_us = k * work_per_system_us * n_entities / 1000.0
        overhead_us = k * per_future_us + scheduling_noise_us * math.sqrt(k)
        # parallel speedup up to core count (assume 8 logical cores)
        cores = 8
        effective = work_us / min(k, cores) + overhead_us
        times.append(effective)
    return times


def plot(ns, primitive, ecs_mom, ecs_col, fan_out):
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))

    ax = axes[0]
    ax.plot(ns, [t * 1000 for t in primitive], 'o-', label='primitive arrays (SoA)')
    ax.plot(ns, [t * 1000 for t in ecs_mom], 's-', label='ECS map-of-maps')
    ax.plot(ns, [t * 1000 for t in ecs_col], '^-', label='ECS component columns')
    ax.axhline(16.667, color='r', linestyle='--', label='60 Hz budget (16.67 ms)')
    ax.set_xlabel('Number of bodies N')
    ax.set_ylabel('One tick (ms)')
    ax.set_title('Per-tick gravity+integration cost by storage layout')
    ax.set_xscale('log')
    ax.set_yscale('log')
    ax.legend()
    ax.grid(True, which='both', ls='--', alpha=0.4)

    ax = axes[1]
    k = list(range(1, len(fan_out) + 1))
    ax.plot(k, fan_out, 'o-', label='modeled fan-out wall time')
    ax.axhline(16667, color='r', linestyle='--', label='60 Hz budget (16667 us)')
    ax.set_xlabel('Number of parallel systems')
    ax.set_ylabel('Wall time (us)')
    ax.set_title('Parallel fan-out overhead (N=500, 8 cores)')
    ax.legend()
    ax.grid(True, which='both', ls='--', alpha=0.4)

    plt.tight_layout()
    plt.savefig('docs/research/physics/phase0_tick_overhead.png', dpi=150)
    print('wrote docs/research/physics/phase0_tick_overhead.png')


if __name__ == '__main__':
    ns = [50, 100, 200, 300, 400, 500]
    print('Benchmarking primitive arrays ...')
    primitive = bench('primitive', ns)
    print('Benchmarking ECS map-of-maps ...')
    ecs_mom = bench('ecs-map-of-maps', ns)
    print('Benchmarking ECS component columns ...')
    ecs_col = bench('ecs-columns', ns)

    print('\nMedian tick times (ms):')
    print(f"{'N':>5} {'primitive':>12} {'ecs-mom':>12} {'ecs-cols':>12}")
    for n, tp, tm, tc in zip(ns, primitive, ecs_mom, ecs_col):
        print(f"{n:>5} {tp*1000:12.3f} {tm*1000:12.3f} {tc*1000:12.3f}")

    fan = fan_out_overhead()
    plot(ns, primitive, ecs_mom, ecs_col, fan)
