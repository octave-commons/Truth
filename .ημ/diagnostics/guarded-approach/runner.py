#!/usr/bin/env python3
"""One disposable input-only smoke with a bounded persistent snapshot client.

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


def verify_sources():
    check = subprocess.run(["git", "diff", "--quiet", BASE, "--", "src", "dev", "resources", "deps.edn"],
                           cwd=ROOT, timeout=5, check=False)
    assert check.returncode == 0, "Production source differs from pinned base"
    names = subprocess.check_output(["git", "ls-files", "-z", "src", "dev", "resources", "deps.edn"],
                                    cwd=ROOT, timeout=5).decode().split("\0")
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in names if p}


def verify_preparation():
    expected = json.loads((HERE / "preparation-hashes.json").read_text())
    for name, digest in expected.items():
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == digest, "Preparation bytes changed"
    return expected


def file_limit():
    # Each child output file is limited to 32 MiB, including its stderr/media.
    resource.setrlimit(resource.RLIMIT_FSIZE, (32 * 1024 * 1024, 32 * 1024 * 1024))


class Attempt:
    def __init__(self, directory, port):
        self.directory, self.port = directory, port
        self.started = time.monotonic()
        self.deadline = self.started + 270  # reserve 30 seconds for owned cleanup
        self.cleanup_deadline = self.started + 300
        self.children = []
        self.seq = 0
        self.state = {"started_at": utc(), "base": BASE, "cwd": str(ROOT), "port": port,
                      "budget_seconds": 300, "work_budget_seconds": 270,
                      "supervisor": identity(os.getpid()), "stage": "starting"}
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
        self.frames = 0

    def event(self, kind, **data):
        with (self.directory / "operations.jsonl").open("a") as out:
            out.write(json.dumps({"at": utc(), "elapsed_seconds": time.monotonic() - self.started,
                                  "kind": kind, **data}) + "\n")

    def remaining(self):
        result = self.deadline - time.monotonic()
        assert result > 0, "Attempt work deadline reached"
        return result

    def spawn(self, command, label, pass_fds=()):
        self.seq += 1
        stem = f"{self.seq:03d}-{label}"
        stdout, stderr = self.directory / (stem + ".stdout"), self.directory / (stem + ".stderr")
        self.event("command", command=command, cwd=str(ROOT), display=self.env.get("DISPLAY"),
                   port=self.port, stdout=stdout.name, stderr=stderr.name)
        with stdout.open("xb") as out, stderr.open("xb") as err:
            child = subprocess.Popen(command, cwd=ROOT, env=self.env, stdin=subprocess.DEVNULL,
                                     stdout=out, stderr=err, start_new_session=True,
                                     pass_fds=pass_fds, preexec_fn=file_limit)
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

    def guard(self):
        same_process(self.state["xvfb"])
        current = same_process(self.state["app"])
        assert Path(current["exe"]).name == "java", "Application PID is not Java"
        assert verify_sources() == self.source_hashes, "Source changed during attempt"
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
        (self.directory / "snapshot-requests").mkdir()
        (self.directory / "snapshot-responses").mkdir()
        config = "{:expected " + edn + " :remaining-ms " + str(int(self.remaining() * 1000)) + "}\n"
        (self.directory / "snapshot-client-config.edn").write_text(config)
        # Same nREPL 1.0.0 dependency as deps.edn :demo-client, no alias/source edit.
        self.snapshot_client, _ = self.spawn(
            [CLOJURE, "-J-Xms64m", "-J-Xmx256m", "-Sdeps",
             '{:deps {nrepl/nrepl {:mvn/version "1.0.0"}}}', "-M",
             str(HERE / "snapshot_client.clj"), str(self.directory)], "snapshot-client")
        until = min(self.deadline, time.monotonic() + 35)
        while not (self.directory / "snapshot-client-ready").exists():
            assert self.snapshot_client.poll() is None, "Snapshot client startup failed; retain outputs"
            assert time.monotonic() < until, "Snapshot client startup deadline reached"
            time.sleep(0.05)
        assert (self.directory / "snapshot-client-ready").read_text() == "ready\n"
        self.state["snapshot_client"] = identity(self.snapshot_client.pid)
        self.event("snapshot-client-ready", identity=self.state["snapshot_client"])

    def inspect(self, timeout=35):
        self.guard()
        self.snapshot_seq += 1
        assert self.snapshot_seq <= 128, "Snapshot request cap exceeded; no silent omission"
        request_id = f"{self.snapshot_seq:04d}"
        available = min(timeout, self.remaining())
        timeout_ms = max(1, int(available * 1000))
        request = self.directory / "snapshot-requests" / (request_id + ".edn")
        result = self.directory / "snapshot-responses" / (request_id + ".result")
        temp = request.with_suffix(".tmp")
        assert not request.exists() and not temp.exists() and not result.exists()
        with temp.open("x") as stream:
            stream.write('{:id "' + request_id + '" :timeout-ms ' + str(timeout_ms) + '}\n')
        until = min(self.deadline, time.monotonic() + available)
        temp.replace(request)
        self.event("snapshot-request", id=request_id, timeout_seconds=available,
                   response_directory="snapshot-responses")
        while not result.exists():
            assert self.snapshot_client.poll() is None, "Snapshot client exited before result"
            if time.monotonic() >= until:
                self.event("snapshot-timeout", id=request_id)
                raise TimeoutError("Snapshot request deadline; no retry or cancellation claim")
            time.sleep(min(0.02, max(0, until - time.monotonic())))
        assert time.monotonic() < until, "Snapshot result arrived after deadline"
        verdict = result.read_text()
        self.event("snapshot-result", id=request_id, verdict=verdict.strip())
        assert verdict == "ok\n", "Snapshot rejected; preserve raw responses"
        output = (self.directory / "snapshot-responses" / (request_id + ".stdout")).read_text()
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

    def await_input(self, label, predicate):
        # Read-only observations may repeat. The input gesture never repeats.
        until = min(self.deadline, time.monotonic() + 20)
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
            self.release_mouse()

    def start(self):
        self.source_hashes = verify_sources()
        self.preparation_hashes = verify_preparation()
        save(self.directory / "source-hashes.json", self.source_hashes)
        save(self.directory / "preparation-hashes.json", self.preparation_hashes)
        # Refuse an occupied port; later remote PID/boot/start checks catch races.
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", self.port))
        with (self.directory / "display.txt").open("xb") as display_out:
            self.x, _ = self.spawn(["Xvfb", "-displayfd", str(display_out.fileno()), "-screen", "0",
                                   "1280x720x24", "-nolisten", "tcp", "-ac"], "xvfb", (display_out.fileno(),))
        until = time.monotonic() + 10
        while not (self.directory / "display.txt").stat().st_size:
            assert self.x.poll() is None and time.monotonic() < until, "Xvfb startup failed/timed out"
            time.sleep(0.1)
        number = (self.directory / "display.txt").read_text().strip()
        assert number.isdecimal(), "Invalid allocated display"
        self.env["DISPLAY"] = ":" + number
        self.state.update(display=self.env["DISPLAY"], xvfb=identity(self.x.pid))
        self.app, log = self.spawn([CLOJURE, "-J-Xms256m", "-J-Xmx2g", "-M:demo", "serve", "nebula"], "runtime")
        until = time.monotonic() + 90
        while "Truth demo ready:" not in log.read_text(errors="replace"):
            assert self.app.poll() is None and time.monotonic() < until, "Game startup failed/timed out"
            time.sleep(0.25)
        self.state["app"] = identity(self.app.pid)
        self.start_snapshot_client()
        self.inspect()
        ids = self.command(["xdotool", "search", "--onlyvisible", "--pid", str(self.app.pid)], "window-id")
        windows = [line for line in ids.splitlines() if line.isdecimal()]
        assert len(windows) == 1, "Expected one visible application window on owned display"
        self.state["x-window"] = windows[0]
        self.command(["xdotool", "windowfocus", "--sync", windows[0]], "focus-owned-window")
        self.state["stage"] = "ready"
        save(self.directory / "state.json", self.state)
        self.event("ready", state=self.state)

    def action(self, request):
        operation, args = request["operation"], request.get("args", [])
        self.guard()
        if operation == "inspect":
            self.inspect()
        elif operation == "frame":
            assert self.frames == 0, "This input smoke permits one full-frame PNG"
            self.frames += 1
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
            self.command(["xdotool", "mousemove_relative", "--", str(dx), str(dy)], "look")
            self.await_input("look", lambda a: a["looking"] and not a["pick_pending"]
                             and abs(a["yaw"] - want_yaw) <= 0.02
                             and abs(a["pitch"] - want_pitch) <= 0.02)
        elif operation == "click":
            assert len(args) == 1 and args[0] in ("spark", "fine", "cruise")
            output = self.inspect()
            before = self.input_state(output)
            assert before["mode"] == "manual" and before["free"] and not before["pick_pending"]
            hits = re.findall(r"^TRUTH_APPROACH_HIT " + args[0] + r" (\d+) (\d+)$", output, re.M)
            assert len(hits) == 1, "Requested menu control not currently visible"
            want_displacement = None
            if args[0] != "spark":
                preset = re.findall(r"^TRUTH_INPUT_PRESET " + args[0] + r" (\S+)$", output, re.M)
                assert len(preset) == 1, "Missing production preset value"
                want_displacement = float(preset[0])
                assert math.isfinite(want_displacement) and want_displacement > 0
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
        except BaseException as error:
            outcome = "failed"
            self.event("failure", error=repr(error))
        finally:
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
                              mouse_pending=self.mouse_pending,
                              mouse_release_uncertain=self.mouse_release_uncertain,
                              unreaped_children=[p.pid for p, _ in self.children if p.poll() is None])
            save(self.directory / "state.json", self.state)
            save(self.directory / "closed.json", self.state)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("start", "inspect", "frame", "tap", "look", "click", "stop", "supervise"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("args", nargs="*")
    parser.add_argument("--port", type=int)
    options = parser.parse_args()
    directory = options.directory.resolve()
    assert directory.parent == HERE / "runs", "Run must be a new direct child of preparation/runs"
    if options.operation == "start":
        assert options.port and 1024 < options.port <= 65535 and options.port not in (7888, 7890, 7896)
        for tool in (CLOJURE, "java", "Xvfb", "xdotool", "ffmpeg", "git"):
            assert shutil.which(tool), f"Missing tool: {tool}"
        verify_sources()
        directory.mkdir(parents=True, exist_ok=False)
        (directory / "requests").mkdir()
        (directory / "results").mkdir()
        with (directory / "supervisor.stdout").open("xb") as out, (directory / "supervisor.stderr").open("xb") as err:
            supervisor = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "supervise", str(directory),
                                           "--port", str(options.port)], cwd=ROOT, stdin=subprocess.DEVNULL,
                                          stdout=out, stderr=err, start_new_session=True)
        save(directory / "supervisor-launch.json", {"pid": supervisor.pid, "at": utc(), "identity": identity(supervisor.pid)})
        until = time.monotonic() + 140
        while time.monotonic() < until:
            if (directory / "state.json").exists():
                state = json.loads((directory / "state.json").read_text())
                if state["stage"] in ("ready", "closed"):
                    print(json.dumps(state, indent=2))
                    return 0 if state["stage"] == "ready" else 1
            assert supervisor.poll() is None, "Supervisor exited; inspect saved logs"
            time.sleep(0.2)
        (directory / "STOP").touch(exist_ok=False)
        raise TimeoutError("Start timed out; stop requested, inspect closure")
    if options.operation == "supervise":
        def interrupted(_signum, _frame):
            raise InterruptedError("Supervisor termination requested")
        signal.signal(signal.SIGTERM, interrupted)
        Attempt(directory, options.port).run()
        return 0
    state = json.loads((directory / "state.json").read_text())
    assert state["stage"] == "ready", "Attempt is not ready"
    same_process(state["supervisor"])
    if options.operation == "stop":
        (directory / "STOP").touch(exist_ok=False)
        until = time.monotonic() + 65
        while time.monotonic() < until and not (directory / "closed.json").exists():
            time.sleep(0.2)
        assert (directory / "closed.json").exists(), "Cleanup did not confirm; inspect ownership before any intervention"
        print((directory / "closed.json").read_text())
        return 0
    with (directory / "operator.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        request_id = str(time.time_ns()) + "-" + uuid.uuid4().hex[:8]
        request = {"id": request_id, "at": utc(), "operation": options.operation, "args": options.args}
        save(directory / "requests" / (request_id + ".json"), request)
        result = directory / "results" / (request_id + ".json")
        until = time.monotonic() + 60
        while time.monotonic() < until and not result.exists() and not (directory / "closed.json").exists():
            time.sleep(0.1)
        assert result.exists(), "No result; do not repeat input, inspect closed state/logs"
        response = json.loads(result.read_text())
        print(json.dumps({"request": request_id, **response}, indent=2))
        return 0 if response["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
