#!/usr/bin/env python3
"""Synthetic protocol checks only; native/process/network entry points blocked."""
import ast
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


def frame(tick, distance, *, D=1.5e14, retention=0.8, yaw=180, mode="manual", speed=0,
          committed=False, target=1010, ready="true", thrust="nil nil nil", world=11):
    return (f"TRUTH_APPROACH_ID {world} 22\n"
            f"TRUTH_COMMITMENT {tick} " + ("1 1010\n" if committed else "0\n") +
            f"TRUTH_FLIGHT {tick} {tick} 1 1000 0 0 0 {speed} 0 0 {thrust}\n"
            f"TRUTH_INPUT_STATE {mode} false none {D} {yaw} 0 0.01 true false\n"
            f"TRUTH_DAMPING_STATE {retention}\n"
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
        self.aim_count = 0
        self.target = 1010

    def event(self, kind, **data):
        self.events.append({"kind": kind, "time": self.clock.now, **data})

    def invoke(self, operation, deadline):
        self.calls.append({"operation": operation, "time": self.clock.now, "deadline": deadline})
        n = len(self.calls)
        self.clock.now += self.delays.get(n, 2.2 if operation == "hold" else 0.2)
        if n in self.failures:
            raise self.failures[n]
        if operation == "aim":
            self.aim_count += 1
            return {"aim": {"outcome": "aimed-at-one-published-observation", "target": 1010}}
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
        self.assertTrue(all(c["operation"] in ("inspect", "aim", "hold", "stop") for c in self.client.calls))

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
                self.client.overrides[5 if "yaw" in changed else 4] = changed
                result = self.run_cadence()
                self.assertEqual("failed", result["outcome"])
                self.assertEqual(1 if "yaw" in changed else 0, self.client.hold_count)

    def test_nonfinite_geometry_and_inherited_envelope_excess_stop(self):
        for changed in ({"distance": float("nan")}, {"speed": 1.5e14 + 1e10}):
            with self.subTest(changed=changed):
                self.setUp()
                self.client.overrides[1] = changed
                self.assertEqual("failed", self.run_cadence()["outcome"])
                self.assertEqual(0, self.client.hold_count)

    def test_reservation_is_not_replenished_or_duplicated(self):
        self.client.distance = 2.5e16
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
        self.client.failures[5] = RuntimeError("uncertain hold")
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

    def test_one_aim_after_released_baselines_then_immediate_pulse_phase(self):
        self.client.overrides[1] = {"yaw": 120}
        self.client.overrides[2] = {"yaw": 120}
        self.client.delays[3] = 80
        result = self.run_cadence()
        self.assertEqual("three-pulses-completed", result["outcome"])
        operations = [c["operation"] for c in self.client.calls]
        self.assertEqual(["inspect", "inspect", "aim", "inspect", "hold"], operations[:5])
        self.assertEqual(3, self.client.aim_count)
        self.assertGreaterEqual(result["pulse_phase_started"], 1081)
        self.assertLess(result["command_phase_elapsed_seconds"], 60)
        self.assertEqual(180, result["intervals"][0]["preinspect"]["controls"]["yaw"])

    def test_unsupported_D_range_fails_before_spending_initial_aim(self):
        self.client.distance = 1e15
        result = self.run_cadence()
        self.assertEqual("failed", result["outcome"])
        self.assertEqual(0, self.client.aim_count)
        self.assertEqual(0, self.client.hold_count)

    def test_actual_retention_and_single_conditional_tail(self):
        result = self.run_cadence()
        self.assertEqual("three-pulses-completed", result["outcome"])
        for interval in result["intervals"]:
            screen = interval["screen"]
            self.assertAlmostEqual(4 * 1.5e14, screen["initial_tail_m"], delta=1)
            expected = screen["initial_tail_m"] + 1.5e14 * (screen["consumed_tick_envelope"] + screen["next_tick_envelope"])
            self.assertEqual(expected, screen["reserved_m"])
        for value in (0.97, 0.81, float("nan")):
            self.setUp()
            self.client.overrides[1] = {"retention": value}
            self.assertEqual("failed", self.run_cadence()["outcome"])
            self.assertEqual(0, self.client.aim_count)

    def test_postpulse_heading_drift_collects_both_coasts_before_bounded_refinement(self):
        # Change target direction without changing the camera.
        original = self.client.invoke
        def invoke(op, deadline):
            out = original(op, deadline)
            if len(self.client.calls) in (6, 7):
                out["text"] = out["text"].replace("false " + str(self.client.distance) + " 0 0", "false " + str(self.client.distance) + " " + str(self.client.distance/10) + " 0")
            return out
        self.client.invoke = invoke
        result = self.run_cadence()
        self.assertEqual("three-pulses-completed", result["outcome"])
        self.assertEqual(3, self.client.hold_count)
        self.assertEqual(3, self.client.aim_count)
        self.assertEqual(3, len(result["intervals"]))
        self.assertGreaterEqual(result["intervals"][0]["coast_b"]["at"] - result["intervals"][0]["coast_a"]["at"], 1)

    def test_aim_failure_or_terminal_never_enters_pulse_phase(self):
        self.client.failures[3] = RuntimeError("aim failed")
        result = self.run_cadence()
        self.assertEqual("failed", result["outcome"])
        self.assertEqual(0, self.client.hold_count)
        self.assertEqual(1, sum(c["operation"] == "aim" for c in self.client.calls))

    def test_refinement_wait_cap_and_consumed_reservation_never_reset(self):
        result = self.run_cadence()
        self.assertEqual("three-pulses-completed", result["outcome"])
        aims = [c for c in self.client.calls if c["operation"] == "aim"]
        self.assertEqual(3, len(aims))
        self.assertTrue(all(c["deadline"] - c["time"] <= 20 for c in aims[1:]))
        self.assertEqual([0, 4, 8], [i["screen"]["consumed_tick_envelope"] for i in result["intervals"]])
        self.assertEqual(12, result["consumed_tick_envelope"])
        self.assertEqual(1, len({i["screen"]["initial_tail_m"] for i in result["intervals"]}))

    def test_refinement_late_result_stops_without_second_hold_or_retry(self):
        self.client.delays[8] = 20.01
        result = self.run_cadence()
        self.assertEqual("failed", result["outcome"])
        self.assertEqual(1, self.client.hold_count)
        self.assertEqual(2, self.client.aim_count)
        self.assertEqual("stop", self.client.calls[-1]["operation"])

    def test_camera_reference_resets_only_after_successful_refinement(self):
        original = self.client.invoke
        def invoke(op, deadline):
            out = original(op, deadline)
            if len(self.client.calls) >= 9 and op not in ("aim", "stop"):
                out["text"] = out["text"].replace("180 0 0.01", "180.01 0 0.01")
            return out
        self.client.invoke = invoke
        result = self.run_cadence()
        self.assertEqual("three-pulses-completed", result["outcome"])
        self.assertEqual(180.01, result["intervals"][1]["preinspect"]["controls"]["yaw"])
        self.assertEqual(4, result["intervals"][1]["screen"]["consumed_tick_envelope"])

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
                    client.invoke("hold", 1025.5)
                response = client.invoke("stop", 1065)
            self.assertEqual(["hold", "stop"], calls)
            self.assertEqual([25, 2], processes[0].waits)
            self.assertTrue(processes[0].terminated)
            self.assertFalse(processes[0].killed)
            self.assertTrue(all(p.poll() is not None for p in processes))
            self.assertEqual(closed, response["closed"])
            recorded = [json.loads(s) for s in (client.logs / "controller.jsonl").read_text().splitlines()]
            self.assertEqual(2, sum(e["kind"] == "reaped" and e["reaped"] for e in recorded))

    def test_actual_adapter_rechecks_hold_reserve_after_slow_guard(self):
        with tempfile.TemporaryDirectory(dir=HERE) as temp:
            client = self.adapter(Path(temp))
            client.guard = lambda: self.clock.sleep(2)
            with patch.object(self.module.time, "monotonic", self.clock.monotonic):
                with self.assertRaisesRegex(AssertionError, "Less than25"):
                    client.invoke("hold", 1026)
            self.assertEqual([], client.children)
            self.assertEqual(1002, self.clock.now)

    def test_actual_refinement_timeout_reaps_child_and_never_repeats_aim(self):
        with tempfile.TemporaryDirectory(dir=HERE) as temp:
            client = self.adapter(Path(temp))
            client.aim_calls, client.target, client.work_end = 1, 1010, 1470
            (client.run / "state.json").write_text(json.dumps({"aim_invocations": 1, "aim_target": 1010}))
            processes = []
            class Child:
                pid, returncode = 11123, None
                def __init__(self):
                    self.terminated, self.waits = False, []
                def poll(self):
                    return self.returncode
                def wait(self, timeout):
                    self.waits.append(timeout)
                    if not self.terminated:
                        raise subprocess.TimeoutExpired("fake-aim", timeout)
                    self.returncode = -15
                    return -15
                def terminate(self):
                    self.terminated = True
                def kill(self):
                    raise AssertionError("Already reaped fake child must not be killed")
            def popen(argv, **_kwargs):
                self.assertEqual([str(self.module.FROZEN / "aim-client.py"), str(client.run), "1010"], argv[1:])
                child = Child(); processes.append(child); return child
            with patch.object(self.module.time, "monotonic", self.clock.monotonic), patch.object(subprocess, "Popen", side_effect=popen):
                with self.assertRaises(subprocess.TimeoutExpired):
                    client.invoke("aim", 1007)
            self.assertEqual(1, len(processes))
            self.assertEqual([7, 2], processes[0].waits)
            self.assertEqual(-15, processes[0].poll())
            self.assertEqual(2, client.aim_calls)  # consumed even though uncertain

    def test_actual_aim_result_requires_exact_saved_report_and_target(self):
        with tempfile.TemporaryDirectory(dir=HERE) as temp:
            client = self.adapter(Path(temp))
            client.aim_calls, client.target, client.work_end = 0, 1010, 1470
            statepath = client.run / "state.json"
            statepath.write_text(json.dumps({"aim_invocations": 0, "aim_target": None}))
            report = {"outcome": "aimed-at-one-published-observation", "target": 1010, "unreaped_client_pids": []}
            saved = client.run / "aim-01-1010"; saved.mkdir()
            (saved / "result.json").write_text(json.dumps({**report, "target": 1011}))
            class Child:
                pid, returncode = 11124, 0
                def poll(self): return 0
                def wait(self, timeout): return 0
            def popen(_argv, **kwargs):
                kwargs["stdout"].write(json.dumps(report).encode()); kwargs["stdout"].flush()
                return Child()
            with patch.object(self.module.time, "monotonic", self.clock.monotonic), patch.object(subprocess, "Popen", side_effect=popen):
                with self.assertRaisesRegex(AssertionError, "Saved aim/result mismatch"):
                    client.invoke("aim", 1185)
            self.assertEqual(1, client.aim_calls)
            self.assertTrue(all(c.poll() is not None for c in client.children))

    def test_refinement_camera_reset_does_not_allow_changed_retention_or_D(self):
        for change in ({"retention": .81}, {"D": 3e14}):
            self.setUp(); self.client.overrides[9] = change
            result = self.run_cadence()
            self.assertEqual("failed", result["outcome"])
            self.assertEqual(1, self.client.hold_count)
            self.assertEqual(2, self.client.aim_count)

    def test_operator_setup_and_poller_contract_without_import_or_execution(self):
        setup = ast.parse((HERE / "root-setup.py").read_text())
        plan = next(ast.literal_eval(n.value) for n in setup.body if isinstance(n, ast.Assign)
                    and any(isinstance(t, ast.Name) and t.id == "plan" for t in n.targets))
        actions = [(op, args) for _, op, args in plan]
        self.assertEqual(17, actions.count(("click", ["damping-down"])))
        self.assertEqual(1, actions.count(("click", ["thrust-down"])))
        self.assertEqual([( "look", ["200", "0"]), ("look", ["-200", "0"]),
                          ("look", ["0", "200"]), ("look", ["0", "-200"])],
                         [x for x in actions if x[0] == "look"])
        self.assertFalse(any(op == "hold" for op, _ in actions))
        tree = ast.parse((HERE / "root-formation-poller.py").read_text())
        fn = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == "observation_stop_reason")
        ns = {}; exec(compile(ast.Module(body=[fn], type_ignores=[]), "<pure-poller>", "exec"), ns)
        reason = ns["observation_stop_reason"]
        self.assertEqual("commitment-observed", reason(1, [1010], True))
        self.assertEqual("run-closed", reason(0, [1010], True))
        self.assertEqual("stored-ready-observed-no-selection", reason(0, [1010], False))
        self.assertIsNone(reason(0, [], False))

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
            if len(self.client.calls) == 6:
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
                    if len(self.client.calls) == 4:
                        result["text"] = result["text"].replace(old, new)
                    return result
                self.client.invoke = invoke
                self.assertEqual("failed", self.run_cadence()["outcome"])
                self.assertEqual(0, self.client.hold_count)


if __name__ == "__main__":
    unittest.main(verbosity=2)
