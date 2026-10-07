#!/usr/bin/env python3
"""Pure diagnostic framing/deadline checks; no JVM, native child or socket allowed.

Synthetic strings below characterize the observation protocol only. They are not
game worlds, natural formation evidence, or production predicate evaluations.
"""
import contextlib
import importlib.util
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

HERE = Path(__file__).resolve().parent


def forbidden(*_args, **_kwargs):
    raise AssertionError("Subprocess/native/process operation forbidden in pure checks")


def load_runner():
    spec = importlib.util.spec_from_file_location("commit_ready_protocol", HERE / "runner.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Clock:
    def __init__(self, now=900.0):
        self.now = now

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


def frame(stored="true", ready="true", fresh="false", committed=(), tick=100,
          target=1010, position="10 0 0", thrust="nil nil nil"):
    return (f"TRUTH_APPROACH_ID 11 22\n"
            f"TRUTH_COMMITMENT {tick} {len(committed)}" + "".join(f" {v}" for v in committed) + "\n"
            f"TRUTH_FLIGHT {tick} 500 1 1 0 0 0 0 0 0 {thrust}\n"
            "TRUTH_INPUT_STATE manual false none 10000000 180 0 0.01 true false\n"
            "TRUTH_INPUT_CURSOR 640 360\n"
            f"TRUTH_COMMIT_TARGET {target} planet {stored} {ready} {fresh} {position} 0 0 0\n")


class ProtocolChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blockers = contextlib.ExitStack()
        for name in ("Popen", "run", "check_output", "check_call", "call"):
            cls.blockers.enter_context(patch.object(subprocess, name, side_effect=forbidden))
        cls.blockers.enter_context(patch.object(socket, "socket", side_effect=forbidden))
        cls.r = load_runner()
        for name in ("kill", "killpg", "system", "fork", "posix_spawn", "posix_spawnp"):
            if hasattr(cls.r.os, name):
                cls.blockers.enter_context(patch.object(cls.r.os, name, side_effect=forbidden))
        cls.blockers.enter_context(patch.object(cls.r, "identity", side_effect=forbidden))

    @classmethod
    def tearDownClass(cls):
        cls.blockers.close()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="pure-check-", dir=HERE)
        self.addCleanup(self.tmp.cleanup)
        self.clock = Clock()
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(self.r.time, "monotonic", self.clock.monotonic))
        self.stack.enter_context(patch.object(self.r.time, "sleep", self.clock.sleep))
        self.a = self.r.Attempt.__new__(self.r.Attempt)
        a = self.a
        a.directory = Path(self.tmp.name)
        (a.directory / "requests").mkdir()
        (a.directory / "results").mkdir()
        a.started = 0.0
        a.starting = False
        a.startup_deadline = 180.0
        a.no_target_deadline = 960.0
        a.deadline = 1470.0
        a.cleanup_deadline = 1500.0
        a.admission = a.target = a.terminal_commitment = a.active_aim = a.aim_deadline = None
        a.state = {}
        a.frames = a.looks = a.holds = a.aims = a.snapshot_seq = 0
        a.key_pending = None
        a.key_release_uncertain = a.mouse_pending = a.mouse_release_uncertain = False
        a.children = []
        a.snapshot_client = a.app = a.x = None
        a.events = []
        a.event = lambda kind, **data: a.events.append((kind, data))
        a.guard = lambda **_kwargs: a.remaining()

    def admit(self, output=None):
        self.a.inspect = lambda **_kwargs: frame() if output is None else output
        self.a.action({"operation": "aim-start", "args": ["1010"]})

    def test_historical_ready_admits_without_fresh_handoff(self):
        self.admit()
        self.assertEqual(1010, self.a.target)
        self.assertFalse(self.a.admission["geometry"]["fresh_handoff"])
        self.assertEqual(1020, self.a.aim_deadline)

    def test_fresh_handoff_cannot_replace_either_admission_flag(self):
        for stored, ready in (("false", "true"), ("true", "false"), ("false", "false")):
            with self.subTest(stored=stored, ready=ready), self.assertRaises(AssertionError):
                self.admit(frame(stored=stored, ready=ready, fresh="true"))
            self.assertIsNone(self.a.target)
            self.assertIsNone(self.a.admission)
            self.assertEqual(960, self.a.work_limit())

    def test_missing_duplicate_nonfinite_and_other_target_fail(self):
        valid = frame()
        target_line = valid.splitlines()[-1] + "\n"
        for output in (valid.replace(target_line, ""), valid + target_line,
                       frame(position="nan 0 0"), frame(position="10 inf 0"), frame(target=1011)):
            with self.subTest(output=output), self.assertRaises(AssertionError):
                self.admit(output)
            self.assertIsNone(self.a.admission)
            self.assertIsNone(self.a.target)

    def test_global_prior_commitment_precedes_invalid_target(self):
        with self.assertRaises(self.r.CommitmentObserved):
            self.admit(frame(stored="false", ready="false", committed=(7, 1010), position="nan 0 0"))
        self.assertEqual([7, 1010], self.a.terminal_commitment["eids"])
        self.assertIsNone(self.a.admission)
        self.assertIsNone(self.a.target)

    def test_terminal_prevents_later_input_before_guard_or_command(self):
        self.a.terminal_commitment = {"tick": 100, "count": 1, "eids": [1010]}
        self.a.guard = forbidden
        self.a.command = forbidden
        for operation in ("tap", "look", "click", "hold", "aim-start", "aim-end"):
            with self.subTest(operation=operation), self.assertRaises(self.r.CommitmentObserved):
                self.a.action({"operation": operation, "args": []})

    def test_global_commitment_frame_rejects_incomplete_or_unpaired_evidence(self):
        for line in ("TRUTH_COMMITMENT 100 2 1010", "TRUTH_COMMITMENT 100 2 7 7",
                     "TRUTH_COMMITMENT 101 0", "TRUTH_COMMITMENT 100 -1"):
            output = frame().replace("TRUTH_COMMITMENT 100 0", line)
            with self.subTest(line=line), self.assertRaises(AssertionError):
                self.r.commitment_frame(output)

    def test_deadlines_use_shared_origin_and_only_success_lifts_phase_limit(self):
        self.assertEqual(960, self.a.work_limit())
        self.a.starting = True
        self.assertEqual(180, self.a.work_limit())
        self.a.starting = False
        self.a.target = 1010  # even a stray tentative assignment cannot lift cutoff
        self.assertEqual(960, self.a.work_limit())
        self.clock.now = 960
        with self.assertRaises(self.r.NoTargetDeadline):
            self.a.remaining()

    def test_late_valid_admission_has_original_work_reserve(self):
        self.clock.now = 959.5
        self.admit()
        self.assertEqual(959.5, self.a.admission["elapsed_seconds"])
        self.assertEqual(1079.5, self.a.aim_deadline)
        self.a.active_aim = self.a.aim_deadline = None
        self.assertEqual(1470, self.a.work_limit())

    def test_snapshot_completion_at_or_after_cutoff_never_admits(self):
        for finish in (960, 960.1):
            self.clock.now = 959
            def snapshot(**_kwargs):
                self.clock.now = finish
                return frame()
            self.a.inspect = snapshot
            with self.subTest(finish=finish), self.assertRaises(self.r.NoTargetDeadline):
                self.a.action({"operation": "aim-start", "args": ["1010"]})
            self.assertIsNone(self.a.target)
            self.assertIsNone(self.a.admission)

    def test_successful_target_cannot_be_replaced(self):
        self.admit()
        self.a.active_aim = self.a.aim_deadline = None
        with self.assertRaisesRegex(AssertionError, "no retarget"):
            self.a.action({"operation": "aim-start", "args": ["1011"]})

    def test_idle_supervisor_begins_cleanup_at_cutoff(self):
        self.clock.now = 959.95
        self.a.start = lambda: None
        self.a.app = self.a.x = Mock()
        self.a.app.poll.return_value = None
        released = []
        self.a.release = lambda: released.append(self.clock.now)
        self.a.run()
        self.assertEqual([960], released)
        self.assertEqual("no-target-deadline", self.a.state["outcome"])
        self.assertEqual(990, self.a.cleanup_deadline)

    def test_late_admission_result_can_be_observed_after_phase_cutoff(self):
        caller_here = self.a.directory / "caller"
        directory = caller_here / "runs" / "only-pure-data"
        (directory / "requests").mkdir(parents=True)
        (directory / "results").mkdir()
        self.clock.now = 959.99
        self.r.save(directory / "state.json", {"stage": "ready", "supervisor": {},
                    "supervisor_monotonic_started": 0.0, "no_target_deadline": 960.0,
                    "successful_admission": None})
        def completion_wait(seconds):
            self.clock.sleep(seconds)
            requests = list((directory / "requests").glob("*.json"))
            self.assertEqual(1, len(requests))
            # A pure completion timing model, not actual world admission evidence.
            self.r.save(directory / "results" / requests[0].name,
                        {"ok": True, "accepted_monotonic": 959.999, "published_monotonic": self.clock.now})
        self.stack.enter_context(patch.object(self.r.time, "sleep", completion_wait))
        self.stack.enter_context(patch.object(self.r, "HERE", caller_here))
        self.stack.enter_context(patch.object(self.r, "same_process", lambda _saved: {}))
        self.stack.enter_context(patch.object(self.r.signal, "signal", Mock()))
        self.stack.enter_context(patch.object(self.r.sys, "argv",
            ["runner.py", "aim-start", str(directory), "1010"]))
        self.stack.enter_context(contextlib.redirect_stdout(__import__("io").StringIO()))
        self.assertEqual(0, self.r.main())
        self.assertGreater(self.clock.now, 960)
        self.assertFalse((directory / "STOP").exists())

    def test_pending_snapshot_wait_is_clipped_at_cutoff(self):
        self.clock.now = 959.99
        self.a.snapshot_client = Mock()
        self.a.snapshot_client.poll.return_value = None
        with self.assertRaises(self.r.NoTargetDeadline):
            self.a.inspect(timeout=35)
        requests = [v for k, v in self.a.events if k == "snapshot-request"]
        self.assertEqual(1, len(requests))
        self.assertLessEqual(requests[0]["timeout_seconds"], 0.011)
        self.assertEqual(960, self.clock.now)

    def test_child_wait_uses_remaining_no_target_budget(self):
        self.clock.now = 959.75
        child = Mock()
        child.wait.return_value = 0
        self.a.finish(child, 5, "pure mocked helper")
        child.wait.assert_called_once_with(timeout=0.25)

    def test_terminal_result_closes_run_and_retains_observation(self):
        request = self.a.directory / "requests" / "one.json"
        request.write_text('{"operation":"inspect"}')
        self.a.app = self.a.x = Mock()
        self.a.app.poll.return_value = None
        self.a.start = lambda: None
        self.a.inspect = lambda: self.a.observe_commitment(frame(committed=(1010,)))
        released = []
        self.a.release = lambda: released.append(True)
        self.a.run()
        result = self.r.json.loads((self.a.directory / "results" / "one.json").read_text())
        self.assertEqual("commitment-observed", result["outcome"])
        self.assertTrue(result["ok"])
        self.assertEqual([1010], result["terminal_commitment"]["eids"])
        self.assertEqual("commitment-observed", self.a.state["outcome"])
        self.assertEqual([True], released)

    def test_held_key_released_once_when_commitment_appears(self):
        self.a.admission = {"target": 1010}
        self.a.target = 1010
        reads = iter((frame(), frame(committed=(1010,), ready="false", position="nan 0 0")))
        def snapshot(**_kwargs):
            output = next(reads)
            self.a.observe_commitment(output)
            return output
        self.a.inspect = snapshot
        commands = []
        self.a.command = lambda argv, *_args, **_kwargs: commands.append(argv)
        self.a.state["xvfb"] = {"test": "no actual PID"}
        self.stack.enter_context(patch.object(self.r, "same_process", lambda _saved: {}))
        with self.assertRaises(self.r.CommitmentObserved):
            self.a.hold(["w", "2"])
        self.assertEqual([["xdotool", "keydown", "w"], ["xdotool", "keyup", "w"]], commands)
        self.assertIsNone(self.a.key_pending)
        self.assertFalse(self.a.key_release_uncertain)
        self.assertEqual([1010], self.a.terminal_commitment["eids"])

    def test_postrelease_commitment_precedes_invalid_target_geometry(self):
        self.a.admission = {"target": 1010}
        self.a.target = 1010
        reads = iter((frame(), frame(tick=101, thrust="1 0 0"),
                      frame(tick=102, committed=(1010,), ready="false", position="nan 0 0")))
        def snapshot(**_kwargs):
            output = next(reads)
            self.a.observe_commitment(output)
            return output
        self.a.inspect = snapshot
        commands = []
        self.a.command = lambda argv, *_args, **_kwargs: commands.append(argv)
        self.a.state["xvfb"] = {"test": "no actual PID"}
        self.stack.enter_context(patch.object(self.r, "same_process", lambda _saved: {}))
        with self.assertRaises(self.r.CommitmentObserved):
            self.a.hold(["w", "2"])
        self.assertEqual(2, len(commands))
        self.assertEqual(["xdotool", "keyup", "w"], commands[-1])
        self.assertIsNone(self.a.key_pending)
        self.assertEqual(102, self.a.terminal_commitment["tick"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
