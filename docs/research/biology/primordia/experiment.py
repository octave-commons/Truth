#!/usr/bin/env python3
"""Plan/run a bounded CPU Primordia pilot, preserving failures as missing evidence.

Python 3.11+; standard library only. This does not implement a simulator or infer
sustained replication from upstream's resource-free sandbox assay.
"""
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time
import tomllib
from datetime import datetime, timezone

SHA = "1c93703ae33b0016d48ca59d22a5703835bfe1e6"


def config(arm, epochs, seed, upkeep=1.0, copy_cost=2):
    """Return explicit overrides; upstream writes the entire resolved config."""
    if arm == "high-upkeep":
        upkeep = 8.0
    no_energy = arm == "no-energy"
    sample = 10 if arm == "smoke" else 50
    return f'''preset = "primordia-2d-sun"
seed = {seed}
epochs = {epochs}
[world]
size = [51, 51]
init_occupancy = 0.5
[individual]
interaction = "hybrid"
l_init = 64
l_max = 128
step_budget = 512
[energy]
enabled = true
mode = "normal"
init_e = {0 if no_energy else 600}
init_f = {0 if no_energy else 500}
absorb_alpha = {0.0 if no_energy else 0.05}
[energy.upkeep]
base = {upkeep}
per_byte = 0.02
[energy.sun]
enabled = {str(not no_energy).lower()}
[energy.vents]
enabled = false
[chemistry]
enabled = {str(not no_energy).lower()}
[matter]
enabled = true
pool_l = 8
pool_d = 8
pool_a = 24
[costs]
CPY = {copy_cost}
[telemetry]
hash_every = 10
snapshot_every = {epochs}
metrics_every = {sample}
opcode_every = {sample}
chirality_every = {sample}
entropy_every = {sample}
entropy_samples = 2601
assay_every = {sample}
census_every = {sample}
ecology_every = {sample}
cluster_every = {sample}
[gpu]
exec = false
'''


def plan(stage):
    """Predeclare controls and matched seed arms, with no adaptive tuning."""
    jobs = [
        dict(id="presets", kind="presets", arm="instrumentation", argv=["{bin}", "presets"]),
        dict(id="seeded-golden", kind="test", arm="seeded", argv=["cargo", "test", "--locked", "--release", "-p", "primordia-core", "--test", "golden", "t3_minimal_replicator_paper", "--", "--exact"]),
        dict(id="seeded-assay", kind="assay", arm="seeded", argv=["{bin}", "assay", "12 13 15 c2", "--config", "paper-0d"], expected=True),
        dict(id="null-assay", kind="assay", arm="negative", argv=["{bin}", "assay", "00 00", "--config", "paper-0d"], expected=False),
    ]
    arms = [("smoke", 11, 20, 1.0, 2)]
    if stage == "pilot":
        arms += [(a, s, 1000, 1.0, 2) for a in ("baseline", "high-upkeep", "no-energy") for s in (2, 3, 4, 5)]
    elif stage == "sweep":
        arms += [(f"upkeep-{u}-cpy-{c}", s, 5000, u, c) for u in (1.0, 4.0, 8.0) for c in (2, 8, 32) for s in (2, 3, 4, 5)]
    for a, s, e, u, c in arms:
        ident = f"{a}-s{s}"
        jobs.append(dict(id=ident, kind="run", arm=a, seed=s, epochs=e, copy_cost=c,
                         config=config(a, e, s, u, c),
                         argv=["{bin}", "run", f"{{out}}/configs/{ident}.toml", "--cpu", "--headless", "--out", f"{{out}}/runs/{ident}", "--progress", "0"]))
    return jobs


def validate_config(resolved, requested):
    """Reject inactive costs and every override upstream failed to preserve."""
    if not (resolved["energy"]["enabled"] and resolved["matter"]["enabled"]):
        raise ValueError("resource constraints are off")
    if resolved["individual"]["interaction"] == "pair_concat":
        raise ValueError("pair_concat bypasses energy and matter")
    def check(actual, expected, prefix=""):
        for k, v in expected.items():
            if isinstance(v, dict):
                check(actual[k], v, prefix + k + ".")
            elif actual.get(k) != v:
                raise ValueError(f"resolved override mismatch: {prefix}{k}")
    check(resolved, requested)
    if resolved["energy"]["upkeep"]["base"] <= 0 or resolved["costs"]["CPY"] <= 0:
        raise ValueError("cost must be positive")


def replay_valid(run, log):
    """A successful empty replay is not determinism evidence."""
    path = run / "hashes.csv"
    if not path.exists():
        return False
    with path.open() as handle:
        rows = list(csv.DictReader(handle))
    m = re.search(r"replay OK: (\d+) hashes identical", log)
    return bool(rows and m and int(m[1]) == len(rows) and all(int(r["epoch"]) > 0 for r in rows))


def wilson(k, n):
    """Two-sided 95% Wilson interval; missing sample is not a zero rate."""
    if n == 0:
        return None
    z = 1.959963984540054
    den = 1 + z*z/n
    center = (k/n + z*z/(2*n)) / den
    half = z * math.sqrt((k/n)*(1-k/n)/n + z*z/(4*n*n)) / den
    return [max(0.0, center-half), min(1.0, center+half)]


def save(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def command(argv, cwd, log, timeout):
    """Bound wall time and terminate descendants on timeout; preserve the log."""
    import signal
    start = time.monotonic()
    with log.open("w") as f:
        try:
            p = subprocess.Popen(argv, cwd=cwd, stdout=f, stderr=subprocess.STDOUT, start_new_session=True,
                                 env={**os.environ, "RAYON_NUM_THREADS": "2"})
        except OSError as exc:
            f.write(str(exc) + "\n")
            return dict(status="blocked", seconds=time.monotonic()-start, returncode=None)
        try:
            rc = p.wait(timeout=timeout)
            status = "completed" if rc == 0 else "failed"
        except subprocess.TimeoutExpired:
            os.killpg(p.pid, signal.SIGKILL)
            rc = p.wait()
            status = "timeout"
    return dict(status=status, seconds=time.monotonic()-start, returncode=rc)


def output_hashes(root):
    """Hash exact raw artifacts, including snapshots; no scientific interpretation."""
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob("*")) if p.is_file()}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("mode", choices=["plan", "run"])
    ap.add_argument("--source", type=Path)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--stage", choices=["smoke", "pilot", "sweep"], default="pilot")
    ap.add_argument("--budget-seconds", type=int, default=900)
    ap.add_argument("--job-seconds", type=int, default=180)
    args = ap.parse_args()
    if args.budget_seconds <= 0 or args.job_seconds <= 0:
        ap.error("budgets must be positive")
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)  # never overwrite a campaign
    (out / "configs").mkdir()
    (out / "logs").mkdir()
    jobs = plan(args.stage)
    for job in jobs:
        if "config" in job:
            (out / "configs" / (job["id"] + ".toml")).write_text(job["config"])
        job.update(status="planned", validated=False, classification="unknown", observed_replicator=None)
    save(out / "plan.json", dict(upstream_sha=SHA, stage=args.stage, jobs=jobs,
         budget_seconds=args.budget_seconds, job_seconds=args.job_seconds))
    host = dict(utc=datetime.now(timezone.utc).isoformat(), platform=platform.platform(),
                cpu=platform.processor(), logical_cpus=os.cpu_count(), python=platform.python_version(),
                harness_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                cargo=shutil.which("cargo"), rustc=shutil.which("rustc"),
                dri_devices=[str(p) for p in Path("/dev/dri").glob("*")], backend="CPU requested; no GPU parity test")
    save(out / "host.json", host)
    if args.mode == "plan":
        save(out / "records.json", jobs)
        return
    start = time.monotonic()
    deadline = start + args.budget_seconds
    source = args.source.resolve() if args.source else None
    reasons = []
    if not source or not (source / ".git").exists():
        reasons.append("--source must be a clean git checkout at the pinned SHA")
    if not host["cargo"] or not host["rustc"]:
        reasons.append("cargo/rustc unavailable on PATH")
    if not reasons:
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
        dirty = subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=source, text=True)
        if head != SHA or dirty:
            reasons.append("upstream checkout must match pinned SHA with no tracked changes")
    if reasons:
        for job in jobs:
            job.update(status="blocked", reason="; ".join(reasons), seconds=0, started=False)
        save(out / "records.json", jobs)
        save(out / "result.json", dict(classification="unknown", completed_simulations=0,
             observed_replicators=None, frequency=None, confidence_interval=None, reasons=reasons))
        print("BLOCKED: " + "; ".join(reasons))
        return 2
    for tool in ("cargo", "rustc"):
        command([tool, "--version"], source, out / "logs" / f"{tool}-version.txt",
                max(0.01, min(10, deadline-time.monotonic())))
    build = command(["cargo", "build", "--locked", "--release", "-p", "primordia-cli"], source,
                    out / "logs/build.txt", max(0.01, min(args.job_seconds, deadline-time.monotonic())))
    save(out / "build.json", build)
    executable = str(source / "target/release/primordia")
    gate = build["status"] == "completed"
    for job in jobs:
        remaining = deadline - time.monotonic()
        if not gate or remaining <= 0:
            job.update(status="not-executed", reason="prior gate failed" if not gate else "campaign wall budget exhausted", started=False)
            continue
        argv = [v.replace("{bin}", executable).replace("{out}", str(out)) for v in job["argv"]]
        log = out / "logs" / (job["id"] + ".txt")
        job.update(command=argv, started=True)
        job.update(command(argv, source, log, min(args.job_seconds, remaining)))
        if job["status"] == "completed":
            try:
                text = log.read_text()
                if job["kind"] == "presets":
                    job["validated"] = "primordia-2d-sun" in text and "paper-0d" in text
                elif job["kind"] == "assay":
                    found = re.search(r"replicator:\s*(true|false)", text)
                    job["validated"] = bool(found and (found[1] == "true") == job["expected"])
                    job["classification"] = "executable-copying" if job["validated"] and job["expected"] else "unknown"
                elif job["kind"] == "test":
                    job["validated"] = "1 passed; 0 failed" in text
                else:
                    run = out / "runs" / job["id"]
                    resolved = tomllib.loads((run / "config.toml").read_text())
                    validate_config(resolved, tomllib.loads(job["config"]))
                    summary = json.loads((run / "summary.json").read_text())
                    manifest = json.loads((run / "manifest.json").read_text())
                    events = (run / "events.jsonl").read_text().strip()
                    required = [run / "history.json", run / "hashes.csv"]
                    if events or summary["epoch"] != job["epochs"] or summary["ledger_violations"] != 0:
                        raise ValueError("events, incomplete epoch window, or ledger violations")
                    if not all(p.exists() for p in required) or not list((run / "snapshots").glob("*.psnap")):
                        raise ValueError("missing trajectories/checkpoints/snapshots")
                    job.update(summary=summary, manifest=manifest,
                               raw_run=str(run), artifact_sha256=output_hashes(run), validated=True,
                               sandbox_first_detection=summary.get("first_replicator"),
                               observed_replicator=None)  # sandbox detection cannot establish replication
                    if job["arm"] == "smoke":
                        rlog = out / "logs/replay.txt"
                        remaining = deadline - time.monotonic()
                        if remaining <= 0:
                            raise ValueError("budget exhausted before smoke replay")
                        replay = command([executable, "replay", str(run), "--cpu"], source, rlog,
                                         min(args.job_seconds, remaining))
                        job["replay"] = replay
                        job["validated"] = replay["status"] == "completed" and replay_valid(run, rlog.read_text())
            except (OSError, KeyError, ValueError) as exc:
                job.update(validated=False, validation_error=str(exc))
            if not job["validated"]:
                gate = False
        else:
            gate = False
        save(out / "records.json", jobs)
    save(out / "records.json", jobs)
    save(out / "result.json", dict(classification="unknown", elapsed_seconds=time.monotonic()-start,
         completed_simulations=sum(j["kind"] == "run" and j["status"] == "completed" for j in jobs),
         artifact_validated_simulations=sum(j["kind"] == "run" and j["validated"] for j in jobs),
         observed_replicators=None, frequency=None, confidence_interval=None,
         reason="Review assay trajectories and identity-linked resource-constrained descent before classifying replication."))
    return 0 if all(j["validated"] for j in jobs) else 2


if __name__ == "__main__":
    raise SystemExit(main())
