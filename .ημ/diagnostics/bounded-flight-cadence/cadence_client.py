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
        self.previous = None
        self.initial = None
        self.max_tps = 0.0
        self.consumed_ticks = 0

    def remaining(self):
        left = self.deadline - self.clock.monotonic()
        assert left > 0, "Original60s command phase expired"
        return left

    def invoke(self, operation):
        self.remaining()
        result = self.client.invoke(operation, self.deadline)
        self.remaining()  # A late valid result supplies no permission to continue.
        return result

    def observation(self, result):
        text = result["text"]
        identity = re.findall(r"^TRUTH_APPROACH_ID (\d+) (\d+)$", text, re.M)
        assert len(identity) == 1, "Missing/duplicate world/window"
        assert tuple(map(int, identity[0])) == (self.client.identity["world"], self.client.identity["window"]), "World/window changed"
        geometry = self.pure.geometry(text, self.target)
        flight = self.pure.flight_state(text)
        controls = self.pure.input_state(text)
        assert controls["displacement"] == EXPECTED_D, "D changed; this comparison requires3.75e13"
        assert geometry["angle_degrees"] <= 0.5, "Fresh heading exceeds0.5 degrees; no automatic aim"
        envelope = math.hypot(*flight["velocity"]) * flight["dt"]
        assert math.isfinite(envelope) and envelope <= EXPECTED_D, "Inherited velocity exceeds D envelope"
        current = {**geometry, "at": self.clock.monotonic(), "observer": flight["observer"],
                   "dt": flight["dt"], "sim_time": flight["sim_time"], "velocity": flight["velocity"],
                   "velocity_tick_envelope_m": envelope,
                   "controls": controls}
        if self.initial is None:
            self.initial = current
        else:
            assert current["observer"] == self.initial["observer"], "Observer changed"
            assert current["dt"] == self.initial["dt"], "Changed dt invalidates this conditional reservation"
            assert controls == self.initial["controls"], "Camera/mode/input configuration changed"
        if self.previous is not None:
            assert current["tick"] > self.previous["tick"], "Fresh observation did not advance a tick"
            elapsed = current["at"] - self.previous["at"]
            assert elapsed > 0 and current["sim_time"] > self.previous["sim_time"], "Nonadvancing observation clock"
            self.max_tps = max(self.max_tps, (current["tick"] - self.previous["tick"]) / elapsed)
        self.previous = current
        self.client.event("observation", **current)
        return current

    def inspect(self):
        return self.observation(self.invoke("inspect"))

    def coast_pause(self):
        assert self.remaining() > 1, "No phase budget for the required released-coast separation"
        self.clock.sleep(1.0)
        self.remaining()

    def screen(self, current):
        assert self.max_tps > 0 and math.isfinite(self.max_tps), "No measured tick-rate interval"
        next_ticks = math.ceil(2 * PULSE_SECONDS * self.max_tps) + 2
        tail = (0.97 / 0.03) * EXPECTED_D  # One initial conditional tail allocation.
        reservation = tail + EXPECTED_D * (self.consumed_ticks + next_ticks)
        assert tail <= 0.1 * current["distance_m"], "Original tail/range screen failed"
        assert reservation <= 0.25 * current["distance_m"], "Non-replenishing residual/pulse reservation exhausted"
        assert self.remaining() >= 25, "Insufficient phase reserve for another bounded hold/readback"
        result = {"next_tick_envelope": next_ticks, "consumed_tick_envelope": self.consumed_ticks,
                  "initial_tail_m": tail, "reserved_m": reservation, "range_m": current["distance_m"],
                  "max_observed_ticks_per_second": self.max_tps}
        self.client.event("screen", **result)
        return result

    def actual_hold(self, result, before, released):
        event = result.get("hold")
        assert isinstance(event, dict) and event["key"] == "w", "Missing actual hold baseline"
        start, end = event["target_before"], event["target_after"]
        assert start["target"] == end["target"] == self.target, "Hold target mismatch"
        assert start["tick"] >= before["tick"] and end["tick"] == released["tick"] > start["tick"], "Hold tick pairing invalid"
        assert event["before"]["observer"] == event["after"]["observer"] == before["observer"], "Hold observer mismatch"
        assert event["before"]["thrust"] is None and event["after"]["thrust"] is None, "Hold release not observed"
        assert event["during"]["thrust"] is not None and start["tick"] < event["during"]["tick"] < end["tick"], "No paired actual driven tick"
        for part in (start, end):
            assert math.isfinite(part["distance_m"]) and part["distance_m"] > 0, "Nonfinite hold range"
            assert len(part["relative_position"]) == 3 and all(math.isfinite(v) for v in part["relative_position"]), "Invalid relative endpoint"
        assert end["distance_m"] == released["distance_m"], "Hold endpoint/current observation mismatch"
        assert math.isfinite(start["heading_error_degrees"]) and start["heading_error_degrees"] <= 0.5, "Actual pre-keydown heading failed"
        assert sum(a * b for a, b in zip(start["relative_position"], end["relative_position"])) > 0, "Sampled relative direction crossed target plane"
        self.consumed_ticks += end["tick"] - start["tick"]
        self.report["consumed_tick_envelope"] = self.consumed_ticks
        return event

    def run(self):
        try:
            self.inspect()
            self.coast_pause()
            self.inspect()
            noncontractions = 0
            for pulse in range(MAX_PULSES):
                before = self.inspect()
                screen = self.screen(before)
                result = self.invoke("hold")
                released = self.observation(result)
                hold = self.actual_hold(result, before, released)
                coast_a = self.inspect()
                self.coast_pause()
                coast_b = self.inspect()
                baseline = hold["target_before"]
                relative_b = [a - b for a, b in zip(coast_b["target_position"], coast_b["spark_position"])]
                assert sum(a * b for a, b in zip(baseline["relative_position"], relative_b)) > 0, "Sampled coast direction crossed target plane"
                delta = coast_b["distance_m"] - baseline["distance_m"]
                noncontractions = noncontractions + 1 if delta >= 0 else 0
                item = {"pulse": pulse + 1, "preinspect": before, "screen": screen,
                        "actual_hold": hold, "released": released,
                        "coast_a": coast_a, "coast_b": coast_b, "range_delta_m": delta,
                        "relative_chord": [a - b for a, b in zip(relative_b, baseline["relative_position"])],
                        "consecutive_noncontractions": noncontractions}
                self.report["intervals"].append(item)
                self.client.event("interval", **item)
                if noncontractions == 2:
                    self.report["outcome"] = "two-noncontractions"
                    break
            else:
                self.report["outcome"] = "three-pulses-completed"
        except self.pure.CommitmentObserved as terminal:
            self.report.update(outcome="commitment-observed", terminal_commitment=terminal.observation)
        except BaseException as error:
            self.report.update(outcome="failed", error=repr(error))
        finally:
            self.report["command_phase_elapsed_seconds"] = self.clock.monotonic() - self.started
            # No further work command is issued after the60s boundary. An
            # already-published action is not synchronously cancelled. STOP and
            # reaping use the separate original supervisor cleanup deadline.
            try:
                self.report["closure"] = self.client.invoke("stop", min(self.client.total_end, self.clock.monotonic() + 65))["closed"]
            except BaseException as error:
                self.report["cleanup_error"] = repr(error)
            self.report["total_elapsed_seconds"] = self.clock.monotonic() - self.started
        return self.report


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
