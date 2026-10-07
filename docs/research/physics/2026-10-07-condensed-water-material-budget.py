"""Exact rational stoichiometry toy; no production imports or empirical fit.
Run from any cwd with Python 3. Creates only adjacent same-prefix JSON/PNG.
"""
from fractions import Fraction as F
from pathlib import Path
import hashlib
import json
import platform

PREFIX = Path(__file__).with_suffix("")
MH, MO = F("1.008"), F("15.999")  # kg/kmol, rounded element masses
BASE = {"H": F("2.016"), "O": F("31.998"), "C": F("12.011"),
        "Si": F("28.085"), "He": F("25.89")}  # exactly 100 kg; constructed, not solar


def allocate(stock, solid, gas, extent_fraction, ice_fraction):
    """Partition an admitted elemental kg budget; retain unassigned residual."""
    if not 0 <= extent_fraction <= 1 or not 0 <= ice_fraction <= 1:
        raise ValueError("fraction outside [0,1]")
    keys = set(stock) | set(solid) | set(gas)
    if any(x < 0 for cells in (stock, solid, gas) for x in cells.values()):
        raise ValueError("negative stock")
    residual = {e: stock.get(e, F(0)) - solid.get(e, F(0)) - gas.get(e, F(0)) for e in keys}
    if any(v < 0 for v in residual.values()):
        raise ValueError("reservation exceeds element stock")
    q = extent_fraction * min(residual.get("H", F(0)) / (2 * MH), residual.get("O", F(0)) / MO)
    water = {"H": 2 * MH * q, "O": MO * q}
    water_solid = {e: ice_fraction * v for e, v in water.items()}
    water_vapor = {e: (1 - ice_fraction) * v for e, v in water.items()}
    after = {e: residual[e] - water.get(e, F(0)) for e in keys}
    compartments = [solid, gas, water_solid, water_vapor, after]
    balance = {e: sum(c.get(e, F(0)) for c in compartments) - stock.get(e, F(0)) for e in keys}
    assert all(v == 0 for v in balance.values())
    assert all(v >= 0 for c in compartments for v in c.values())
    return {"extent_kmol": q, "water_kg": sum(water.values()),
            "ice_kg": sum(water_solid.values()), "vapor_kg": sum(water_vapor.values()),
            "water_solid_elements_kg": water_solid, "water_vapor_elements_kg": water_vapor,
            "unassigned_elements_kg": after, "balance_error_kg": balance}


def encoded(value):
    if isinstance(value, F):
        return {"exact": str(value), "display": float(value)}
    if isinstance(value, dict):
        return {k: encoded(v) for k, v in sorted(value.items())}
    if isinstance(value, list):
        return [encoded(v) for v in value]
    return value


def main():
    co = {"C": BASE["C"], "O": MO}  # one kmol CO, explicit toy reservation
    silica = {"O": F(8), "Si": F(8) / (2 * MO) * BASE["Si"]}
    cases = [
        ("cold_capacity", BASE, {}, {}, F(1), F(1)),
        ("CO_and_silica_compete", BASE, silica, co, F(1), F(1)),
        ("partial_ice", BASE, silica, co, F(1), F(1, 2)),
        ("warm_vapor", BASE, silica, co, F(1), F(0)),
        ("no_H", {**BASE, "H": F(0)}, {}, {}, F(1), F(1)),
        ("all_O_reserved", BASE, {}, {"O": BASE["O"]}, F(1), F(1)),
        ("no_chemical_conversion", BASE, {}, {}, F(0), F(1)),
        ("over_reserved_reject", BASE, {}, {"O": BASE["O"] + 1}, F(1), F(1)),
    ]
    observations = []
    for name, stock, solid, gas, eta, ice in cases:
        row = {"case": name, "input": {"stock_kg": stock, "reserved_solid_kg": solid,
               "reserved_gas_kg": gas, "extent_fraction": eta, "ice_fraction": ice}}
        try:
            row["output"] = allocate(stock, solid, gas, eta, ice)
            row["outcome"] = "accepted_arithmetic"
        except ValueError as error:
            row["outcome"] = "rejected"
            row["reason"] = str(error)
        observations.append(row)
    sweep = []
    for i in range(21):
        fraction = F(i, 20)
        result = allocate(BASE, {}, {"O": fraction * BASE["O"]}, F(1), F(1))
        sweep.append({"reserved_O_fraction": fraction, "water_kg": result["water_kg"]})
    result = {"scope": "Exact rational constructed-budget experiment; not a planetary observation, empirical calibration or production test.",
              "python": platform.python_version(), "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "atomic_masses_kg_per_kmol": {"H": MH, "O": MO}, "cases": observations, "oxygen_sweep": sweep,
              "conservation": "All seven admitted cases have exactly zero element residual and nonnegative compartments. One over-reservation rejects.",
              "limitations": "Reservations/extent/phase fractions are explicit inputs. No pressure law, hydrate kinetics, global disk depletion, biological availability or performance claim."}
    output = PREFIX.with_suffix(".json")
    with output.open("x") as f:
        json.dump(encoded(result), f, ensure_ascii=False, indent=2); f.write("\n")
    print(json.dumps({"cases": [{"case": r["case"], "outcome": r["outcome"],
                                 "water_kg": float(r.get("output", {}).get("water_kg", 0)),
                                 "ice_kg": float(r.get("output", {}).get("ice_kg", 0))} for r in observations]}, indent=2))
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(figsize=(7.2, 4.2))
    ax.plot([float(r["reserved_O_fraction"]) for r in sweep], [float(r["water_kg"]) for r in sweep], marker="o", color="#126e82")
    ax.set(xlabel="Fraction of oxygen reserved elsewhere (toy input)", ylabel="Maximum allocated H₂O mass (kg)",
           title="Constructed 100 kg inventory: oxygen competition limits water")
    ax.grid(alpha=.25)
    fig.text(.01, .01, "H=2.016 kg, O=31.998 kg; extent=1, ice=1. Accounting illustration, not calibration.", fontsize=8)
    fig.tight_layout(rect=(0,.04,1,1))
    fig.savefig(PREFIX.with_suffix(".png"), dpi=150)
    plt.close(fig)


if __name__ == "__main__":
    main()
