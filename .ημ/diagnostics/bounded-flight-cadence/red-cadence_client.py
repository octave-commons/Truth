#!/usr/bin/env python3
"""Finite, root-released cadence measurement through the unchanged frozen CLI.

Usage: cadence_client.py RUN_DIRECTORY EXPLICIT_TARGET
Never selects, aims, changes D, retries a gesture, or starts a native service.
Root must give this process exclusive command ownership until closure.
"""
import argparse
import ast
import datetime
import fcntl
import hashlib
import json
import math
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
from types import SimpleNamespace

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FROZEN = HERE.parent / "bounded-look-200"
BASE = "b395c4049718f7ce015ddf25fc0a192d97821373"
PINS = {"runner.py": "f16f555a45da3a1dd45a53d69cbdb35ec0343eda1b6fa7d6d5d7fb58cf9a5e7a",
        "aim-client.py": "54f45febf3fd5290d207ca79dd245221a45060e73bfdf84387bc113f38d66b10"}
EXPECTED_D = 3.75e13
PHASE_SECONDS = 60.0
PULSE_SECONDS = 2.0
MAX_PULSES = 3
IDENTITY_KEYS = ("supervisor", "app", "xvfb", "display", "port", "world", "window", "x-window", "snapshot_client")


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def read_json(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    assert isinstance(value, dict), "Expected object: " + str(path)
    return value


def verify_pins():
    for name, digest in PINS.items():
        assert hashlib.sha256((FROZEN / name).read_bytes()).hexdigest() == digest, "Frozen source changed: " + name


def load_pure():
    """Compile only named, pinned pure definitions; never import a native driver."""
    verify_pins()
    tree = ast.parse((FROZEN / "runner.py").read_text())
    names = {"CommitmentObserved", "commitment_frame", "target_frame", "clean_closure"}
    nodes = [n for n in tree.body if isinstance(n, (ast.ClassDef, ast.FunctionDef)) and n.name in names]
    attempt = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == "Attempt")
    methods = {"input_state", "flight_state"}
    for node in attempt.body:
        if isinstance(node, ast.FunctionDef) and node.name in methods:
            node.decorator_list = []
            nodes.append(node)
    assert {n.name for n in nodes} == names | methods, "Pure extraction contract changed"
    ns = {"math": math, "re": re}
    exec(compile(ast.fix_missing_locations(ast.Module(body=nodes, type_ignores=[])), "<pinned-pure-runner>", "exec"), ns)
    pure = SimpleNamespace(**{n.name: ns[n.name] for n in nodes}, LOOK_PIXEL_CAP=200)
    aim = ast.parse((FROZEN / "aim-client.py").read_text())
    nodes = [n for n in aim.body if isinstance(n, ast.FunctionDef) and n.name in {"one_line", "geometry"}]
    assert {n.name for n in nodes} == {"one_line", "geometry"}, "Pure aim extraction changed"
    ns = {"math": math, "re": re, "protocol": pure}
    exec(compile(ast.fix_missing_locations(ast.Module(body=nodes, type_ignores=[])), "<pinned-pure-aim>", "exec"), ns)
    pure.geometry = ns["geometry"]
    return pure


def validate_reply(response, saved):
    assert response.get("ok") is True and isinstance(response.get("request"), str), "Unsuccessful or unidentified action"
    assert saved.get("ok") is True and all(response.get(k) == v for k, v in saved.items()), "Saved action/result mismatch"


class Client:
    """One sequential child at a time; only frozen inspect/hold/stop commands."""
    def __init__(self, run, target, pure):
        self.run = run.resolve()
        assert self.run.parent == FROZEN / "runs", "Run must belong to the frozen profile"
        verify_pins()
        state = read_json(self.run / "state.json")
        assert state["base"] == BASE and state["cwd"] == str(ROOT), "Unexpected source/checkout"
        assert state["stage"] == "ready" and state["active_aim"] is None, "Run is not ready outside an aim"
        assert state["aim_target"] == target, "Explicit target is not the already selected target"
        admission = state["successful_admission"]
        assert admission and admission["target"] == target and admission["elapsed_seconds"] < 960, "No original pre960 admission"
        assert state["holds"] + MAX_PULSES <= 20, "Insufficient original hold budget"
        self.identity = {k: state[k] for k in IDENTITY_KEYS}
        self.work_end = state["supervisor_monotonic_started"] + state["work_budget_seconds"]
        self.total_end = state["supervisor_monotonic_started"] + state["budget_seconds"]
        assert self.work_end - time.monotonic() >= PHASE_SECONDS + 40, "Original work/input reserve insufficient"
        self.pure = pure
        self.logs = self.run / "bounded-cadence-01"
        self.logs.mkdir(exist_ok=False)
        self.seq = 0
        self.children = []
        self.stop_sent = False

    def event(self, kind, **data):
        with (self.logs / "controller.jsonl").open("a", encoding="utf-8") as out:
            out.write(json.dumps({"kind": kind, "at": utc(), "monotonic": time.monotonic(), **data}) + "\n")
            out.flush()

    def guard(self):
        verify_pins()
        state = read_json(self.run / "state.json")
        assert state["stage"] == "ready" and state["active_aim"] is None, "Run closed or another aim active"
        assert {k: state[k] for k in self.identity} == self.identity, "Owned identities changed"
        assert time.monotonic() < self.work_end - 40, "Original work reserve exhausted"

    def invoke(self, operation, deadline):
        assert operation in ("inspect", "hold", "stop")
        cleanup = operation == "stop"
        verify_pins()
        if cleanup:
            assert not self.stop_sent, "Do not repeat STOP"
            self.stop_sent = True
        else:
            self.guard()
        self.seq += 1
        stem = f"{self.seq:03d}-{operation}"
        argv = [sys.executable, str(FROZEN / "runner.py"), operation, str(self.run)]
        if operation == "hold":
            argv += ["w", "2"]
        limit = min(deadline, self.total_end)
        timeout = min(65 if cleanup else 25, limit - time.monotonic())
        assert timeout > 0, "No command/cleanup budget remains"
        offset = (self.run / "operations.jsonl").stat().st_size
        self.event("command", argv=argv, timeout_seconds=timeout, operation=operation)
        child = None
        with (self.logs / (stem + ".stdout")).open("xb") as out, (self.logs / (stem + ".stderr")).open("xb") as err:
            try:
                child = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
                self.children.append(child)
                code = child.wait(timeout=timeout)
            finally:
                if child is not None:
                    # Only our CLI child is signalled. Its frozen handler requests
                    # supervisor STOP; remote work is not claimed cancelled.
                    if child.poll() is None:
                        child.terminate()
                        try:
                            child.wait(timeout=max(0.01, min(2, self.total_end - time.monotonic())))
                        except subprocess.TimeoutExpired:
                            child.kill()
                            child.wait(timeout=max(0.01, min(2, self.total_end - time.monotonic())))
                    self.event("reaped", pid=child.pid, returncode=child.returncode, reaped=child.poll() is not None)
        assert code == 0, "Frozen client failed; uncertain gesture is never repeated"
        assert time.monotonic() < limit, "Command completed after its deadline"
        response = read_json(self.logs / (stem + ".stdout"))
        if cleanup:
            assert self.pure.clean_closure(response), "Owned closure not clean"
            return {"closed": response}
        validate_reply(response, read_json(self.run / "results" / (response["request"] + ".json")))
        if response.get("outcome") == "commitment-observed":
            raise self.pure.CommitmentObserved(response["terminal_commitment"])
        raw = (self.run / "snapshot-current.stdout").read_bytes()
        with (self.logs / (stem + "-snapshot.txt")).open("xb") as out:
            out.write(raw)
        with (self.run / "operations.jsonl").open("rb") as source:
            source.seek(offset)
            span = source.read()
        with (self.logs / (stem + "-operations.jsonl")).open("xb") as out:
            out.write(span)
        events = [json.loads(line) for line in span.splitlines()]
        holds = [e for e in events if e["kind"] == "hold-complete"]
        if operation == "hold":
            assert len(holds) == 1, "Missing/ambiguous actual hold baseline"
        else:
            assert not holds, "Unexpected interleaved hold"
        self.event("action-accepted", operation=operation, response=response,
                   snapshot_sha256=hashlib.sha256(raw).hexdigest(), snapshot_bytes=len(raw),
                   operations_offset=offset, operations_bytes=len(span), operations_sha256=hashlib.sha256(span).hexdigest())
        return {"text": raw.decode("utf-8"), "hold": holds[0] if holds else None}


class Cadence:
    def __init__(self, client, pure, target, clock=time):
        self.client, self.pure, self.target, self.clock = client, pure, target, clock
        self.started = clock.monotonic()
        self.deadline = min(self.started + PHASE_SECONDS, client.work_end - 40)
        self.report = {"target": target, "D": EXPECTED_D, "phase_seconds": PHASE_SECONDS,
                       "maximum_pulses": MAX_PULSES, "requested_hold_seconds": PULSE_SECONDS,
                       "intervals": [], "outcome": "not-started"}

    def run(self):
        raise NotImplementedError("RED: finite cadence, screens and unconditional closure not implemented")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("target", type=int)
    args = parser.parse_args()
    assert args.target >= 0, "Explicit nonnegative target required"
    pure = load_pure()
    run = args.run.resolve()
    assert run.parent == FROZEN / "runs", "Unowned run path"
    def interrupted(signum, _frame):
        raise InterruptedError(f"Cadence interrupted by {signum}; no input retry")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    # This lock excludes duplicate cadence helpers; root's exclusive command
    # ownership remains necessary because ordinary runner clients do not use it.
    with (run / "cadence-controller.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        client = Client(run, args.target, pure)
        result = Cadence(client, pure, args.target).run()
        result["unreaped_client_pids"] = [c.pid for c in client.children if c.poll() is None]
        with (client.logs / "result.json").open("x", encoding="utf-8") as out:
            json.dump(result, out, indent=2)
            out.write("\n")
        print(json.dumps(result, indent=2))
        return 0 if result["outcome"] in ("three-pulses-completed", "two-noncontractions", "commitment-observed") and "cleanup_error" not in result and not result["unreaped_client_pids"] else 1


if __name__ == "__main__":
    sys.exit(main())
