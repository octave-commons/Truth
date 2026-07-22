"""Toy model / sanity check for the Phase 3 atmosphere-retention classifier.

Computes, for six representative bodies, the escape velocity v_esc, the
per-species Jeans escape parameter lambda = G*M*m/(k_B*T*R), and the
ratio v_esc/v_th(rms) = sqrt(lambda) used by the recommended classifier
(docs/research/atmosphere/planetary-atmosphere-retention-classifier.md).

Run: python3 atmosphere_retention_toy.py
Produces: atmosphere_retention_toy.png (v_esc vs T_eff bucket diagram)
"""
import math
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

G = 6.674e-11
kB = 1.381e-23
amu = 1.6605e-27
sigma = 5.670e-8
Lsun = 3.828e26
AU = 1.496e11

SPECIES = {
    "H2": 2.016 * amu,
    "He": 4.0026 * amu,
    "H2O": 18.015 * amu,
    "N2": 28.014 * amu,
    "CO2": 44.01 * amu,
}

# H/He gated at ratio > 6 (lambda > 36); H2O/CO2/N2 at ratio > 3 (lambda > 9).
# See docs/research/atmosphere/planetary-atmosphere-retention-classifier.md
# section 3 for the physical justification of the asymmetric threshold.
SPECIES_THRESHOLD = {"H2": 6.0, "He": 6.0, "H2O": 3.0, "N2": 3.0, "CO2": 3.0}

H2HE_MU = 1 / (0.75 / 2.016 + 0.25 / 4.0026) * amu  # solar-mix mean molecular mass

# (material-class, thermal-band) — Phase 1 outputs this is downstream of.
BODIES = {
    "Earth": dict(M=5.972e24, R=6.371e6, T=255.0,
                  material="rocky", band="temperate"),
    "Mars": dict(M=6.417e23, R=3.3895e6, T=210.0,
                 material="rocky", band="cold"),
    "Titan": dict(M=1.3452e23, R=2.575e6, T=88.0,
                  material="icy", band="frozen"),
    "Jupiter": dict(M=1.898e27, R=6.9911e7, T=110.0,
                    material="gaseous", band="frozen"),
    "Hot super-Earth": dict(M=5 * 5.972e24, R=1.6 * 6.371e6, a=0.02 * AU, L=Lsun,
                             material="rocky", band="hot"),
    "Cold icy body (Pluto-like)": dict(M=1.303e22, R=1.188e6, T=40.0,
                                        material="icy", band="frozen"),
}


def teq(L, a, albedo=0.0):
    return ((L * (1 - albedo)) / (16 * math.pi * sigma * a ** 2)) ** 0.25


def v_esc(M, R):
    return math.sqrt(2 * G * M / R)


def species_ratio(M, R, T, m):
    """v_esc / v_th(rms); this equals sqrt(Jeans lambda)."""
    v_th_rms = math.sqrt(3 * kB * T / m)
    return v_esc(M, R) / v_th_rms


def candidate_species(material, band):
    """Which volatiles are chemically plausible atmospheric constituents,
    from material-class (what the body is made of) and thermal-band
    (whether H2O is condensed out as ice below ~250K). See section 3."""
    if material == "gaseous":
        return {"H2", "He"}
    base = {"N2", "CO2"}
    if band in ("temperate", "warm", "hot"):
        base.add("H2O")
    return base


def representative_mu(material, band):
    """Single dominant-species mean molecular mass for the overall
    atmosphere-class bucket, per parent-spec section 4 ('mu is the mean
    molecular mass of the dominant atmospheric species')."""
    if material == "gaseous":
        return H2HE_MU
    if band == "hot":
        return SPECIES["CO2"]
    return SPECIES["N2"]


def overall_bucket(ratio):
    if ratio < 3:
        return "none"
    if ratio < 6:
        return "thin"
    if ratio < 10:
        return "substantial"
    return "thick"


rows = []
for name, b in BODIES.items():
    T = b.get("T") or teq(b["L"], b["a"])
    M, R = b["M"], b["R"]
    material, band = b["material"], b["band"]
    ve = v_esc(M, R)
    candidates = candidate_species(material, band)
    retained = {sp for sp in candidates
                if species_ratio(M, R, T, SPECIES[sp]) > SPECIES_THRESHOLD[sp]}
    mu = representative_mu(material, band)
    ratio_overall = species_ratio(M, R, T, mu)
    bucket = overall_bucket(ratio_overall)
    rows.append((name, M, R, T, ve, retained, ratio_overall, bucket))
    ret_str = ",".join(sorted(retained)) if retained else "{}"
    print(f"{name:28s} [{material:8s}/{band:9s}] T={T:7.1f}K v_esc={ve/1000:7.2f}km/s "
          f"retained={ret_str:20} ratio={ratio_overall:6.2f} -> {bucket}")

fig, ax = plt.subplots(figsize=(7, 5.5))
colors = {"none": "#888888", "thin": "#4c9be8", "substantial": "#e8a23a", "thick": "#c0392b"}
for name, M, R, T, ve, ret, ratio, bucket in rows:
    ax.scatter(T, ve / 1000, s=90, color=colors[bucket], edgecolor="k", zorder=3)
    ax.annotate(name, (T, ve / 1000), textcoords="offset points", xytext=(6, 4), fontsize=8)

ax.set_xscale("log")
ax.set_yscale("log")
ax.set_xlabel("Equilibrium temperature T_eff (K)")
ax.set_ylabel("Escape velocity v_esc (km/s)")
ax.set_title("Phase 3 atmosphere-retention toy model:\nsix bodies classified by dominant-species v_esc/v_th ratio")
handles = [plt.Line2D([0], [0], marker="o", color="w", markerfacecolor=c, markeredgecolor="k",
                       label=k, markersize=9) for k, c in colors.items()]
ax.legend(handles=handles, title="atmosphere-class", loc="upper left")
ax.grid(True, which="both", alpha=0.25)
fig.tight_layout()
fig.savefig("atmosphere_retention_toy.png", dpi=150)
print("\nWrote atmosphere_retention_toy.png")
