"""Run the authorized capture/BEFORE adapter with source and load provenance."""
import datetime
import hashlib
import json
import pathlib
import subprocess
import time

ROOT = pathlib.Path.cwd()
BASE = ROOT / ".ημ/diagnostics/warp-lifecycle"
OUT = BASE / "before"
SOURCES = [
    "src/domain/intervention.clj", "src/domain/ecs/tick.clj",
    "src/domain/integrator.clj", "src/domain/integrator/kinematics.clj",
    "src/domain/physics/cache/soa.clj", "src/domain/stellar/merge.clj",
    "src/domain/ecs/registry.clj", "test/domain/warp_lifecycle_test.clj",
    "bench/gates_of_truth/bench.clj", "bench/gates_of_truth/bench/phase0.clj",
    ".ημ/diagnostics/warp-lifecycle/evidence/warp.clj",
]


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def hashes():
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in SOURCES}


def write_new(path, data):
    with path.open("x") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def run(action, label):
    deps = ('{:paths ["src" "resources" "test" "bench" '
            '".ημ/diagnostics/warp-lifecycle"] '
            ':deps {criterium/criterium {:mvn/version "0.4.6"}}}')
    cmd = ["clojure", "-J-Xms256m", "-J-Xmx2g", "-Sdeps", deps,
           "-M", "-m", "evidence.warp", action, str(BASE / "fixtures.edn"), str(OUT)]
    before = hashes()
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    diff = subprocess.check_output(["git", "diff", "--", "src", "bench", "deps.edn"], text=True)
    assert head == "e434a8384e9e87342b0a1012b6b87b33acbb3a46" and not diff
    command = {"command": cmd, "cwd": str(ROOT), "head": head, "source_diff": diff,
               "started_at": now(), "source_sha256": before,
               "boot_id": pathlib.Path("/proc/sys/kernel/random/boot_id").read_text().strip()}
    with (OUT / (label + ".log")).open("x") as log, (OUT / (label + "-load.jsonl")).open("x") as load:
        proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT)
        command["pid"] = proc.pid
        write_new(OUT / (label + "-command.json"), command)
        while proc.poll() is None:
            row = {"time": now(), "loadavg": pathlib.Path("/proc/loadavg").read_text().strip(),
                   "uptime": pathlib.Path("/proc/uptime").read_text().strip(),
                   "aggregate_cpu": pathlib.Path("/proc/stat").read_text().splitlines()[0],
                   "processes": subprocess.check_output(
                       ["ps", "-eo", "pid,ppid,comm,stat,pcpu,rss"], text=True)}
            load.write(json.dumps(row) + "\n")
            load.flush()
            time.sleep(5)
        rc = proc.wait()
    after = hashes()
    write_new(OUT / (label + "-result.json"),
              {"exit_code": rc, "completed_at": now(), "pid": proc.pid, "reaped": True,
               "source_sha256": after, "source_unchanged": before == after,
               "log_sha256": hashlib.sha256((OUT / (label + ".log")).read_bytes()).hexdigest()})
    print(label, "exit", rc, "PID", proc.pid, "reaped", flush=True)
    assert before == after, "Source changed while executing"
    if rc:
        raise SystemExit(rc)


run("capture", "capture-attempt2")
run("before", "benchmark")
