"""Offline operator tests: extract pure code; never import or run an operator."""

import ast
from contextlib import ExitStack
from pathlib import Path
import unittest
from unittest.mock import patch


HERE = Path(__file__).resolve().parent


def source(name):
    return ast.parse((HERE / name).read_text(), filename=name)


def assigned(tree, name):
    matches = [node.value for node in tree.body if isinstance(node, ast.Assign)
               and any(isinstance(target, ast.Name) and target.id == name
                       for target in node.targets)]
    assert len(matches) == 1, name
    return matches[0]


class OperatorTests(unittest.TestCase):
    def setUp(self):
        self.blockers = ExitStack()
        self.addCleanup(self.blockers.close)
        self.mocks = [self.blockers.enter_context(patch(name, side_effect=AssertionError(
            "Process, network and native operations are forbidden in these tests")))
            for name in ("subprocess.Popen", "subprocess.run", "subprocess.call",
                         "subprocess.check_call", "subprocess.check_output",
                         "os.system", "os.fork", "os.execv", "os.execve",
                         "os.posix_spawn", "os.posix_spawnp", "socket.socket",
                         "ctypes.CDLL", "ctypes.PyDLL")]
        self.addCleanup(self.assert_no_runtime_calls)
        tree = source("root-formation-poller.py")
        nodes = [node for node in tree.body if isinstance(node, ast.FunctionDef)
                 and node.name == "observation_stop_reason"]
        self.assertEqual(len(nodes), 1)
        self.assertFalse(any(isinstance(node, ast.Call) for node in ast.walk(nodes[0])))
        namespace = {}
        exec(compile(ast.Module(body=nodes, type_ignores=[]), "<pure-poller-reason>", "exec"),
             namespace)
        self.reason = namespace["observation_stop_reason"]

    def assert_no_runtime_calls(self):
        for mocked in self.mocks:
            mocked.assert_not_called()

    def test_commitment_takes_priority_over_ready_and_closed(self):
        for closed in (False, True):
            self.assertEqual(self.reason(1, [1010], closed), "commitment-observed")

    def test_closed_takes_priority_over_ready(self):
        self.assertEqual(self.reason(0, [1010], True), "run-closed")

    def test_ready_alone_stops_without_selection(self):
        self.assertEqual(self.reason(0, [1010], False),
                         "stored-ready-observed-no-selection")

    def test_no_target_continues_observation(self):
        self.assertIsNone(self.reason(0, [], False))

    def test_empty_terminal_world_still_stops(self):
        self.assertEqual(self.reason(2, [], False), "commitment-observed")
        self.assertEqual(self.reason(0, [], True), "run-closed")

    def test_poller_uses_same_snapshot_commitment_and_current_closed_file(self):
        tree = source("root-formation-poller.py")
        calls = [node for node in ast.walk(tree) if isinstance(node, ast.Call)
                 and isinstance(node.func, ast.Name)
                 and node.func.id == "observation_stop_reason"]
        self.assertEqual(len(calls), 1)
        self.assertEqual(ast.unparse(calls[0]),
                         "observation_stop_reason(int(commit[0].split()[1]), ready, "
                         "(run / 'closed.json').exists())")
        stops = [node for node in ast.walk(tree) if isinstance(node, ast.If)
                 and isinstance(node.test, ast.Name) and node.test.id == "observed_stop"]
        self.assertEqual(len(stops), 1)
        self.assertTrue(any(isinstance(node, ast.Break) for node in stops[0].body))
        self.assertFalse(any(isinstance(node, ast.If) and isinstance(node.test, ast.Name)
                             and node.test.id == "ready" for node in ast.walk(tree)))

    def test_fresh_attempt_and_record_paths(self):
        for name, record, expected in (
            ("root-setup.py", "path", "attempt-02-calibration.json"),
            ("root-formation-poller.py", "recordpath", "attempt-02-formation-poller.json"),
        ):
            tree = source(name)
            self.assertEqual(ast.literal_eval(assigned(tree, "run").right), "runs/attempt-02")
            self.assertEqual(ast.literal_eval(assigned(tree, "logs").right), "attempt-02-clients")
            self.assertEqual(ast.literal_eval(assigned(tree, record).right), expected)
            self.assertNotIn("attempt-01", (HERE / name).read_text())

    def test_setup_reduces_cruise_three_times_before_lock(self):
        plan = ast.literal_eval(assigned(source("root-setup.py"), "plan"))
        self.assertEqual(len({step[0] for step in plan}), len(plan))
        cruise = next(i for i, step in enumerate(plan) if step[2] == ["cruise"])
        reductions = plan[cruise + 1:cruise + 4]
        self.assertEqual([(op, args) for _, op, args in reductions],
                         [("click", ["thrust-down"])] * 3)
        self.assertEqual(plan[cruise + 4][1:], ("click", ["spark"]))
        self.assertEqual(plan[cruise + 5][1:], ("tap", ["Tab"]))
        self.assertEqual(3e14 / 2 ** len(reductions), 3.75e13)
        self.assertIn("float(c[3]) == 37500000000000.0",
                      ast.unparse(source("root-setup.py")))

    def test_setup_preserves_four_separate_looks_and_final_inspection(self):
        plan = ast.literal_eval(assigned(source("root-setup.py"), "plan"))
        self.assertEqual([step[2] for step in plan if step[1] == "look"],
                         [["200", "0"], ["-200", "0"], ["0", "200"], ["0", "-200"]])
        self.assertEqual(plan[-1][1:], ("inspect", []))
        self.assertNotIn("hold", [step[1] for step in plan])
        self.assertNotIn("aim-start", [step[1] for step in plan])

    def test_poller_has_no_target_selection_or_gameplay_command(self):
        tree = source("root-formation-poller.py")
        command = next(node.value for node in ast.walk(tree) if isinstance(node, ast.Assign)
                       and any(isinstance(target, ast.Name) and target.id == "args"
                               for target in node.targets))
        self.assertEqual(len(command.elts), 4)
        self.assertEqual(ast.literal_eval(command.elts[2]), "inspect")
        record = assigned(tree, "record")
        selected = [(key, value) for key, value in zip(record.keys, record.values)
                    if isinstance(key, ast.Constant) and key.value == "selects_target"]
        self.assertEqual(len(selected), 1)
        self.assertIs(ast.literal_eval(selected[0][1]), False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
