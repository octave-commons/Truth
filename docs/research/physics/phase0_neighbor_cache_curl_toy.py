import numpy as np
import time
import sys

# --- toy SPH/MHD-lite parameters ------------------------------------------------
N = 500
np.random.seed(42)

# positions in a box [-L,L]^3
L = 1e15
pos = (np.random.rand(N, 3) - 0.5) * 2.0 * L
mass = np.full(N, 1e28)
radius = np.full(N, 2e14)            # "radius" r_i in the code (h_i = 2*r_i)
density = np.full(N, 1e-15)
pressure = np.full(N, 1e-11)
mu0 = 4.0 * np.pi * 1e-7

# seed a mostly poloidal field along z with a weak ordered toroidal wrap
# Vary field strength log-uniformly so that some clumps are magnetically
# dominated (beta < 1) and some are not, making threshold gating meaningful.
B_strength = np.exp(np.random.uniform(np.log(1e-10), np.log(1e-7), N))
B = np.zeros((N, 3))
B[:, 2] = B_strength                      # poloidal z
B[:, 0] += 0.1 * B_strength * (pos[:, 1] / L)  # small x component
B[:, 1] -= 0.1 * B_strength * (pos[:, 0] / L)  # small y component

# --- cubic-spline kernel gradient (same convention as hydro.clj) ----------------
def cubic_dw_dq(q):
    q = np.asarray(q)
    out = np.zeros_like(q)
    m1 = (q >= 0.0) & (q <= 0.5)
    m2 = (q > 0.5) & (q <= 1.0)
    out[m1] = -12.0 * q[m1] + 18.0 * q[m1]**2
    out[m2] = -6.0 * (1.0 - q[m2])**2
    return out

def kernel_grad(r_vec, h):
    """Returns the 3-vector gradient ∇_i W_ij for separation vector r_vec = r_i - r_j."""
    r_vec = np.asarray(r_vec, dtype=float)
    r2 = np.dot(r_vec, r_vec)
    r = np.sqrt(r2)
    if r == 0.0 or h == 0.0 or r > h:
        return np.zeros(3)
    q = r / h
    dw = cubic_dw_dq(q)
    factor = (8.0 / np.pi) * (1.0 / h**4) * (dw / r)
    return r_vec * factor

# --- naive O(N^2) neighbor lists within h = 2*radius (constant here) ------------
h_pressure = 2.0 * radius[0]  # h_i = 2*r_i
# naive neighbor list
neighbors = []
for i in range(N):
    d = pos - pos[i]
    r2 = np.einsum('ij,ij->i', d, d)
    nbrs = [j for j in range(N) if j != i and r2[j] <= h_pressure**2]
    neighbors.append(nbrs)

# --- helper: plasma beta and Alfvén speed --------------------------------------
def plasma_beta(P, B):
    B2 = np.sum(B**2, axis=1)
    pB = B2 / (2.0 * mu0)
    return np.where(pB > 0, P / pB, np.inf)

def alfven_speed(B, rho):
    B2 = np.sum(B**2, axis=1)
    return np.where(rho > 0, np.sqrt(B2 / (mu0 * rho)), 0.0)

beta = plasma_beta(pressure, B)
va = alfven_speed(B, density)

# --- Baseline: current dual-gradient cache + separate consumers -----------------
def baseline_dual_cache():
    """Mimics the current neighbor cache: precompute pressure-gradient and
    curl-gradient vectors for every neighbor, then have hydro and EM consumers
    loop again over the cached vectors."""
    # 1) build cache
    grad_pressure = [[] for _ in range(N)]
    grad_curl = [[] for _ in range(N)]
    for i in range(N):
        r_i = radius[i]
        for j in neighbors[i]:
            r_vec = pos[i] - pos[j]
            h_p = r_i + radius[j]
            h_c = 0.5 * (r_i + radius[j])
            grad_pressure[i].append(kernel_grad(r_vec, h_p))
            grad_curl[i].append(kernel_grad(r_vec, h_c))

    # 2) hydro pressure-gradient consumer
    a_pressure = np.zeros((N, 3))
    for i in range(N):
        acc = np.zeros(3)
        Pi = pressure[i]
        rhoi = density[i]
        for idx, j in enumerate(neighbors[i]):
            gp = grad_pressure[i][idx]
            Pj = pressure[j]
            rhoj = density[j]
            term = Pi / (rhoi**2) + Pj / (rhoj**2)
            acc -= mass[j] * term * gp
        a_pressure[i] = acc

    # 3) EM curl consumer
    a_lorentz = np.zeros((N, 3))
    curl = np.zeros((N, 3))
    for i in range(N):
        c = np.zeros(3)
        for idx, j in enumerate(neighbors[i]):
            gc = grad_curl[i][idx]
            dB = B[i] - B[j]
            c += (mass[j] / density[j]) * np.cross(dB, gc)
        curl[i] = c
        a_lorentz[i] = np.cross(c, B[i]) / (mu0 * density[i])

    return a_pressure, a_lorentz, curl

# --- Recommended: merged hydro+EM pass, scalar accumulation, threshold gating ---
def recommended_merged(threshold_beta=1.0):
    """Merged pass: compute kernel gradient once per pair (using h_ij = r_i + r_j),
    accumulate pressure gradient and curl with scalar double accumulation, and
    skip the curl where plasma beta > threshold."""
    a_pressure = np.zeros((N, 3))
    a_lorentz = np.zeros((N, 3))
    curl = np.zeros((N, 3))

    for i in range(N):
        r_i = radius[i]
        Pi = pressure[i]
        rhoi = density[i]
        Bi = B[i]
        do_curl = beta[i] < threshold_beta

        ax, ay, az = 0.0, 0.0, 0.0
        cx, cy, cz = 0.0, 0.0, 0.0

        for j in neighbors[i]:
            r_vec = pos[i] - pos[j]
            h = r_i + radius[j]  # same pair smoothing for both terms
            grad = kernel_grad(r_vec, h)
            gx, gy, gz = grad

            # pressure-gradient contribution
            Pj = pressure[j]
            rhoj = density[j]
            term = Pi / (rhoi**2) + Pj / (rhoj**2)
            scale = -mass[j] * term
            ax += gx * scale
            ay += gy * scale
            az += gz * scale

            if do_curl:
                Bj = B[j]
                dBx = Bi[0] - Bj[0]
                dBy = Bi[1] - Bj[1]
                dBz = Bi[2] - Bj[2]
                factor = mass[j] / rhoj
                # (dB × grad) accumulated as scalars
                cx += factor * (dBy * gz - dBz * gy)
                cy += factor * (dBz * gx - dBx * gz)
                cz += factor * (dBx * gy - dBy * gx)

        a_pressure[i] = [ax, ay, az]
        if do_curl:
            curl[i] = [cx, cy, cz]
            a_lorentz[i] = np.cross([cx, cy, cz], Bi) / (mu0 * rhoi)

    return a_pressure, a_lorentz, curl

# --- timing and diagnostics -----------------------------------------------------
runs = 5
t0 = time.perf_counter()
for _ in range(runs):
    a_p_base, a_l_base, curl_base = baseline_dual_cache()
t_base = (time.perf_counter() - t0) / runs

t0 = time.perf_counter()
for _ in range(runs):
    a_p_rec, a_l_rec, curl_rec = recommended_merged(threshold_beta=1.0)
t_rec = (time.perf_counter() - t0) / runs

# error between baseline and recommended pressure/lorentz accelerations
def rel_vec_error(a, b, mask=None):
    if mask is None:
        mask = np.ones(len(a), dtype=bool)
    mag = np.linalg.norm(a, axis=1)
    diff = np.linalg.norm(a - b, axis=1)
    out = np.zeros_like(mag)
    sel = (mag > 0) & mask
    out[sel] = diff[sel] / mag[sel]
    return out

err_pressure = np.median(rel_vec_error(a_p_base, a_p_rec))
active_curl = beta < 1.0
err_lorentz = np.median(rel_vec_error(a_l_base, a_l_rec, active_curl))

# fraction of particles where curl was skipped (beta >= 1)
skipped = np.sum(beta >= 1.0) / N

print(f"N = {N}")
print(f"baseline dual-cache time: {t_base*1e3:.3f} ms")
print(f"recommended merged time: {t_rec*1e3:.3f} ms")
print(f"speedup: {t_base/t_rec:.2f}x")
print(f"median rel. error pressure: {err_pressure:.3e}")
print(f"median rel. error Lorentz: {err_lorentz:.3e}")
print(f"fraction skipped by beta>=1: {skipped:.3f}")

# --- write summary table --------------------------------------------------------
summary = f"""| model | time (ms) | median rel. error pressure | median rel. error Lorentz | skipped fraction |
|---|---|---|---|---|
| baseline dual-cache | {t_base*1e3:.3f} | — | — | 0.0 |
| recommended merged | {t_rec*1e3:.3f} | {err_pressure:.3e} | {err_lorentz:.3e} | {skipped:.3f} |
| speedup | {t_base/t_rec:.2f}x | | | |
"""
with open('docs/research/physics/phase0_neighbor_cache_curl_toy_summary.txt', 'w') as f:
    f.write(summary)
print('wrote docs/research/physics/phase0_neighbor_cache_curl_toy_summary.txt')

# --- generate SVG bar chart -----------------------------------------------------
def make_svg_bar_chart(path, labels, values, colors, title, ylabel):
    width, height = 600, 400
    margin_left, margin_right = 80, 40
    margin_bottom, margin_top = 60, 60
    chart_w = width - margin_left - margin_right
    chart_h = height - margin_bottom - margin_top

    max_v = max(values) if max(values) > 0 else 1.0
    # round up to nice number
    y_max = max_v * 1.2
    n = len(labels)
    bar_w = chart_w / (n * 2)
    gap = bar_w

    lines = []
    lines.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    lines.append(f'<rect width="{width}" height="{height}" fill="white"/>')
    lines.append(f'<text x="{width/2}" y="{margin_top/2}" text-anchor="middle" font-size="16" font-family="sans-serif">{title}</text>')

    # axes
    x0 = margin_left
    y0 = height - margin_bottom
    lines.append(f'<line x1="{x0}" y1="{y0}" x2="{x0+chart_w}" y2="{y0}" stroke="black"/>')
    lines.append(f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{margin_top}" stroke="black"/>')

    # y-axis ticks
    for k in range(5):
        y_val = y_max * (k / 4.0)
        y_pix = y0 - (y_val / y_max) * chart_h
        lines.append(f'<line x1="{x0-5}" y1="{y_pix}" x2="{x0}" y2="{y_pix}" stroke="black"/>')
        lines.append(f'<text x="{x0-10}" y="{y_pix+4}" text-anchor="end" font-size="12" font-family="sans-serif">{y_val:.3f}</text>')

    # y label
    lines.append(f'<text x="{20}" y="{height/2}" text-anchor="middle" font-size="14" font-family="sans-serif" transform="rotate(-90 20 {height/2})">{ylabel}</text>')

    for i, (lab, val, col) in enumerate(zip(labels, values, colors)):
        x = x0 + gap + i * (bar_w + gap)
        h = (val / y_max) * chart_h
        y = y0 - h
        lines.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{col}" stroke="black"/>')
        lines.append(f'<text x="{x + bar_w/2}" y="{y0 + 20}" text-anchor="middle" font-size="12" font-family="sans-serif">{lab}</text>')
        lines.append(f'<text x="{x + bar_w/2}" y="{y - 5}" text-anchor="middle" font-size="12" font-family="sans-serif">{val:.2f}</text>')

    lines.append('</svg>')
    with open(path, 'w') as f:
        f.write('\n'.join(lines))

make_svg_bar_chart(
    'docs/research/physics/phase0_neighbor_cache_curl_toy.svg',
    ['baseline\ndual-cache', 'recommended\nmerged'],
    [t_base*1e3, t_rec*1e3],
    ['#ff7f50', '#87ceeb'],
    f'Cache/curl cost, N={N}',
    'time (ms)'
)
print('wrote docs/research/physics/phase0_neighbor_cache_curl_toy.svg')
