import numpy as np
import matplotlib.pyplot as plt
import time

# --- toy SPH/MHD-lite parameters ------------------------------------------------
N = 500
np.random.seed(42)

# positions in a box [-L,L]^3
L = 1e15
pos = (np.random.rand(N, 3) - 0.5) * 2.0 * L
mass = np.full(N, 1e28)
radius = np.full(N, 2e14)
density = np.full(N, 1e-15)
mu0 = 4.0 * np.pi * 1e-7

# seed a mostly poloidal field along z with a weak ordered toroidal wrap
B = np.zeros((N, 3))
B[:, 2] = 1e-9                       # poloidal z
B[:, 0] += 1e-10 * (pos[:, 1] / L)  # small x component
B[:, 1] -= 1e-10 * (pos[:, 0] / L)  # small y component

# cubic-spline kernel gradient (same convention as hydro.clj)
def cubic_dw_dq(q):
    q = np.asarray(q)
    out = np.zeros_like(q)
    m1 = (q >= 0.0) & (q <= 0.5)
    m2 = (q > 0.5) & (q <= 1.0)
    out[m1] = -12.0 * q[m1] + 18.0 * q[m1]**2
    out[m2] = -6.0 * (1.0 - q[m2])**2
    return out

def kernel_grad(r_vec, h):
    r_vec = np.asarray(r_vec, dtype=float)
    r = np.linalg.norm(r_vec)
    if r == 0.0 or h == 0.0 or r > h:
        return np.zeros(3)
    q = r / h
    dw = cubic_dw_dq(q)
    factor = (8.0 / np.pi) * (1.0 / h**4) * (dw / r)
    return r_vec * factor

# naive O(N^2) neighbor list within h = 2*radius (constant here)
h = 2.0 * radius[0]
neighbors = []
for i in range(N):
    d = pos - pos[i]
    r2 = np.einsum('ij,ij->i', d, d)
    nbrs = [j for j in range(N) if j != i and r2[j] <= h**2]
    neighbors.append(nbrs)

# --- full curl Lorentz force ----------------------------------------------------
def full_lorentz():
    a = np.zeros((N, 3))
    curl = np.zeros((N, 3))
    for i in range(N):
        c = np.zeros(3)
        for j in neighbors[i]:
            r_ij = pos[i] - pos[j]
            grad = kernel_grad(r_ij, h)
            dB = B[i] - B[j]
            # (B_i - B_j) x grad
            c += (mass[j] / density[j]) * np.cross(dB, grad)
        curl[i] = c
        a[i] = np.cross(c, B[i]) / (mu0 * density[i])
    return a, curl

t0 = time.perf_counter()
a_full, curl_full = full_lorentz()
t_full = time.perf_counter() - t0

# --- magnetic-pressure-only approximation ---------------------------------------
def pressure_only():
    P_B = np.sum(B**2, axis=1) / (2.0 * mu0)
    a = np.zeros((N, 3))
    for i in range(N):
        acc = np.zeros(3)
        for j in neighbors[i]:
            r_ij = pos[i] - pos[j]
            grad = kernel_grad(r_ij, h)
            term = P_B[i] / density[i]**2 + P_B[j] / density[j]**2
            acc -= mass[j] * term * grad
        a[i] = acc
    return a

t0 = time.perf_counter()
a_press = pressure_only()
t_press = time.perf_counter() - t0

# --- diagnostics ----------------------------------------------------------------
mag_full = np.linalg.norm(a_full, axis=1)
mag_press = np.linalg.norm(a_press, axis=1)

# relative vector error for particles with non-zero full acceleration
mask = mag_full > 0.0
rel_err = np.zeros(N)
rel_err[mask] = np.linalg.norm(a_full[mask] - a_press[mask], axis=1) / mag_full[mask]
median_err = np.median(rel_err[mask])
mean_err = np.mean(rel_err[mask])

# angle between full and pressure-only accelerations
dot = np.einsum('ij,ij->i', a_full, a_press)
cos_theta = np.zeros(N)
prod = mag_full * mag_press
nz = prod > 0.0
cos_theta[nz] = dot[nz] / prod[nz]

print(f"N={N}")
print(f"full Lorentz time:      {t_full*1e3:.3f} ms")
print(f"pressure-only time:     {t_press*1e3:.3f} ms")
print(f"speedup:                {t_full/t_press:.2f}x")
print(f"median |a_full|:        {np.median(mag_full):.3e}")
print(f"median |a_press|:       {np.median(mag_press):.3e}")
print(f"median rel. vector err: {median_err:.3f}")
print(f"mean   rel. vector err: {mean_err:.3f}")
print(f"median cos(theta):      {np.median(cos_theta[nz]):.3f}")

# --- plots ----------------------------------------------------------------------
fig, axes = plt.subplots(2, 2, figsize=(11, 9))

ax = axes[0, 0]
ax.scatter(mag_full, mag_press, s=8, alpha=0.5)
lo, hi = min(mag_full.min(), mag_press.min()), max(mag_full.max(), mag_press.max())
ax.plot([lo, hi], [lo, hi], 'r--', lw=1)
ax.set_xscale('log'); ax.set_yscale('log')
ax.set_xlabel('|a_full| (m/s²)'); ax.set_ylabel('|a_pressure-only| (m/s²)')
ax.set_title('Acceleration magnitude comparison')

ax = axes[0, 1]
ax.hist(rel_err[mask], bins=50, color='steelblue', edgecolor='white')
ax.axvline(median_err, color='red', linestyle='--', label=f'median={median_err:.2f}')
ax.set_xlabel('|a_full - a_press| / |a_full|')
ax.set_ylabel('particles')
ax.set_title('Relative vector error')
ax.legend()

ax = axes[1, 0]
ax.hist(cos_theta[nz], bins=50, color='seagreen', edgecolor='white')
ax.set_xlabel('cos(theta) between full and pressure-only a')
ax.set_ylabel('particles')
ax.set_title('Acceleration alignment')

ax = axes[1, 1]
bars = ax.bar(['full curl\nLorentz', 'pressure-only\ngradient'],
              [t_full*1e3, t_press*1e3], color=['coral', 'skyblue'])
ax.set_ylabel('time (ms)')
ax.set_title(f'CPU cost, N={N}')
for bar, t in zip(bars, [t_full*1e3, t_press*1e3]):
    ax.text(bar.get_x() + bar.get_width()/2, bar.get_height(),
            f'{t:.2f} ms', ha='center', va='bottom')

plt.suptitle('MHD-lite Lorentz vs. magnetic-pressure-only approximation')
plt.tight_layout()
plt.savefig('docs/research/physics/mhd_lorentz_toy.png', dpi=150)
print('saved docs/research/physics/mhd_lorentz_toy.png')

# save a tiny table for the notebook
with open('docs/research/physics/mhd_lorentz_toy_summary.txt', 'w') as f:
    f.write('| model | time (ms) | median |a| (m/s²) | mean rel. error |\n')
    f.write('|---|---|---|---|\n')
    f.write(f'| full curl Lorentz | {t_full*1e3:.3f} | {np.median(mag_full):.3e} | — |\n')
    f.write(f'| pressure-only | {t_press*1e3:.3f} | {np.median(mag_press):.3e} | {mean_err:.3f} |\n')
    f.write(f'| speedup | {t_full/t_press:.2f}x | | |\n')
print('saved docs/research/physics/mhd_lorentz_toy_summary.txt')
