#!/usr/bin/env python3
"""Run one root-supplied frozen attempt-02 operation; never retry or select a target.

Usage: python3 attempt-02-root-client.py FRESH-STEM OPERATION [ARGS...]
Use only after the formation poller has released the sole root command lane.
The frozen clients own identity, input, timeout and cleanup policy. This wrapper
only awaits its direct client and records files; it supplies no extra deadline,
automatic stop, cancellation, game-safety judgment or concurrent-input exclusion.
Frame, stop and aim do not imply a new snapshot and never copy the latest one.
"""
from pathlib import Path
import datetime
import hashlib
import json
import math
import re
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FROZEN = HERE
RUN = FROZEN / "runs" / "attempt-02"
LOGS = HERE / "attempt-02-clients"
PINS = {'runner.py': '1f871920ea12c251e49200680495419052b66f3cf520030a451e2571386e1cc0', 'aim-client.py': '386a1ca5d8075492668bd0f31c3ec9c17723734ad4fbd89df287718e6df2239e', 'cadence_client.py': '3a86998b928b79ba50ee1a0d856d945a654564b72be2f69b576eb3fe4de102d1'}
SNAPSHOT_OPS = {"inspect", "tap", "look", "click", "hold"}


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def main():
    assert len(sys.argv) >= 3, "Expected FRESH-STEM OPERATION [ARGS...]"
    stem, op, *args = sys.argv[1:]
    assert re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", stem), "Unsafe or empty stem"
    assert op in SNAPSHOT_OPS | {"start", "frame", "stop", "aim", "flight"}, "Unsupported operation"
    if op in {"start", "inspect", "frame", "stop"}:
        assert not args, "This operation accepts no arguments"
    elif op == "tap":
        assert len(args) == 1 and args[0] in {"r", "Tab"}, "Unsupported tap"
    elif op == "click":
        assert len(args) == 1 and args[0] in {"spark", "fine", "cruise", "thrust-down", "thrust-up", "damping-down"}, "Unsupported click"
    elif op == "look":
        assert len(args) == 2 and all(re.fullmatch(r"-?[0-9]+", x) for x in args), "Expected two integer pixel deltas"
        dx, dy = map(int, args)
        assert max(abs(dx), abs(dy)) <= 200 and (dx or dy), "Nonzero per-axis look cap is 200 pixels"
    elif op == "hold":
        assert len(args) == 2 and args[0] == "w", "Expected hold w SECONDS"
        seconds = float(args[1])
        assert math.isfinite(seconds) and 0 < seconds <= 2, "Requested hold must be in (0,2] seconds"
    else:
        assert len(args) == 1 and re.fullmatch(r"[0-9]+", args[0]), "Expected one explicit nonnegative target ID"
    LOGS.mkdir(exist_ok=True)
    assert not RUN.exists() if op == "start" else RUN.is_dir(), "Fresh start or existing attempt-02 required"
    for name, digest in PINS.items():
        assert hashlib.sha256((FROZEN / name).read_bytes()).hexdigest() == digest, "Frozen client bytes changed"
    paths = {suffix: LOGS / (stem + suffix) for suffix in
             (".stdout", ".stderr", ".command.jsonl", ".json", "-snapshot.txt")}
    assert not any(p.exists() or p.is_symlink() for p in paths.values()), "Stem already used; do not overwrite or retry"
    argv = ([sys.executable, str(FROZEN / "cadence_client.py"), str(RUN), *args] if op == "flight"
            else [sys.executable, str(FROZEN / "aim-client.py"), str(RUN), *args] if op == "aim"
            else [sys.executable, str(FROZEN / "runner.py"), op, str(RUN), *args])
    if op == "start":
        argv += ["--port", "7898"]
    started = time.monotonic()
    record = {"args": argv, "operation": op, "started_at": utc(), "started_monotonic": started,
              "source_pins": PINS, "snapshot_copied": False, "state": "starting"}
    with paths[".command.jsonl"].open("xb") as journal, paths[".stdout"].open("xb") as out, paths[".stderr"].open("xb") as err:
        def event(data):
            journal.write((json.dumps(data) + "\n").encode("utf-8"))
            journal.flush()
        event(record)
        child = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
        record.update(pid=child.pid, state="running")
        event(record)
        rc = child.wait()
        record.update(returncode=rc, state="reaped", ended_at=utc(), elapsed_seconds=time.monotonic() - started)
        if rc == 0 and op in SNAPSHOT_OPS:
            # Sole root command lane is required: this is the client's latest
            # saved observation, not an independently atomic new world query.
            snapshot = (RUN / "snapshot-current.stdout").read_bytes()
            with paths["-snapshot.txt"].open("xb") as saved:
                saved.write(snapshot)
            record.update(snapshot_copied=True, snapshot_path=str(paths["-snapshot.txt"]),
                          snapshot_bytes=len(snapshot), snapshot_sha256=hashlib.sha256(snapshot).hexdigest(),
                          snapshot_copied_at=utc())
        event(record)
    with paths[".json"].open("xb") as saved:
        saved.write((json.dumps(record, indent=2) + "\n").encode("utf-8"))
    print(json.dumps(record, indent=2), flush=True)
    for suffix in (".stdout", ".stderr"):
        data = paths[suffix].read_bytes()
        print(f"{suffix}: {len(data)} bytes saved; final {min(4096, len(data))} bytes follow", flush=True)
        print(data[-4096:].decode("utf-8", errors="replace"), flush=True)
    if record["snapshot_copied"]:
        prefixes = ("TRUTH_APPROACH_ID ", "TRUTH_INPUT_STATE ", "TRUTH_INPUT_CURSOR ",
                    "TRUTH_DAMPING_STATE ", "TRUTH_FLIGHT ", "TRUTH_COMMITMENT ", "TRUTH_COMMIT_TARGET ")
        print("Saved observation markers only; no derived safety or freshness beyond the sole-operation copy:", flush=True)
        for line in snapshot.decode("utf-8").splitlines():
            if line.startswith(prefixes):
                print(line, flush=True)
    return rc if rc >= 0 else 1


if __name__ == "__main__":
    sys.exit(main())
