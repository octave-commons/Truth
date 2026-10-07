#!/usr/bin/env python3
"""One bounded ordinary natural-flight diagnostic with a bounded persistent snapshot client.

Only the detached supervisor owns subprocesses. External stages submit bounded,
allowlisted requests; there is no arbitrary shell, nREPL, world or camera setter.
"""
import argparse
import datetime
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import re
import resource
import shutil
import signal
import socket
import subprocess
import sys
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BASE = "b395c4049718f7ce015ddf25fc0a192d97821373"
CLOJURE = "/usr/local/bin/clojure"
TAP_KEYS = ("r", "Tab")
RELEASE_KEYS = TAP_KEYS
HOLD_KEYS = ("w",)
STARTUP_SECONDS = 180
WORK_SECONDS = 1470
TOTAL_SECONDS = 1500
CLEANUP_SECONDS = 30
CLOSURE_WAIT_SECONDS = 45


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def save(path, value):
    """Atomic current-state/result file, never a historical receipt rewrite."""
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, indent=2) + "\n")
    temp.replace(path)


def identity(pid):
    data = Path(f"/proc/{pid}/stat").read_text()
    fields = data[data.rfind(")") + 2:].split()
    return {"pid": pid, "start": fields[19], "state": fields[0],
            "boot": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
            "cwd": os.readlink(f"/proc/{pid}/cwd"),
            "exe": os.readlink(f"/proc/{pid}/exe")}


def same_process(saved):
    current = identity(saved["pid"])
    assert all(current[k] == saved[k] for k in ("pid", "start", "boot", "cwd")), "Process identity changed"
    assert current["state"] not in ("T", "Z", "X"), "Owned process unavailable/stopped"
    return current


def bounded_time(deadline, maximum=5):
    remaining = deadline - time.monotonic()
    assert remaining > 0, "Shared absolute deadline reached"
    return min(maximum, remaining)


class StopRequested(RuntimeError):
    pass


def verify_sources(deadline=None):
    check = subprocess.run(["git", "diff", "--quiet", BASE, "--", "src", "dev", "resources", "deps.edn"],
                           cwd=ROOT, timeout=bounded_time(deadline) if deadline is not None else 5, check=False)
    assert check.returncode == 0, "Production source differs from pinned base"
    names = subprocess.check_output(["git", "ls-files", "-z", "src", "dev", "resources", "deps.edn"],
                                    cwd=ROOT, timeout=bounded_time(deadline) if deadline is not None else 5).decode().split("\0")
    result = {}
    for name in names:
        if name:
            if deadline is not None:
                bounded_time(deadline)
            result[name] = hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
    if deadline is not None:
        bounded_time(deadline)
    return result


def verify_preparation():
    expected = json.loads((HERE / "preparation-hashes.json").read_text())
    for name, digest in expected.items():
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == digest, "Preparation bytes changed"
    return expected


def file_limit(limit):
    # Per-child file ceiling; the snapshot worker needs the explicit 128 MiB journal cap.
    resource.setrlimit(resource.RLIMIT_FSIZE, (limit, limit))


class Attempt:
    def __init__(self, directory, port, started):
        self.directory, self.port = directory, port
        assert math.isfinite(started) and 0 <= time.monotonic() - started < STARTUP_SECONDS, "Invalid/expired caller startup origin"
        self.started = started
        self.starting = True
        self.startup_deadline = started + STARTUP_SECONDS
        self.deadline = started + WORK_SECONDS
        self.cleanup_deadline = started + TOTAL_SECONDS
        self.children = []
        self.seq = 0
        self.state = {"started_at": utc(), "base": BASE, "cwd": str(ROOT), "port": port,
                      "budget_seconds": TOTAL_SECONDS, "work_budget_seconds": WORK_SECONDS,
                      "startup_budget_seconds": STARTUP_SECONDS, "startup_deadline": self.startup_deadline,
                      "startup_origin": "Caller monotonic instant before preflight; shared by all processes",
                      "supervisor": identity(os.getpid()), "supervisor_monotonic_started": self.started,
                      "stage": "starting"}
        self.env = os.environ.copy()
        option_keys = ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "CLJ_JVM_OPTS")
        self.state["removed_inherited_jvm_option_keys"] = [k for k in option_keys if k in self.env]
        for key in option_keys:
            self.env.pop(key, None)
        # The tools.deps preparation JVM does not receive -J options.
        self.env["CLJ_JVM_OPTS"] = "-Xms64m -Xmx256m"
        self.env["JAVA_CMD"] = str(Path(shutil.which("java")).resolve())
        self.state["java_executable"] = self.env["JAVA_CMD"]
        self.env.pop("DISPLAY", None)
        self.env["TRUTH_DEMO_PORT"] = str(port)
        self.x = self.app = self.snapshot_client = None
        self.snapshot_seq = 0
        self.mouse_pending = False
        self.mouse_release_uncertain = False
        self.key_pending = None
        self.key_release_uncertain = False
        self.frames = self.looks = self.holds = self.aims = 0
        self.target = self.active_aim = self.aim_deadline = None
        self.state.update(frames=0, looks=0, holds=0, aim_invocations=0, aim_target=None, active_aim=None, aim_deadline=None)

    def event(self, kind, **data):
        with (self.directory / "operations.jsonl").open("a") as out:
            out.write(json.dumps({"at": utc(), "elapsed_seconds": time.monotonic() - self.started,
                                  "kind": kind, **data}) + "\n")

    def work_limit(self):
        limit = min(self.deadline, self.startup_deadline) if self.starting else self.deadline
        return min(limit, self.aim_deadline) if self.aim_deadline is not None else limit

    def remaining(self):
        if (self.directory / "STOP").exists():
            raise StopRequested("Owned stop requested")
        result = self.work_limit() - time.monotonic()
        assert result > 0, "Attempt work deadline reached"
        return result

    def spawn(self, command, label, pass_fds=(), file_limit_bytes=32 * 1024 * 1024):
        self.seq += 1
        stem = f"{self.seq:03d}-{label}"
        stdout, stderr = self.directory / (stem + ".stdout"), self.directory / (stem + ".stderr")
        self.event("command", command=command, cwd=str(ROOT), display=self.env.get("DISPLAY"),
                   port=self.port, stdout=stdout.name, stderr=stderr.name, file_limit_bytes=file_limit_bytes)
        with stdout.open("xb") as out, stderr.open("xb") as err:
            child = subprocess.Popen(command, cwd=ROOT, env=self.env, stdin=subprocess.DEVNULL,
                                     stdout=out, stderr=err, start_new_session=True,
                                     pass_fds=pass_fds, preexec_fn=lambda: file_limit(file_limit_bytes))
        self.children.append((child, stem))
        try:
            sample = identity(child.pid)
        except (FileNotFoundError, ProcessLookupError) as error:
            # Fast helpers may finish before /proc/exe can be read. They remain
            # registered Popen children and are reaped/logged by finish().
            sample = {"pid": child.pid, "returncode_at_sample": child.poll(),
                      "proc_sample_unavailable": repr(error)}
        self.event("spawn", label=stem, identity=sample)
        return child, stdout

    def finish(self, child, timeout, label, cleanup=False):
        try:
            available = max(0, self.cleanup_deadline - time.monotonic()) if cleanup else self.remaining()
            result = child.wait(timeout=min(timeout, available))
        except subprocess.TimeoutExpired:
            self.terminate(child, label)
            self.event("timeout", label=label)
            raise
        self.event("exit", label=label, returncode=result)
        assert result == 0, f"{label} exited {result}; preserved output"

    def command(self, command, label, timeout=5, cleanup=False):
        if not cleanup:
            self.remaining()
        child, out = self.spawn(command, label)
        self.finish(child, timeout, label, cleanup)
        return out.read_text()

    def terminate(self, child, label):
        if child.poll() is None:
            # Every child has its own new session; only this Popen-owned group.
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=max(0, min(3, self.cleanup_deadline - time.monotonic())))
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait(timeout=max(0, min(2, self.cleanup_deadline - time.monotonic())))
        self.event("reaped", label=label, pid=child.pid, returncode=child.returncode)

    def guard(self, verify_files=True):
        same_process(self.state["xvfb"])
        current = same_process(self.state["app"])
        assert Path(current["exe"]).name == "java", "Application PID is not Java"
        if verify_files:
            assert verify_sources(self.work_limit()) == self.source_hashes, "Source changed during attempt"
            assert verify_preparation() == self.preparation_hashes, "Preparation changed during attempt"
        if self.snapshot_client:
            assert self.snapshot_client.poll() is None, "Persistent snapshot client exited"
            if "snapshot_client" in self.state:
                client_identity = same_process(self.state["snapshot_client"])
                assert Path(client_identity["exe"]).name == "java", "Snapshot client is not Java"
        self.remaining()

    def start_snapshot_client(self):
        expected = {k: self.state["app"][k] for k in ("pid", "boot", "start", "cwd")}
        expected.update(display=self.state["display"], port=str(self.port))
        edn = "{" + " ".join(":" + k + " " + json.dumps(v) for k, v in expected.items()) + "}"
        self.remaining()
        config = "{:expected " + edn + " :remaining-ms " + str(int((self.deadline - time.monotonic()) * 1000)) + "}\n"
        (self.directory / "snapshot-client-config.edn").write_text(config)
        # Same nREPL 1.0.0 dependency as deps.edn :demo-client, no alias/source edit.
        self.snapshot_client, _ = self.spawn(
            [CLOJURE, "-J-Xms64m", "-J-Xmx256m", "-Sdeps",
             '{:deps {nrepl/nrepl {:mvn/version "1.0.0"}}}', "-M",
             str(HERE / "snapshot_client.clj"), str(self.directory)], "snapshot-client",
            file_limit_bytes=128 * 1024 * 1024)
        until = min(self.work_limit(), time.monotonic() + 35)
        while not (self.directory / "snapshot-client-ready").exists():
            self.remaining()
            assert self.snapshot_client.poll() is None, "Snapshot client startup failed; retain outputs"
            assert time.monotonic() < until, "Snapshot client startup deadline reached"
            time.sleep(0.05)
        assert (self.directory / "snapshot-client-ready").read_text() == "ready\n"
        self.state["snapshot_client"] = identity(self.snapshot_client.pid)
        self.event("snapshot-client-ready", identity=self.state["snapshot_client"])

    def inspect(self, timeout=35, held_until=None):
        assert math.isfinite(timeout) and timeout > 0, "No remaining snapshot budget"
        if held_until is None:
            self.guard()
        else:
            # Source/preparation verification completed before keydown. Never
            # run git/full-file hashing while a key is held. Fixed remote reads
            # still validate app/boot/start/cwd/world/window on every request.
            assert self.key_pending is not None and held_until > time.monotonic()
            self.guard(verify_files=False)
            timeout = min(timeout, held_until - time.monotonic())
            assert timeout > 0, "Hold observation budget exhausted"
        self.snapshot_seq += 1
        assert self.snapshot_seq <= 2048, "Snapshot request cap exceeded; no silent omission"
        request_id = f"{self.snapshot_seq:04d}"
        previous_id = f"{self.snapshot_seq - 1:04d}"
        available = min(timeout, self.remaining())
        timeout_ms = max(1, int(available * 1000))
        request = self.directory / "snapshot-current-request.edn"
        result = self.directory / "snapshot-current.result"
        temp = request.with_suffix(".tmp")
        assert not temp.exists(), "Unfinished local request publication"
        with temp.open("x", encoding="utf-8") as stream:
            stream.write('{:id "' + request_id + '" :timeout-ms ' + str(timeout_ms) + '}\n')
            stream.flush()
        until = min(self.work_limit(), time.monotonic() + available,
                    held_until if held_until is not None else self.deadline)
        temp.replace(request)
        self.event("snapshot-request", id=request_id, timeout_seconds=available,
                   raw_journal="snapshot-messages.edn", summary_journal="snapshot-requests.edn")
        while True:
            self.remaining()
            if result.exists():
                verdict = result.read_text(encoding="utf-8")
                parts = verdict.split()
                assert len(parts) == 2 and parts[0] in (previous_id, request_id), "Unexpected/stale IPC result id"
                if parts[0] == request_id:
                    break
            assert self.snapshot_client.poll() is None, "Snapshot client exited before result"
            if time.monotonic() >= until:
                self.event("snapshot-timeout", id=request_id)
                raise TimeoutError("Snapshot request deadline; no retry or cancellation claim")
            time.sleep(min(0.02, max(0, until - time.monotonic())))
        assert time.monotonic() < until, "Snapshot result arrived after deadline"
        self.event("snapshot-result", id=request_id, verdict=verdict.strip())
        assert verdict == request_id + " ok\n", "Snapshot rejected; preserve raw responses"
        # Serialized ownership: consume this slot before publishing any next request.
        output = (self.directory / "snapshot-current.stdout").read_text(encoding="utf-8")
        self.event("snapshot-consumed", id=request_id, stdout_utf8_bytes=len(output.encode("utf-8")))
        matches = re.findall(r"^TRUTH_APPROACH_ID (\d+) (\d+)$", output, re.M)
        assert len(matches) == 1, "Missing/duplicate identity frame"
        observed = dict(world=int(matches[0][0]), window=int(matches[0][1]))
        assert all(k not in self.state or self.state[k] == v for k, v in observed.items()), "World/window changed"
        self.state.update(observed)
        save(self.directory / "state.json", self.state)
        return output

    @staticmethod
    def input_state(output):
        matches = re.findall(r"^TRUTH_INPUT_STATE (.+)$", output, re.M)
        assert len(matches) == 1, "Missing/duplicate input state"
        values = matches[0].split()
        assert len(values) == 9 and all(values[i] in ("true", "false") for i in (1, 7, 8))
        numbers = [float(values[i]) for i in (3, 4, 5, 6)]
        assert all(math.isfinite(v) for v in numbers), "Nonfinite input readback"
        cursor = re.findall(r"^TRUTH_INPUT_CURSOR (\S+) (\S+)$", output, re.M)
        assert len(cursor) == 1, "Missing/duplicate cursor state"
        point = None if cursor[0] == ("nil", "nil") else [float(v) for v in cursor[0]]
        assert point is None or all(math.isfinite(v) for v in point)
        return dict(zip(("mode", "free", "domain", "displacement", "yaw", "pitch", "sensitivity", "looking", "pick_pending"),
                        (values[0], values[1] == "true", values[2], *numbers,
                         values[7] == "true", values[8] == "true")), cursor=point)

    @staticmethod
    def flight_state(output):
        matches = re.findall(r"^TRUTH_FLIGHT (.+)$", output, re.M)
        assert len(matches) == 1, "Missing/duplicate flight state"
        values = matches[0].split()
        assert len(values) == 13
        tick, sim_time, dt, observer = int(values[0]), float(values[1]), float(values[2]), int(values[3])
        position, velocity = [float(v) for v in values[4:7]], [float(v) for v in values[7:10]]
        thrust = None if values[10:] == ["nil"] * 3 else [float(v) for v in values[10:]]
        assert tick >= 0 and dt > 0 and all(math.isfinite(v) for v in [sim_time, dt, *position, *velocity])
        assert thrust is None or all(math.isfinite(v) for v in thrust)
        return dict(tick=tick, sim_time=sim_time, dt=dt, observer=observer,
                    position=position, velocity=velocity, thrust=thrust)

    @staticmethod
    def expected_thrust(controls, key):
        # Source parity: navigation/input camera-forward, forward×world-up,
        # and thrust-direction normalization. This is an observation oracle,
        # never a world write or alternative movement implementation.
        yaw, pitch = math.radians(controls["yaw"]), math.radians(controls["pitch"])
        forward = [-math.cos(pitch) * math.cos(yaw), -math.cos(pitch) * math.sin(yaw), -math.sin(pitch)]
        right = [forward[1], -forward[0], 0.0]
        right_length = math.hypot(*right)
        assert right_length > 0, "Degenerate camera basis"
        right = [v / right_length for v in right]
        vector = {"w": forward, "s": [-v for v in forward],
                  "d": right, "a": [-v for v in right],
                  "space": [0.0, 0.0, 1.0], "Control_L": [0.0, 0.0, -1.0]}[key]
        norm = math.hypot(*vector)
        return [v / norm for v in vector]

    def target_geometry(self, output):
        assert self.target is not None, "Root must explicitly admit a current natural target through aim-start"
        rows = [line.split() for line in re.findall(r"^TRUTH_TARGET (.+)$", output, re.M)]
        matches = [row for row in rows if row[0] == str(self.target)]
        assert len(matches) == 1, "Explicit target absent/omitted; no auto-retarget"
        row = matches[0]
        assert len(row) == 9 and row[1] in ("planet", "gas-giant") and row[2] == "true", "Target is not in the current production handoff write-set"
        position, velocity = list(map(float, row[3:6])), list(map(float, row[6:9]))
        assert all(math.isfinite(v) for v in position + velocity), "Nonfinite target geometry"
        spark = self.flight_state(output)
        relative = [a - b for a, b in zip(position, spark["position"])]
        relative_velocity = [a - b for a, b in zip(velocity, spark["velocity"])]
        distance = math.hypot(*relative)
        assert math.isfinite(distance) and distance > 0, "Degenerate target distance"
        return dict(target=self.target, tick=spark["tick"], dt=spark["dt"], sim_time=spark["sim_time"],
                    spark_position=spark["position"], target_position=position,
                    spark_velocity=spark["velocity"], target_velocity=velocity,
                    relative_position=relative, relative_velocity=relative_velocity, distance_m=distance)

    def release_movement(self):
        if self.key_pending is not None:
            key = self.key_pending
            self.key_pending = None
            self.key_release_uncertain = True
            same_process(self.state["xvfb"])
            self.command(["xdotool", "keyup", key], "release-movement", 2, cleanup=True)
            self.key_release_uncertain = False
            self.event("movement-key-released", key=key)

    def hold(self, args):
        assert len(args) == 2 and args[0] in HOLD_KEYS
        key, seconds = args[0], float(args[1])
        assert math.isfinite(seconds) and 0 < seconds <= 2, "Hold request must be positive and <=2 seconds"
        output = self.inspect()
        controls, before = self.input_state(output), self.flight_state(output)
        target_before = self.target_geometry(output)
        assert self.holds < 20, "Run-wide hold budget exhausted"
        assert controls["mode"] == "manual" and controls["looking"] and controls["domain"] == "none"
        assert not controls["free"] and not controls["pick_pending"] and before["thrust"] is None
        assert self.key_pending is None and not self.key_release_uncertain
        assert self.remaining() >= seconds + 25, "Hold/readback reserve exhausted"
        expected_thrust = self.expected_thrust(controls, key)
        direction = [v / target_before["distance_m"] for v in target_before["relative_position"]]
        dot = max(-1, min(1, sum(a * b for a, b in zip(expected_thrust, direction))))
        heading_error = math.degrees(math.acos(dot))
        target_before["heading_error_degrees"] = heading_error
        assert heading_error <= 0.5, "Fresh target heading exceeds half a degree; no pulse admitted"
        direction_tolerance = 1e-9
        started = time.monotonic()
        release_at = started + seconds
        during = None
        self.event("hold-start", key=key, requested_seconds=seconds, before=before, controls=controls,
                   expected_thrust=expected_thrust, component_tolerance=direction_tolerance,
                   camera_tolerance_degrees=1e-9)
        self.holds += 1
        self.state["holds"] = self.holds
        save(self.directory / "state.json", self.state)
        self.event("hold-budget", used=self.holds, maximum=20, target_before=target_before)
        self.key_pending = key  # An ambiguous keydown still requires one release attempt.
        try:
            self.command(["xdotool", "keydown", key], "movement-keydown", min(1, seconds))
            while time.monotonic() < release_at:
                left = release_at - time.monotonic()
                if left <= 0.05:
                    break
                observed_output = self.inspect(timeout=min(1, left), held_until=release_at)
                current_controls = self.input_state(observed_output)
                observed = self.flight_state(observed_output)
                assert current_controls["looking"] and current_controls["domain"] == "none"
                assert observed["observer"] == before["observer"]
                assert abs(current_controls["yaw"] - controls["yaw"]) <= 1e-9 and abs(current_controls["pitch"] - controls["pitch"]) <= 1e-9, "Camera changed during hold"
                matched_direction = (observed["thrust"] is not None and
                                     all(abs(a - b) <= direction_tolerance for a, b in zip(observed["thrust"], expected_thrust)))
                self.event("hold-observation", state=observed, controls=current_controls,
                           expected_thrust=expected_thrust, direction_matches=matched_direction)
                assert observed["thrust"] is None or matched_direction, "Published thrust differs from held-key camera direction"
                if observed["tick"] > before["tick"] and observed["thrust"] is not None:
                    during = observed
                    # No further blocking operation before the planned release deadline.
                    time.sleep(max(0, release_at - time.monotonic()))
                    break
                time.sleep(min(0.05, max(0, release_at - time.monotonic())))
        finally:
            self.release_movement()
            released_after = time.monotonic() - started
            self.event("hold-release", key=key, requested_seconds=seconds,
                       release_elapsed_seconds=released_after, exceeds_ten_seconds=released_after > 10)
        assert released_after <= 10, "Observed release latency exceeded ten seconds; no further input"
        assert during is not None, "No actual tick with published thrust observed; gesture not repeated"
        until = min(self.work_limit(), time.monotonic() + 20)
        for attempt in range(5):
            output_after = self.inspect(timeout=min(3, until - time.monotonic()))
            after = self.flight_state(output_after)
            assert after["observer"] == before["observer"]
            matched = after["tick"] > during["tick"] and after["thrust"] is None
            self.event("hold-release-observation", attempt=attempt, state=after, matched=matched)
            if matched:
                target_after = self.target_geometry(output_after)
                self.event("hold-complete", key=key, before=before, during=during, after=after,
                           target_before=target_before, target_after=target_after,
                           relative_chord=[a - b for a, b in zip(target_after["relative_position"], target_before["relative_position"])],
                           observed_tick_delta=after["tick"] - before["tick"],
                           position_delta=[a - b for a, b in zip(after["position"], before["position"])],
                           velocity_delta=[a - b for a, b in zip(after["velocity"], before["velocity"])])
                return
            time.sleep(min(0.1, max(0, until - time.monotonic())))
        raise AssertionError("Published thrust release unconfirmed; no cancellation/momentum erasure claimed")

    def await_input(self, label, predicate):
        # Read-only observations may repeat. The input gesture never repeats.
        until = min(self.work_limit(), time.monotonic() + 20)
        for attempt in range(3):
            left = until - time.monotonic()
            assert left > 0, f"Input observation deadline: {label}"
            output = self.inspect(timeout=min(10, left))
            state = self.input_state(output)
            matched = bool(predicate(state))
            self.event("input-observation", label=label, attempt=attempt, state=state, matched=matched)
            if matched:
                return state
            if attempt < 2:
                time.sleep(min(0.2, max(0, until - time.monotonic())))
        raise AssertionError(f"Input postcondition unconfirmed: {label}; gesture not retried")

    def release_keys(self):
        if self.x and self.x.poll() is None and "xvfb" in self.state:
            same_process(self.state["xvfb"])
            self.command(["xdotool", "keyup", *RELEASE_KEYS], "release-keys", 2, cleanup=True)

    def release_mouse(self):
        if self.mouse_pending:
            # Claim the one release attempt before spawning it. Failure remains
            # explicit; cleanup must not repeat an ambiguously completed release.
            self.mouse_pending = False
            self.mouse_release_uncertain = True
            same_process(self.state["xvfb"])
            self.command(["xdotool", "mouseup", "1"], "release-mouse", 2, cleanup=True)
            self.mouse_release_uncertain = False

    def release(self):
        try:
            self.release_keys()
        finally:
            try:
                self.release_movement()
            finally:
                self.release_mouse()

    def start(self):
        self.remaining()
        self.source_hashes = verify_sources(self.startup_deadline)
        self.preparation_hashes = verify_preparation()
        save(self.directory / "source-hashes.json", self.source_hashes)
        save(self.directory / "preparation-hashes.json", self.preparation_hashes)
        # Refuse an occupied port; later remote PID/boot/start checks catch races.
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", self.port))
        with (self.directory / "display.txt").open("xb") as display_out:
            self.x, _ = self.spawn(["Xvfb", "-displayfd", str(display_out.fileno()), "-screen", "0",
                                   "1280x720x24", "-nolisten", "tcp", "-ac"], "xvfb", (display_out.fileno(),))
        until = min(self.startup_deadline, time.monotonic() + 10)
        while not (self.directory / "display.txt").stat().st_size:
            self.remaining()
            assert self.x.poll() is None and time.monotonic() < until, "Xvfb startup failed/timed out"
            time.sleep(0.1)
        number = (self.directory / "display.txt").read_text().strip()
        assert number.isdecimal(), "Invalid allocated display"
        self.env["DISPLAY"] = ":" + number
        self.state.update(display=self.env["DISPLAY"], xvfb=identity(self.x.pid))
        self.app, log = self.spawn([CLOJURE, "-J-Xms256m", "-J-Xmx2g", "-M:demo", "serve", "nebula"], "runtime")
        self.remaining()
        until = min(self.startup_deadline, time.monotonic() + 90)
        while "Truth demo ready:" not in log.read_text(errors="replace"):
            self.remaining()
            assert self.app.poll() is None and time.monotonic() < until, "Game startup failed/timed out"
            time.sleep(0.25)
        self.remaining()
        self.state["app"] = identity(self.app.pid)
        self.start_snapshot_client()
        self.inspect()
        ids = self.command(["xdotool", "search", "--onlyvisible", "--pid", str(self.app.pid)], "window-id")
        windows = [line for line in ids.splitlines() if line.isdecimal()]
        assert len(windows) == 1, "Expected one visible application window on owned display"
        self.state["x-window"] = windows[0]
        self.command(["xdotool", "windowfocus", "--sync", windows[0]], "focus-owned-window")
        self.remaining()
        self.starting = False
        self.state["stage"] = "ready"
        save(self.directory / "state.json", self.state)
        self.event("ready", state=self.state)

    def action(self, request):
        operation, args = request["operation"], request.get("args", [])
        self.guard()
        if self.active_aim is not None:
            assert time.monotonic() < self.aim_deadline, "Active aim deadline reached"
            assert request.get("aim") == self.active_aim and operation in ("inspect", "look", "aim-end"), "Another command cannot interleave with the active aim; stop remains available"
        else:
            assert request.get("aim") is None, "No matching active aim invocation"
        if operation in ("tap", "look", "click", "hold"):
            assert self.deadline - time.monotonic() >= 40, "Run work reserve exhausted; no gesture admitted"
        if operation == "aim-start":
            assert len(args) == 1 and args[0].isdecimal()
            assert self.aims < 3 and self.remaining() >= 160, "Aim invocation/reserve budget exhausted"
            target = int(args[0])
            assert self.target is None or self.target == target, "Run target is fixed; no retarget"
            if self.target is None:
                assert time.monotonic() - self.started <= 960, "Natural formation/target admission cutoff reached"
            self.target = target
            geometry = self.target_geometry(self.inspect())
            self.aims += 1
            self.active_aim = self.aims
            self.aim_deadline = min(self.deadline, time.monotonic() + 120)
            self.state.update(aim_invocations=self.aims, aim_target=self.target, active_aim=self.active_aim, aim_deadline=self.aim_deadline)
            save(self.directory / "state.json", self.state)
            self.event("aim-admitted", invocation=self.aims, maximum=3, deadline=self.aim_deadline, geometry=geometry)
        elif operation == "aim-end":
            assert self.active_aim is not None and not args, "No active aim to complete"
            self.event("aim-completed", invocation=self.active_aim)
            self.active_aim = self.aim_deadline = None
            self.state.update(active_aim=None, aim_deadline=None)
            save(self.directory / "state.json", self.state)
        elif operation == "inspect":
            self.inspect()
        elif operation == "hold":
            self.hold(args)
        elif operation == "frame":
            assert self.frames < 3, "Run-wide three-PNG budget exhausted"
            self.frames += 1
            self.state["frames"] = self.frames
            save(self.directory / "state.json", self.state)
            filename = f"frame-{request['id']}.png"
            self.command(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-threads", "1",
                          "-f", "x11grab", "-draw_mouse", "0", "-video_size", "1280x720", "-i",
                          self.state["display"] + ".0", "-frames:v", "1", str(self.directory / filename)], "frame", 10)
        elif operation == "tap":
            assert len(args) == 1 and args[0] in TAP_KEYS
            before = self.input_state(self.inspect())
            try:
                self.command(["xdotool", "key", "--delay", "80", args[0]], "tap")
            finally:
                self.release_keys()
            want_free = not before["free"] if args[0] == "Tab" else before["free"]
            want_mode = "manual" if args[0] == "r" else before["mode"]
            self.await_input("tap-" + args[0], lambda a: a["free"] == want_free
                             and a["mode"] == want_mode and a["domain"] == before["domain"]
                             and not a["pick_pending"]
                             and (want_free or before["domain"] != "none" or a["looking"]))
        elif operation == "look":
            assert len(args) == 2
            dx, dy = map(int, args)
            assert max(abs(dx), abs(dy)) <= 100 and (dx or dy), "Small look steps only"
            before = self.input_state(self.inspect())
            assert before["looking"] and not before["pick_pending"], "Actual locked manual look required"
            want_yaw = before["yaw"] + dx * before["sensitivity"]
            want_pitch = max(-89, min(89, before["pitch"] - dy * before["sensitivity"]))
            self.event("look-expectation", requested_pixels=[dx, dy], before=before,
                       expected_yaw=want_yaw, expected_pitch=want_pitch, tolerance_degrees=0.02)
            assert self.looks < 360, "Run-wide look gesture budget exhausted"
            self.looks += 1
            self.state["looks"] = self.looks
            save(self.directory / "state.json", self.state)
            self.event("look-budget", used=self.looks, maximum=360)
            self.command(["xdotool", "mousemove_relative", "--", str(dx), str(dy)], "look")
            self.await_input("look", lambda a: a["looking"] and not a["pick_pending"]
                             and abs(a["yaw"] - want_yaw) <= 0.02
                             and abs(a["pitch"] - want_pitch) <= 0.02)
        elif operation == "click":
            assert len(args) == 1 and args[0] in ("spark", "fine", "cruise", "thrust-down", "thrust-up")
            output = self.inspect()
            before = self.input_state(output)
            assert before["mode"] == "manual" and before["free"] and not before["pick_pending"]
            hits = re.findall(r"^TRUTH_APPROACH_HIT " + args[0] + r" (\d+) (\d+)$", output, re.M)
            assert len(hits) == 1, "Requested menu control not currently visible"
            want_displacement = None
            if args[0] in ("fine", "cruise"):
                preset = re.findall(r"^TRUTH_INPUT_PRESET " + args[0] + r" (\S+)$", output, re.M)
                assert len(preset) == 1, "Missing production preset value"
                want_displacement = float(preset[0])
                assert math.isfinite(want_displacement) and want_displacement > 0
            elif args[0] in ("thrust-down", "thrust-up"):
                knob = re.findall(r"^TRUTH_THRUST_KNOB (\S+) (\S+) (\S+) (\S+)$", output, re.M)
                assert len(knob) == 1, "Missing actual production Thrust knob bounds"
                down, up, low, high = map(float, knob[0])
                assert all(math.isfinite(v) and v > 0 for v in (down, up, low, high)) and low <= high
                factor = down if args[0] == "thrust-down" else up
                want_displacement = max(low, min(high, before["displacement"] * factor))
                self.event("thrust-knob-expectation", action=args[0], factor=factor, bounds=[low, high],
                           before=before["displacement"], expected=want_displacement)
            point = [int(v) for v in hits[0]]
            self.command(["xdotool", "mousemove", *hits[0]], "menu-position")
            self.await_input("menu-position", lambda a: a["mode"] == "manual" and a["free"]
                             and a["domain"] == before["domain"] and not a["pick_pending"]
                             and a["cursor"] is not None
                             and all(abs(x - y) <= 1 for x, y in zip(a["cursor"], point)))
            assert not self.mouse_pending and not self.mouse_release_uncertain
            self.mouse_pending = True  # includes an ambiguously completed press
            try:
                self.command(["xdotool", "mousedown", "1"], "menu-press")
                time.sleep(min(0.08, self.remaining()))
            finally:
                self.release_mouse()
            if args[0] == "spark":
                want_domain = "none" if before["domain"] == "spark" else "spark"
                self.await_input("spark-toggle", lambda a: a["mode"] == "manual" and a["free"]
                                 and a["domain"] == want_domain and not a["pick_pending"])
            else:
                self.await_input("range-" + args[0], lambda a: a["mode"] == "manual" and a["free"]
                                 and a["domain"] == "spark" and not a["pick_pending"]
                                 and a["displacement"] == want_displacement)

        else:
            raise ValueError("Unknown/forbidden operation")

    def run(self):
        outcome = "deadline"
        try:
            self.start()
            while time.monotonic() < self.deadline:
                if (self.directory / "STOP").exists():
                    outcome = "operator-stop"
                    break
                assert self.app.poll() is None and self.x.poll() is None, "Owned native process exited"
                assert self.active_aim is None or time.monotonic() < self.aim_deadline, "Abandoned/expired aim; stop run without unlocking"
                for request_path in sorted((self.directory / "requests").glob("*.json")):
                    request = json.loads(request_path.read_text())
                    result_path = self.directory / "results" / request_path.name
                    if result_path.exists():
                        continue
                    try:
                        self.event("request", request=request)
                        self.action(request)
                        save(result_path, {"ok": True, "finished_at": utc()})
                    except BaseException as error:
                        save(result_path, {"ok": False, "error": repr(error), "finished_at": utc()})
                        raise
                time.sleep(0.1)
        except StopRequested as error:
            outcome = "operator-stop"
            self.event("stop-observed", error=str(error), during_startup=self.starting)
        except BaseException as error:
            outcome = "failed"
            self.event("failure", error=repr(error))
        finally:
            self.cleanup_deadline = min(self.cleanup_deadline, time.monotonic() + CLEANUP_SECONDS)
            cleanup_errors = []
            try:
                self.release()
            except BaseException as error:
                cleanup_errors.append(repr(error))
            if self.snapshot_client and self.snapshot_client.poll() is None:
                try:
                    (self.directory / "snapshot-client-close").touch(exist_ok=False)
                    self.snapshot_client.wait(timeout=max(0, min(2, self.cleanup_deadline - time.monotonic())))
                except subprocess.TimeoutExpired:
                    # Connection close is not remote cancellation. Owned native
                    # teardown follows; never resume/retry an uncertain request.
                    self.event("snapshot-client-close-timeout")
                except BaseException as error:
                    cleanup_errors.append(repr(error))
            # Reap helpers first, then Java, and only then its X server.
            ordered = [(p, label) for p, label in self.children if p not in (self.app, self.x)]
            ordered += [(p, label) for p, label in self.children if p is self.app]
            ordered += [(p, label) for p, label in self.children if p is self.x]
            for child, label in ordered:
                try:
                    self.terminate(child, label)
                except BaseException as error:
                    cleanup_errors.append(f"{label}: {error!r}")
            self.state.update(stage="closed", outcome=outcome, cleanup_errors=cleanup_errors,
                              ended_at=utc(), elapsed_seconds=time.monotonic() - self.started,
                              budget_exceeded=time.monotonic() > self.cleanup_deadline,
                              cleanup_deadline=self.cleanup_deadline,
                              total_budget_exceeded=time.monotonic() > self.started + TOTAL_SECONDS,
                              key_pending=self.key_pending, key_release_uncertain=self.key_release_uncertain,
                              mouse_pending=self.mouse_pending,
                              mouse_release_uncertain=self.mouse_release_uncertain,
                              unreaped_children=[p.pid for p, _ in self.children if p.poll() is None])
            save(self.directory / "state.json", self.state)
            save(self.directory / "closed.json", self.state)


def await_closed(directory, deadline):
    while time.monotonic() < deadline:
        if (directory / "closed.json").exists():
            return json.loads((directory / "closed.json").read_text())
        time.sleep(min(0.1, max(0, deadline - time.monotonic())))
    raise TimeoutError("Owned closure not confirmed by deadline; inspect saved ownership, do not restart")


def clean_closure(state):
    return (state["stage"] == "closed" and not state["cleanup_errors"] and not state["unreaped_children"]
            and not state["budget_exceeded"] and not state["total_budget_exceeded"]
            and state["key_pending"] is None and not state["key_release_uncertain"]
            and not state["mouse_pending"] and not state["mouse_release_uncertain"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("start", "inspect", "frame", "tap", "look", "click", "hold", "aim-start", "aim-end", "stop", "supervise"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("args", nargs="*")
    parser.add_argument("--port", type=int)
    parser.add_argument("--started", type=float)
    parser.add_argument("--aim", type=int)
    options = parser.parse_args()
    directory = options.directory.resolve()
    assert directory.parent == HERE / "runs", "Run must be a new direct child of preparation/runs"
    assert options.operation == "supervise" or options.started is None, "Only the owned supervisor accepts a lease origin"

    def interrupted(signum, _frame):
        raise InterruptedError(f"Process signal {signum}; abort work and retain owned cleanup")

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    if options.operation == "start":
        # One authority begins BEFORE tool/source preflight, not after spawning.
        started = time.monotonic()
        started_at = utc()
        startup_deadline = started + STARTUP_SECONDS
        total_deadline = started + TOTAL_SECONDS
        supervisor = None
        try:
            assert options.port and 1024 < options.port <= 65535 and options.port not in (7888, 7890, 7896)
            for tool in (CLOJURE, "java", "Xvfb", "xdotool", "ffmpeg", "git"):
                bounded_time(startup_deadline)
                assert shutil.which(tool), f"Missing tool: {tool}"
            verify_sources(startup_deadline)
            bounded_time(startup_deadline)
            directory.mkdir(parents=True, exist_ok=False)
            (directory / "requests").mkdir()
            (directory / "results").mkdir()
            with (directory / "supervisor.stdout").open("xb") as out, (directory / "supervisor.stderr").open("xb") as err:
                supervisor = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "supervise", str(directory),
                                               "--port", str(options.port), "--started", repr(started)],
                                              cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out, stderr=err, start_new_session=True)
            save(directory / "supervisor-launch.json", {"pid": supervisor.pid, "at": utc(), "identity": identity(supervisor.pid),
                                                       "lease_started_at": started_at, "lease_started_monotonic": started,
                                                       "startup_deadline": startup_deadline, "total_deadline": total_deadline})
            while time.monotonic() < startup_deadline:
                if (directory / "state.json").exists():
                    state = json.loads((directory / "state.json").read_text())
                    if state["stage"] in ("ready", "closed"):
                        assert state["supervisor_monotonic_started"] == started, "Startup origin changed"
                        print(json.dumps(state, indent=2))
                        return 0 if state["stage"] == "ready" else 1
                assert supervisor.poll() is None, "Supervisor exited; inspect saved logs"
                time.sleep(min(0.1, max(0, startup_deadline - time.monotonic())))
            raise TimeoutError("Shared180s startup deadline reached")
        except BaseException as error:
            if supervisor is not None:
                # Also works before state.json/ready exists. The owning supervisor
                # observes STOP between startup phases and retains finally cleanup.
                (directory / "STOP").touch(exist_ok=True)
                try:
                    closed = await_closed(directory, min(total_deadline, time.monotonic() + CLOSURE_WAIT_SECONDS))
                    assert clean_closure(closed), "Startup cleanup did not close cleanly"
                except BaseException as cleanup_error:
                    error.add_note("Owned startup closure: " + repr(cleanup_error))
            raise
    if options.operation == "supervise":
        assert options.started is not None
        Attempt(directory, options.port, options.started).run()
        return 0
    if options.operation == "stop":
        if (directory / "closed.json").exists():
            closed = json.loads((directory / "closed.json").read_text())
        else:
            launch = json.loads((directory / "supervisor-launch.json").read_text())
            same_process(launch["identity"])
            (directory / "STOP").touch(exist_ok=True)
            closed = await_closed(directory, min(launch["total_deadline"], time.monotonic() + CLOSURE_WAIT_SECONDS))
        print(json.dumps(closed, indent=2))
        return 0 if clean_closure(closed) else 1
    state = json.loads((directory / "state.json").read_text())
    assert state["stage"] == "ready", "Attempt is not ready"
    same_process(state["supervisor"])
    request_may_be_published = False
    try:
        with (directory / "operator.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            request_id = str(time.time_ns()) + "-" + uuid.uuid4().hex[:8]
            request = {"id": request_id, "at": utc(), "operation": options.operation, "args": options.args}
            if options.aim is not None:
                request["aim"] = options.aim
            request_may_be_published = True
            save(directory / "requests" / (request_id + ".json"), request)
            result = directory / "results" / (request_id + ".json")
            until = min(state["supervisor_monotonic_started"] + WORK_SECONDS, time.monotonic() + 60)
            while time.monotonic() < until and not result.exists() and not (directory / "closed.json").exists():
                time.sleep(min(0.1, max(0, until - time.monotonic())))
            assert result.exists(), "No result; do not repeat input, inspect closed state/logs"
            response = json.loads(result.read_text())
            print(json.dumps({"request": request_id, **response}, indent=2))
            assert response["ok"], "Submitted action failed; await owned closure and do not retry"
            return 0
    except BaseException as error:
        # A submitted action can outlive a waiting client. Preserve uncertainty,
        # request the same owned supervisor to stop, and await bounded closure;
        # never retry a gesture or leave an implicit success/unlock behind.
        if request_may_be_published:
            (directory / "STOP").touch(exist_ok=True)
            try:
                closed = await_closed(directory, min(state["supervisor_monotonic_started"] + TOTAL_SECONDS,
                                                      time.monotonic() + CLOSURE_WAIT_SECONDS))
                assert clean_closure(closed), "Action failure cleanup did not close cleanly"
            except BaseException as cleanup_error:
                error.add_note("Owned action closure: " + repr(cleanup_error))
        else:
            error.add_note("No input request was published; existing owner and lease remain authoritative")
        raise



if __name__ == "__main__":
    sys.exit(main())
