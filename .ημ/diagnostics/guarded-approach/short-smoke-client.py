#!/usr/bin/env python3
"""Complete one fresh input-only smoke through the frozen runner CLI.

Usage: python3 short-smoke-client.py attempt-01 --port 7898
No native APIs, process discovery, world access, or input retry occurs here.
Only directly created runner CLIENT processes are owned by this controller;
the unchanged detached runner supervisor owns the game and its cleanup.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
RUNNER = HERE / "runner.py"
RUNNER_SHA256 = "509344fe1bf0dc6c9cc070bc6793af7f433462850e56669f0e2b30ec9a649f40"
BASE = "b395c4049718f7ce015ddf25fc0a192d97821373"
SEQUENCE = (("inspect",), ("inspect",),
            ("tap", "r"), ("click", "spark"), ("click", "spark"),
            ("tap", "Tab"),
            ("look", "100", "0"), ("look", "-100", "0"),
            ("look", "0", "100"), ("look", "0", "-100"),
            ("tap", "Tab"), ("tap", "Tab"))


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def read_json(path):
    value = json.loads(path.read_text())
    assert isinstance(value, dict), f"Expected JSON object: {path}"
    return value


class Completion:
    def __init__(self, name, port):
        assert re.fullmatch(r"attempt-[0-9]+", name), "Use a fresh attempt-N name"
        assert 1024 < port <= 65535 and port not in (7888, 7890, 7896)
        self.run = HERE / "runs" / name
        self.logs = HERE / "completion-client-runs" / name
        assert not self.run.exists() and not self.logs.exists(), "Fresh attempt only"
        assert hashlib.sha256(RUNNER.read_bytes()).hexdigest() == RUNNER_SHA256
        self.logs.mkdir(parents=True, exist_ok=False)
        self.started = time.monotonic()
        self.deadline = self.started + 300
        self.work_deadline = self.started + 270
        self.port, self.sequence, self.pin = port, 0, None
        self.clients = []
        self.report = {"started_at": utc(), "run": str(self.run), "port": port,
                       "runner_sha256": RUNNER_SHA256, "base": BASE,
                       "controller_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                       "total_seconds": 300, "work_seconds": 270,
                       "minimum_work_seconds_before_input": 40,
                       "clock": "Controller monotonic start precedes supervisor; budget is conservative.",
                       "planned_sequence": [list(s) for s in SEQUENCE], "completed": []}

    def event(self, kind, **fields):
        with (self.logs / "controller.jsonl").open("a") as stream:
            stream.write(json.dumps({"at": utc(), "kind": kind,
                                     "elapsed_seconds": time.monotonic() - self.started,
                                     **fields}) + "\n")

    def ready(self, state):
        assert state["stage"] == "ready", "Runner no longer ready"
        assert state["base"] == BASE and state["cwd"] == str(ROOT)
        assert state["port"] == self.port
        assert (state["work_budget_seconds"], state["budget_seconds"]) == (270, 300)
        pin = {k: state[k] for k in ("supervisor", "app", "xvfb", "display", "world", "window", "x-window", "snapshot_client")}
        assert self.pin is None or pin == self.pin, "Recorded run identity changed"
        self.pin = pin

    def invoke(self, operation, *args):
        assert hashlib.sha256(RUNNER.read_bytes()).hexdigest() == RUNNER_SHA256
        if operation in ("tap", "look", "click"):
            self.ready(read_json(self.run / "state.json"))
            assert self.work_deadline - time.monotonic() >= 40, "Input reserve exhausted; no gesture submitted"
        self.sequence += 1
        stem = f"{self.sequence:02d}-{operation}"
        argv = [sys.executable, str(RUNNER), operation, str(self.run), *args]
        if operation == "start":
            argv += ["--port", str(self.port)]
        remaining = self.deadline - time.monotonic()
        assert remaining > 0, "Controller deadline reached"
        timeout = min(145 if operation == "start" else 65, remaining)
        stdout, stderr = self.logs / (stem + ".stdout"), self.logs / (stem + ".stderr")
        self.event("client-command", argv=argv, cwd=str(ROOT), timeout_seconds=timeout,
                   stdout=stdout.name, stderr=stderr.name,
                   remaining_work_seconds=self.work_deadline - time.monotonic())
        with stdout.open("xb") as out, stderr.open("xb") as err:
            child = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
            self.clients.append(child)
            self.event("client-spawn", pid=child.pid, stem=stem)
            try:
                code = child.wait(timeout=timeout)
            finally:
                if child.poll() is None:
                    # Only this Popen-owned client, never the detached supervisor or game.
                    child.kill()
                    child.wait(timeout=max(0.01, min(2, self.deadline - time.monotonic())))
                self.event("client-exit", pid=child.pid, stem=stem, returncode=child.returncode,
                           reaped=child.poll() is not None)
        assert code == 0, f"Client {stem} exited {code}; stop sequence and retain output"
        result = read_json(stdout)  # Reject non-JSON, mixed output, and false success.
        if operation == "start":
            self.ready(result)
        elif operation != "stop":
            assert result.get("ok") is True and isinstance(result.get("request"), str)
            saved = read_json(self.run / "results" / (result["request"] + ".json"))
            assert saved.get("ok") is True and all(result.get(k) == v for k, v in saved.items())
            self.ready(read_json(self.run / "state.json"))
        self.event("client-validated", operation=operation, args=list(args), response=result)
        return result

    def close(self):
        # At most one stop submission. An already closed attempt receives no command.
        stop_sent = False
        while time.monotonic() < self.deadline:
            if (self.run / "closed.json").exists():
                closed = read_json(self.run / "closed.json")
                self.report["closed"] = closed
                assert closed["stage"] == "closed" and closed["outcome"] == "operator-stop"
                assert not closed["cleanup_errors"] and not closed["unreaped_children"]
                assert closed["budget_exceeded"] is False
                assert closed["mouse_pending"] is False and closed["mouse_release_uncertain"] is False
                return
            state_file = self.run / "state.json"
            if not stop_sent and state_file.exists() and read_json(state_file)["stage"] == "ready":
                stop_sent = True
                try:
                    self.invoke("stop")
                except BaseException as error:
                    # Closure can race the CLI's ready assertion. Record it and read
                    # the final outcome; never repeat a stop or an input command.
                    self.report["stop_client_error"] = repr(error)
            if not self.run.exists():
                raise RuntimeError("Start did not create a run; no supervisor cleanup command available")
            time.sleep(min(0.2, max(0, self.deadline - time.monotonic())))
        raise TimeoutError("No confirmed closure within controller budget; inspect preserved ownership evidence")

    def execute(self):
        errors = []
        try:
            self.invoke("start")
            for action in SEQUENCE:
                self.invoke(*action)
                self.report["completed"].append(list(action))
        except BaseException as error:
            errors.append(repr(error))
            self.event("sequence-stopped", error=repr(error))
        finally:
            try:
                self.close()
            except BaseException as error:
                errors.append(repr(error))
            self.report.update(ended_at=utc(), errors=errors,
                               outcome="passed" if not errors and "stop_client_error" not in self.report else "failed",
                               elapsed_seconds=time.monotonic() - self.started,
                               controller_budget_exceeded=time.monotonic() > self.deadline,
                               unreaped_client_pids=[p.pid for p in self.clients if p.poll() is None])
            if self.report["controller_budget_exceeded"] or self.report["unreaped_client_pids"]:
                self.report["outcome"] = "failed"
            with (self.logs / "result.json").open("x") as stream:
                json.dump(self.report, stream, indent=2)
                stream.write("\n")
        print(json.dumps(self.report, indent=2))
        return 0 if self.report["outcome"] == "passed" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("attempt")
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()

    def interrupted(signum, _frame):
        raise InterruptedError(f"Controller signal {signum}; abort inputs and request runner cleanup")

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    return Completion(args.attempt, args.port).execute()


if __name__ == "__main__":
    sys.exit(main())
