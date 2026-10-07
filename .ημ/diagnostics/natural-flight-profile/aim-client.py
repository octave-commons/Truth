#!/usr/bin/env python3
"""Finite orientation-only diagnostic for one explicitly chosen natural target.

Uses only the frozen runner's aim admission, inspect/look and stop operations. It never starts a
world, translates the Spark, selects/follows an entity, or predicts interception.
Runs only inside this separately reviewed bounded natural-flight profile.
"""
import argparse
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

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
RUNNER = HERE / "runner.py"
RUNNER_SHA256 = "54cff29903228e3a028b73cc23424f41e2d963a0989182c4c0ca023ca57866a3"
BASE = "b395c4049718f7ce015ddf25fc0a192d97821373"
MAX_STEPS = 180
MAX_SECONDS = 120
TOLERANCE_DEGREES = 0.5


def read_json(path):
    result = json.loads(path.read_text())
    assert isinstance(result, dict)
    return result


def one_line(text, name):
    values = re.findall(r"^" + name + r" (.+)$", text, re.M)
    assert len(values) == 1, "Missing/duplicate " + name
    return values[0].split()


def geometry(text, target):
    controls = one_line(text, "TRUTH_INPUT_STATE")
    assert len(controls) == 9 and controls[0:3] == ["manual", "false", "none"]
    assert controls[7:] == ["true", "false"], "Locked manual look with no pending pick required"
    cursor = one_line(text, "TRUTH_INPUT_CURSOR")
    assert len(cursor) == 2 and all(math.isfinite(float(v)) for v in cursor), "Initialize cursor through menu first"
    yaw, pitch, sensitivity = map(float, controls[4:7])
    assert all(math.isfinite(v) for v in (yaw, pitch, sensitivity)) and 0 < sensitivity <= 1
    flight = one_line(text, "TRUTH_FLIGHT")
    assert len(flight) == 13 and flight[10:] == ["nil"] * 3, "Aiming requires released movement"
    position = list(map(float, flight[4:7]))
    candidates = [line.split() for line in re.findall(r"^TRUTH_TARGET (.+)$", text, re.M)]
    matches = [v for v in candidates if v[0] == str(target)]
    assert len(matches) == 1, "Explicit target missing/omitted/duplicated; no auto-retarget"
    body = matches[0]
    assert len(body) == 9 and body[1] in ("planet", "gas-giant") and body[2] == "true", "Target not currently production-eligible"
    destination = list(map(float, body[3:6]))
    assert all(math.isfinite(v) for v in position + destination + list(map(float, body[6:9])))
    delta = [a - b for a, b in zip(destination, position)]
    distance = math.hypot(*delta)
    assert math.isfinite(distance) and distance > 0, "Degenerate/nonfinite target direction"
    direction = [v / distance for v in delta]
    # Invert the existing z-up camera-forward law, not an alternate flight law.
    desired_yaw = math.degrees(math.atan2(-direction[1], -direction[0]))
    desired_pitch = -math.degrees(math.asin(max(-1, min(1, direction[2]))))
    assert -89 <= desired_pitch <= 89, "Target direction outside actual pitch range"
    yaw_error = (desired_yaw - yaw + 180) % 360 - 180
    pitch_error = desired_pitch - pitch
    yr, pr = math.radians(yaw), math.radians(pitch)
    forward = [-math.cos(pr) * math.cos(yr), -math.cos(pr) * math.sin(yr), -math.sin(pr)]
    dot = max(-1, min(1, sum(a * b for a, b in zip(forward, direction))))
    angle = math.degrees(math.acos(dot))
    dx = max(-100, min(100, round(yaw_error / sensitivity)))
    dy = max(-100, min(100, round(-pitch_error / sensitivity)))
    return {"target": target, "tick": int(flight[0]), "spark_position": position,
            "target_position": destination, "distance_m": distance, "angle_degrees": angle,
            "yaw": yaw, "pitch": pitch, "sensitivity": sensitivity,
            "desired_yaw": desired_yaw, "desired_pitch": desired_pitch,
            "requested_pixels": [dx, dy]}


class Aim:
    def __init__(self, directory, target):
        self.run = directory.resolve()
        assert self.run.parent == HERE / "runs" and target >= 0
        assert hashlib.sha256(RUNNER.read_bytes()).hexdigest() == RUNNER_SHA256
        self.state = read_json(self.run / "state.json")
        assert self.state["stage"] == "ready" and self.state["base"] == BASE and self.state["cwd"] == str(ROOT)
        assert "supervisor_monotonic_started" in self.state, "Missing owned monotonic lease"
        self.pin = {k: self.state[k] for k in ("supervisor", "app", "xvfb", "display", "port", "world", "window", "x-window", "snapshot_client")}
        self.started = time.monotonic()
        self.deadline = self.started + MAX_SECONDS
        self.target = target
        assert self.state["active_aim"] is None, "Another aim already owns the diagnostic input lane"
        assert self.state["aim_target"] in (None, target), "Run target is fixed"
        self.invocation = self.state["aim_invocations"] + 1
        assert self.invocation <= 3, "Three-aim run budget exhausted"
        remaining = self.state["supervisor_monotonic_started"] + self.state["work_budget_seconds"] - time.monotonic()
        assert remaining >= 160, "Aim120s plus40s work reserve required before admission"
        self.admitted = False
        self.logs = self.run / (f"aim-{self.invocation:02d}-" + str(target))
        self.logs.mkdir(exist_ok=False)
        self.count = 0
        self.clients = []
        self.report = {"target": target, "invocation": self.invocation, "max_steps": MAX_STEPS, "max_work_seconds": MAX_SECONDS, "max_failure_cleanup_seconds": 65,
                       "tolerance_degrees": TOLERANCE_DEGREES, "source": BASE,
                       "runner_sha256": RUNNER_SHA256, "initial_identity": self.pin,
                       "scope": "orientation diagnostic only; no thrust, follow, capture or interception", "steps": []}

    def event(self, kind, **data):
        with (self.logs / "controller.jsonl").open("a") as out:
            out.write(json.dumps({"kind": kind, "at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                  "elapsed_seconds": time.monotonic() - self.started, **data}) + "\n")

    def guard(self):
        assert hashlib.sha256(RUNNER.read_bytes()).hexdigest() == RUNNER_SHA256
        state = read_json(self.run / "state.json")
        assert state["stage"] == "ready" and {k: state[k] for k in self.pin} == self.pin
        # Same-host monotonic origin is data published by the owned supervisor;
        # wall-clock timestamps are evidence only, never the lease authority.
        assert "supervisor_monotonic_started" in state, "Missing exact monotonic lease origin"
        remaining = state["supervisor_monotonic_started"] + state["work_budget_seconds"] - time.monotonic()
        assert remaining >= 40, "Input reserve exhausted"
        assert time.monotonic() < self.deadline, "Aim deadline reached"

    def invoke(self, operation, *args, cleanup=False):
        assert hashlib.sha256(RUNNER.read_bytes()).hexdigest() == RUNNER_SHA256, "Runner bytes changed"
        if not cleanup:
            self.guard()
        self.count += 1
        stem = f"{self.count:03d}-{operation}"
        argv = [sys.executable, str(RUNNER), operation, str(self.run), *args]
        if self.admitted and operation != "stop":
            argv += ["--aim", str(self.invocation)]
        lease_end = self.state["supervisor_monotonic_started"] + self.state["budget_seconds"]
        remaining = lease_end - time.monotonic()
        assert remaining > 0, "Supervisor cleanup lease exhausted"
        timeout = min(65, remaining) if cleanup else max(0.01, min(25, remaining, self.deadline - time.monotonic()))
        stdout, stderr = self.logs / (stem + ".stdout"), self.logs / (stem + ".stderr")
        self.event("command", argv=argv, timeout_seconds=timeout)
        with stdout.open("xb") as out, stderr.open("xb") as err:
            child = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
            self.clients.append(child)
            try:
                code = child.wait(timeout=timeout)
            finally:
                if child.poll() is None:
                    child.kill()
                    child.wait(timeout=2)
                self.event("reaped", pid=child.pid, returncode=child.returncode)
        assert code == 0, "Runner client failed; gesture never repeated"
        response = read_json(stdout)
        if operation != "stop":
            assert response.get("ok") is True and isinstance(response.get("request"), str)
            saved = read_json(self.run / "results" / (response["request"] + ".json"))
            assert saved.get("ok") is True and all(response.get(k) == v for k, v in saved.items())
        return response

    def execute(self):
        try:
            self.invoke("aim-start", str(self.target))
            current = read_json(self.run / "state.json")
            assert current["active_aim"] == current["aim_invocations"] == self.invocation, "Aim admission mismatch"
            self.admitted = True
            for step in range(MAX_STEPS + 1):
                self.invoke("inspect")
                state = geometry((self.run / "snapshot-current.stdout").read_text(encoding="utf-8"), self.target)
                self.report["steps"].append(state)
                self.event("geometry", step=step, **state)
                if state["angle_degrees"] <= TOLERANCE_DEGREES:
                    self.invoke("aim-end")
                    self.admitted = False
                    self.report["outcome"] = "aimed-at-one-published-observation"
                    break
                assert step < MAX_STEPS, "Aim step cap reached"
                dx, dy = state["requested_pixels"]
                assert dx or dy, "Pixel quantization cannot reduce remaining angle"
                self.invoke("look", str(dx), str(dy))
            else:
                raise AssertionError("No bounded aiming outcome")
        except BaseException as error:
            self.report.update(outcome="failed", error=repr(error))
            self.event("failed", error=repr(error))
            try:
                if read_json(self.run / "state.json")["stage"] == "ready":
                    self.invoke("stop", cleanup=True)
            except BaseException as cleanup_error:
                self.report["stop_error"] = repr(cleanup_error)
        finally:
            self.report.update(elapsed_seconds=time.monotonic() - self.started,
                               unreaped_client_pids=[p.pid for p in self.clients if p.poll() is None])
            with (self.logs / "result.json").open("x") as out:
                json.dump(self.report, out, indent=2)
                out.write("\n")
        print(json.dumps(self.report, indent=2))
        return 0 if self.report["outcome"] == "aimed-at-one-published-observation" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("target", type=int)
    args = parser.parse_args()
    directory = args.directory.resolve()
    assert directory.parent == HERE / "runs", "Only a prepared owned run directory is accepted"

    def interrupted(signum, _frame):
        raise InterruptedError(f"Aim controller signal {signum}; abort and request owned cleanup")

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        with (directory / "aim-controller.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return Aim(directory, args.target).execute()
    except BaseException as error:
        # Constructor/lock failures happen before execute's cleanup boundary.
        # Stop only the verified frozen runner's own recorded run; never invoke
        # changed code or operate an unverified directory as a fallback.
        report = {"outcome": "pre-admission-failed", "error": repr(error)}
        child = None
        try:
            assert hashlib.sha256(RUNNER.read_bytes()).hexdigest() == RUNNER_SHA256, "Cannot use changed runner for cleanup"
            state = read_json(directory / "state.json")
            assert state["base"] == BASE and state["cwd"] == str(ROOT), "Unverified run; no cleanup authority"
            remaining = state["supervisor_monotonic_started"] + state["budget_seconds"] - time.monotonic()
            assert remaining > 0, "Original cleanup lease exhausted"
            argv = [sys.executable, str(RUNNER), "stop", str(directory)]
            child = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            report["cleanup_client"] = {"pid": child.pid, "argv": argv}
            out = err = b""
            try:
                out, err = child.communicate(timeout=min(45, remaining))
            except BaseException:
                if child.poll() is None:
                    child.kill()
                out, err = child.communicate(timeout=2)
                raise
            finally:
                report["cleanup_client"].update(returncode=child.returncode, stdout=out.decode("utf-8"), stderr=err.decode("utf-8"), reaped=child.poll() is not None)
            assert child.returncode == 0, "Pre-admission cleanup failed"
        except BaseException as cleanup_error:
            report["cleanup_error"] = repr(cleanup_error)
            if child is not None:
                report["cleanup_client"]["returncode"] = child.returncode
                report["cleanup_client"]["reaped"] = child.poll() is not None
        print(json.dumps(report, indent=2))
        return 1


if __name__ == "__main__":
    sys.exit(main())
