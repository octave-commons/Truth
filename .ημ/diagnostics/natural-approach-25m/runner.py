#!/usr/bin/env python3
"""One disposable natural-nebula attempt; preparation does not execute this file.

Only the detached supervisor owns subprocesses. External stages submit bounded,
allowlisted requests; there is no arbitrary shell, nREPL, world or camera setter.
"""
import argparse
import datetime
import fcntl
import hashlib
import json
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
MOVE_KEYS = ("w", "a", "s", "d", "space", "Control_L")
TAP_KEYS = ("r", "Tab", "comma", "period")
RELEASE_KEYS = MOVE_KEYS + TAP_KEYS + ("Shift_L", "Shift_R")


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
        self.deadline = self.started + 1470  # reserve 30 seconds for owned cleanup
        self.cleanup_deadline = self.started + 1500
        self.children = []
        self.seq = 0
        self.state = {"started_at": utc(), "base": BASE, "cwd": str(ROOT), "port": port,
                      "budget_seconds": 1500, "work_budget_seconds": 1470,
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
        self.x = self.app = self.video = None

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
        self.remaining()

    def inspect(self):
        self.guard()
        expected = {k: self.state["app"][k] for k in ("pid", "boot", "start", "cwd")}
        expected.update(display=self.state["display"], port=str(self.port))
        for k in ("world", "window"):
            if k in self.state:
                expected[k] = self.state[k]
        # JSON string literals here are Clojure string literals, not shell input.
        edn = "{" + " ".join(":" + k + " " + json.dumps(v) for k, v in expected.items()) + "}"
        form = "((load-file " + json.dumps(str(HERE / "snapshot.clj")) + ") " + edn + ")"
        output = self.command([CLOJURE, "-J-Xms64m", "-J-Xmx256m", "-M:demo-client", form], "inspect", 35)
        matches = re.findall(r"^TRUTH_APPROACH_ID (\d+) (\d+)$", output, re.M)
        assert len(matches) == 1, "Missing/duplicate identity frame"
        self.state.update(world=int(matches[0][0]), window=int(matches[0][1]))
        save(self.directory / "state.json", self.state)
        return output

    def release(self):
        if self.x and self.x.poll() is None and "xvfb" in self.state:
            same_process(self.state["xvfb"])
            self.command(["xdotool", "keyup", *RELEASE_KEYS, "mouseup", "1"], "release-all", 2, cleanup=True)

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
            filename = f"frame-{request['id']}.png"
            self.command(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-threads", "1",
                          "-f", "x11grab", "-draw_mouse", "0", "-video_size", "1280x720", "-i",
                          self.state["display"] + ".0", "-frames:v", "1", str(self.directory / filename)], "frame", 10)
        elif operation == "record":
            seconds = float(args[0])
            assert 1 <= seconds <= 60 and seconds + 5 < self.remaining(), "Video exceeds budget"
            assert not self.video or self.video.poll() is not None, "One video at a time"
            self.video, _ = self.spawn(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                                       "-f", "x11grab", "-draw_mouse", "0", "-framerate", "12",
                                       "-video_size", "1280x720", "-i", self.state["display"] + ".0",
                                       "-t", str(seconds), "-c:v", "libx264", "-threads", "1",
                                       "-preset", "ultrafast", "-crf", "26", "-pix_fmt", "yuv420p",
                                       str(self.directory / f"video-{request['id']}.mp4")], "video")
        elif operation == "tap":
            assert args[0] in TAP_KEYS
            try:
                self.command(["xdotool", "key", "--delay", "80", args[0]], "tap")
            finally:
                self.release()
        elif operation == "hold":
            key, seconds = args[0], float(args[1])
            assert key in MOVE_KEYS and 0 < seconds <= 10 and seconds + 3 < self.remaining()
            try:
                self.command(["xdotool", "keydown", key], "keydown")
                time.sleep(seconds)
            finally:
                self.release()
        elif operation == "look":
            dx, dy = map(int, args)
            assert abs(dx) <= 500 and abs(dy) <= 500
            self.command(["xdotool", "mousemove_relative", "--", str(dx), str(dy)], "look")
        elif operation == "click":
            assert args[0] in ("spark", "fine", "cruise")
            output = self.inspect()
            assert "TRUTH_APPROACH_VIEW manual true" in output, "Menu clicking requires manual mode/free cursor"
            hits = re.findall(r"^TRUTH_APPROACH_HIT " + args[0] + r" (\d+) (\d+)$", output, re.M)
            assert len(hits) == 1, "Requested menu control not currently visible"
            try:
                self.command(["xdotool", "mousemove", *hits[0], "click", "1"], "menu-click")
            finally:
                self.release()
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
                for child, label in self.children:
                    if child is self.video and child.poll() is not None:
                        assert child.returncode == 0, f"Video failed: {label}"
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
            # Reap helpers/video first, then Java, and only then its X server.
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
                              unreaped_children=[p.pid for p, _ in self.children if p.poll() is None])
            save(self.directory / "state.json", self.state)
            save(self.directory / "closed.json", self.state)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("start", "inspect", "frame", "record", "tap", "hold", "look", "click", "stop", "supervise"))
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
