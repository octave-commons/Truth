#!/usr/bin/env python3
"""Synthetic protocol checks only; native/process/network entry points blocked."""
import contextlib
import importlib.util
import json
import math
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent


def forbidden(*_args, **_kwargs):
    raise AssertionError("Native/subprocess/network operation forbidden")


class Clock:
    def __init__(self):
        self.now = 1000.0
    def monotonic(self):
        return self.now
    def sleep(self, seconds):
        assert seconds >= 0
        self.now += seconds


def frame(tick, distance, *, D=3.75e13, yaw=180, mode="manual", speed=0,
          committed=False, target=1010, ready="true", thrust="nil nil nil", world=11):
    return (f"TRUTH_APPROACH_ID {world} 22\n"
            f"TRUTH_COMMITMENT {tick} " + ("1 1010\n" if committed else "0\n") +
            f"TRUTH_FLIGHT {tick} {tick} 1 1000 0 0 0 {speed} 0 0 {thrust}\n"
            f"TRUTH_INPUT_STATE {mode} false none {D} {yaw} 0 0.01 true false\n"
            "TRUTH_INPUT_CURSOR 640 360\n"
            f"TRUTH_COMMIT_TARGET {target} planet true {ready} false {distance} 0 0 0 0 0\n")


class FakeClient:
    def __init__(self, clock):
        self.clock = clock
        self.work_end, self.total_end = 1470.0, 1500.0
        self.identity = {"world": 11, "window": 22}
        self.tick, self.distance = 100, 1e17
        self.calls, self.events = [], []
        self.overrides, self.failures, self.delays = {}, {}, {}
        self.baseline_delta = 0
        self.hold_ticks = 4
        self.hold_count = self.inspect_count = 0

    def event(self, kind, **data):
        self.events.append({"kind": kind, "time": self.clock.now, **data})

    def invoke(self, operation, deadline):
        self.calls.append({"operation": operation, "time": self.clock.now, "deadline": deadline})
        n = len(self.calls)
        self.clock.now += self.delays.get(n, 2.2 if operation == "hold" else 0.2)
        if n in self.failures:
            raise self.failures[n]
        if operation == "stop":
            return {"closed": {"stage": "closed"}}
        self.distance -= 1e13
        self.inspect_count += operation == "inspect"
        old_tick = self.tick
        self.tick += self.hold_ticks if operation == "hold" else 1
        extras = self.overrides.get(n, {})
        distance = extras.pop("distance", self.distance) if "distance" in extras else self.distance
        output = frame(self.tick, distance, **extras)
        event = None
        if operation == "hold":
            self.hold_count += 1
            baseline = self.distance + 1e13 + self.baseline_delta
            event = {"kind": "hold-complete", "key": "w",
                     "before": {"tick": old_tick, "observer": 1000, "thrust": None},
                     "during": {"tick": old_tick + 1, "thrust": [1, 0, 0]},
                     "after": {"tick": self.tick, "observer": 1000, "thrust": None},
                     "target_before": {"target": 1010, "tick": old_tick, "distance_m": baseline,
                                       "relative_position": [baseline, 0, 0], "heading_error_degrees": 0.0},
                     "target_after": {"target": 1010, "tick": self.tick, "distance_m": distance,
                                      "relative_position": [distance, 0, 0]}}
            if extras.get("missing_hold"):
                event = None
        return {"text": output, "hold": event}


class Checks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocks = contextlib.ExitStack()
        for name in ("Popen", "run", "check_output", "check_call", "call"):
            cls.blocks.enter_context(patch.object(subprocess, name, side_effect=forbidden))
        cls.blocks.enter_context(patch.object(socket, "socket", side_effect=forbidden))
        for name in ("kill", "killpg", "system", "fork", "posix_spawn", "posix_spawnp"):
            if hasattr(os, name):
                cls.blocks.enter_context(patch.object(os, name, side_effect=forbidden))
        spec = importlib.util.spec_from_file_location("cadence_under_test", HERE / "cadence_client.py")
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)
        cls.pure = cls.module.load_pure()

    @classmethod
    def tearDownClass(cls):
        cls.blocks.close()

    def setUp(self):
        self.clock = Clock()
        self.client = FakeClient(self.clock)
        self.cadence = self.module.Cadence(self.client, self.pure, 1010, self.clock)

    def run_cadence(self):
        result = self.cadence.run()
        self.assertEqual("stop", self.client.calls[-1]["operation"])
        self.assertEqual(1, sum(c["operation"] == "stop" for c in self.client.calls))
        self.assertLessEqual(sum(c["operation"] == "hold" for c in self.client.calls), 3)
        return result

    def test_three_fixed_pulses_and_spaced_advancing_coasts(self):
        result = self.run_cadence()
        self.assertEqual("three-pulses-completed", result["outcome"])
        self.assertEqual(3, len(result["intervals"]))
        for item in result["intervals"]:
            self.assertGreaterEqual(item["coast_b"]["at"] - item["coast_a"]["at"], 1)
            self.assertGreater(item["coast_b"]["tick"], item["coast_a"]["tick"])
            self.assertLess(item["range_delta_m"], 0)
        self.assertTrue(all(c["operation"] in ("inspect", "hold", "stop") for c in self.client.calls))

    def test_actual_pre_keydown_baseline_not_earlier_inspect(self):
        self.client.baseline_delta = -5e14
        result = self.run_cadence()
        self.assertEqual("two-noncontractions", result["outcome"])
        self.assertEqual(2, self.client.hold_count)
        self.assertTrue(all(i["range_delta_m"] > 0 for i in result["intervals"]))

    def test_terminal_precedes_invalid_target_and_closes_without_hold(self):
        self.client.overrides[1] = {"committed": True, "ready": "false", "distance": float("nan")}
        result = self.run_cadence()
        self.assertEqual("commitment-observed", result["outcome"])
        self.assertEqual(0, self.client.hold_count)

    def test_changed_D_camera_mode_world_and_readiness_stop(self):
        for changed in ({"D": 7.5e13}, {"yaw": 180.01}, {"mode": "follow-selection"},
                        {"world": 99}, {"ready": "false"}):
            with self.subTest(changed=changed):
                self.setUp()
                self.client.overrides[3] = changed
                result = self.run_cadence()
                self.assertEqual("failed", result["outcome"])
                self.assertEqual(0, self.client.hold_count)

    def test_nonfinite_geometry_and_inherited_envelope_excess_stop(self):
        for changed in ({"distance": float("nan")}, {"speed": 3.75e13 + 1e10}):
            with self.subTest(changed=changed):
                self.setUp()
                self.client.overrides[1] = changed
                self.assertEqual("failed", self.run_cadence()["outcome"])
                self.assertEqual(0, self.client.hold_count)

    def test_reservation_is_not_replenished_or_duplicated(self):
        self.client.distance = 1.3e16
        self.client.hold_ticks = 100
        result = self.run_cadence()
        self.assertEqual("failed", result["outcome"])
        self.assertEqual(1, self.client.hold_count)
        self.assertEqual(100, result["consumed_tick_envelope"])

    def test_missing_hold_baseline_stops_before_coast_or_second_gesture(self):
        original = self.client.invoke
        def invoke(op, deadline):
            out = original(op, deadline)
            if op == "hold":
                out["hold"] = None
            return out
        self.client.invoke = invoke
        self.assertEqual("failed", self.run_cadence()["outcome"])
        self.assertEqual(1, self.client.hold_count)

    def test_read_error_identity_preserved_and_no_gesture_replay(self):
        self.client.failures[4] = RuntimeError("uncertain hold")
        result = self.run_cadence()
        self.assertEqual("failed", result["outcome"])
        self.assertIn("uncertain hold", result["error"])
        self.assertEqual(1, sum(c["operation"] == "hold" for c in self.client.calls))

    def test_expired_phase_admits_no_command_except_stop(self):
        self.clock.now = self.cadence.deadline
        self.assertEqual("failed", self.run_cadence()["outcome"])
        self.assertEqual(["stop"], [c["operation"] for c in self.client.calls])

    def test_late_matching_reply_is_rejected_without_hold(self):
        self.client.delays[1] = 61
        self.assertEqual("failed", self.run_cadence()["outcome"])
        self.assertEqual(0, self.client.hold_count)

    def test_cleanup_failure_is_not_reported_as_clean_completion(self):
        self.client.overrides[1] = {"committed": True}
        self.client.failures[2] = RuntimeError("cleanup unavailable")
        result = self.run_cadence()
        self.assertEqual("commitment-observed", result["outcome"])
        self.assertIn("cleanup unavailable", result["cleanup_error"])

    def test_saved_result_binding_rejects_false_success(self):
        self.module.validate_reply({"request": "r", "ok": True, "outcome": "complete"}, {"ok": True, "outcome": "complete"})
        for response, saved in (({"ok": True}, {"ok": True}),
                                ({"request": "r", "ok": True}, {"ok": False}),
                                ({"request": "r", "ok": True, "outcome": "a"}, {"ok": True, "outcome": "b"})):
            with self.subTest(response=response), self.assertRaises(AssertionError):
                self.module.validate_reply(response, saved)

    def adapter(self, directory):
        """Instantiate the actual I/O adapter against synthetic files only."""
        client = self.module.Client.__new__(self.module.Client)
        client.run = directory / "run"
        client.logs = directory / "logs"
        client.run.mkdir()
        client.logs.mkdir()
        (client.run / "results").mkdir()
        (client.run / "operations.jsonl").write_bytes(b"")
        (client.run / "snapshot-current.stdout").write_text(frame(100, 1e17))
        client.pure, client.seq, client.children = self.pure, 0, []
        client.total_end, client.stop_sent = 1500.0, False
        client.guard = lambda: None  # Real process identity is explicitly unexercised.
        return client

    def test_actual_adapter_timeout_reaps_only_own_fake_child_then_stop(self):
        with tempfile.TemporaryDirectory(dir=HERE) as temp:
            client = self.adapter(Path(temp))
            calls, processes = [], []
            closed = {"stage": "closed", "cleanup_errors": [], "unreaped_children": [],
                      "budget_exceeded": False, "total_budget_exceeded": False,
                      "key_pending": None, "key_release_uncertain": False,
                      "mouse_pending": False, "mouse_release_uncertain": False}
            class Child:
                pid = 123456
                def __init__(self, operation):
                    self.operation, self.returncode, self.terminated = operation, None, False
                    self.waits, self.killed = [], False
                def poll(self):
                    return self.returncode
                def wait(self, timeout):
                    self.waits.append(timeout)
                    if self.operation == "hold" and not self.terminated:
                        raise subprocess.TimeoutExpired("fake-hold", timeout)
                    self.returncode = 0
                    return 0
                def terminate(self):
                    self.terminated = True
                def kill(self):
                    self.killed = True
            def popen(argv, **kwargs):
                op = argv[2]
                calls.append(op)
                self.assertIn(op, ("hold", "stop"))
                child = Child(op)
                processes.append(child)
                if op == "stop":
                    kwargs["stdout"].write(json.dumps(closed).encode())
                    kwargs["stdout"].flush()
                return child
            with patch.object(self.module.time, "monotonic", self.clock.monotonic), patch.object(subprocess, "Popen", side_effect=popen):
                with self.assertRaises(subprocess.TimeoutExpired):
                    client.invoke("hold", 1001.5)
                response = client.invoke("stop", 1065)
            self.assertEqual(["hold", "stop"], calls)
            self.assertEqual([1.5, 2], processes[0].waits)
            self.assertTrue(processes[0].terminated)
            self.assertFalse(processes[0].killed)
            self.assertTrue(all(p.poll() is not None for p in processes))
            self.assertEqual(closed, response["closed"])
            recorded = [json.loads(s) for s in (client.logs / "controller.jsonl").read_text().splitlines()]
            self.assertEqual(2, sum(e["kind"] == "reaped" and e["reaped"] for e in recorded))

    def test_actual_adapter_preserves_stdout_and_rejects_mismatched_saved_result(self):
        with tempfile.TemporaryDirectory(dir=HERE) as temp:
            client = self.adapter(Path(temp))
            response = {"request": "fake-r", "ok": True, "outcome": "complete"}
            (client.run / "results/fake-r.json").write_text(json.dumps({"ok": False, "outcome": "failed"}))
            class Child:
                pid, returncode = 123457, 0
                def poll(self):
                    return 0
                def wait(self, timeout):
                    return 0
            def popen(_argv, **kwargs):
                kwargs["stdout"].write(json.dumps(response).encode())
                kwargs["stdout"].flush()
                return Child()
            with patch.object(self.module.time, "monotonic", self.clock.monotonic), patch.object(subprocess, "Popen", side_effect=popen):
                with self.assertRaisesRegex(AssertionError, "Saved action/result mismatch"):
                    client.invoke("inspect", 1025)
            self.assertEqual(response, json.loads((client.logs / "001-inspect.stdout").read_text()))
            self.assertFalse((client.logs / "001-inspect-snapshot.txt").exists())

    def test_nonadvancing_post_hold_read_stops_without_more_input(self):
        original = self.client.invoke
        def invoke(op, deadline):
            result = original(op, deadline)
            if len(self.client.calls) == 5:
                self.client.tick -= 1
                result["text"] = frame(self.client.tick, self.client.distance)
            return result
        self.client.invoke = invoke
        self.assertEqual("failed", self.run_cadence()["outcome"])
        self.assertEqual(1, self.client.hold_count)

    def test_dt_and_observer_changes_stop_before_hold(self):
        for old, new in ((" 1 1000 ", " 2 1000 "), (" 1 1000 ", " 1 1001 ")):
            with self.subTest(new=new):
                self.setUp()
                original = self.client.invoke
                def invoke(op, deadline):
                    result = original(op, deadline)
                    if len(self.client.calls) == 3:
                        result["text"] = result["text"].replace(old, new)
                    return result
                self.client.invoke = invoke
                self.assertEqual("failed", self.run_cadence()["outcome"])
                self.assertEqual(0, self.client.hold_count)


if __name__ == "__main__":
    unittest.main(verbosity=2)
