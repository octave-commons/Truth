"""Evidence integrity contracts; these tests do not simulate Primordia."""
import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("experiment", pathlib.Path(__file__).with_name("experiment.py"))
experiment = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(experiment)


class EvidenceContracts(unittest.TestCase):
    def test_plan_has_four_matched_seeds_and_active_costs(self):
        jobs = experiment.plan("pilot")
        smoke = next(j for j in jobs if j["arm"] == "smoke")
        self.assertLessEqual(experiment.tomllib.loads(smoke["config"])["telemetry"]["metrics_every"], smoke["epochs"])
        for arm in ("baseline", "high-upkeep", "no-energy"):
            self.assertEqual([2, 3, 4, 5], [j["seed"] for j in jobs if j["arm"] == arm])
        for j in jobs:
            if j["kind"] == "run":
                c = experiment.tomllib.loads(j["config"])
                self.assertTrue(c["energy"]["enabled"])
                self.assertTrue(c["matter"]["enabled"])
                self.assertEqual("hybrid", c["individual"]["interaction"])
                self.assertGreater(c["costs"]["CPY"], 0)
                self.assertNotIn("--events", j["argv"])

    def test_zero_checkpoints_is_not_valid_replay(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d)
            (p / "hashes.csv").write_text("epoch,hash\n")
            self.assertFalse(experiment.replay_valid(p, "replay OK: 0 hashes identical"))
            (p / "hashes.csv").write_text("epoch,hash\n10,abcdef\n")
            self.assertFalse(experiment.replay_valid(p, "replay OK: 0 hashes identical"))
            self.assertTrue(experiment.replay_valid(p, "replay OK: 1 hashes identical"))

    def test_resolved_pair_concat_and_missing_fields_rejected(self):
        c = experiment.tomllib.loads(experiment.config("baseline", 20, 11))
        experiment.validate_config(c, c)
        c["individual"]["interaction"] = "pair_concat"
        with self.assertRaises(ValueError):
            experiment.validate_config(c, c)
        with self.assertRaises((ValueError, KeyError)):
            experiment.validate_config({}, {})

    def test_uncertainty_does_not_turn_absence_into_zero(self):
        self.assertIsNone(experiment.wilson(0, 0))
        lo, hi = experiment.wilson(0, 4)
        self.assertAlmostEqual(0, lo)
        self.assertGreater(hi, 0.48)

    def test_sweep_is_bounded_and_not_a_matter_threshold(self):
        jobs = experiment.plan("sweep")
        self.assertEqual(36, sum(j["kind"] == "run" and j["arm"] != "smoke" for j in jobs))
        self.assertEqual({2, 8, 32}, {j["copy_cost"] for j in jobs if j["arm"] != "smoke" and j["kind"] == "run"})

    def test_missing_command_and_timeout_are_not_completed(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d)
            missing = experiment.command([str(p / "absent")], p, p / "missing.log", 1)
            self.assertEqual("blocked", missing["status"])
            timed = experiment.command([sys.executable, "-c", "import time; time.sleep(10)"], p, p / "timeout.log", 0.05)
            self.assertEqual("timeout", timed["status"])
            self.assertLess(timed["seconds"], 2)

    def test_blocked_preflight_preserves_all_planned_jobs_as_unknown(self):
        with tempfile.TemporaryDirectory() as d:
            out = pathlib.Path(d) / "campaign"
            proc = subprocess.run([sys.executable, str(pathlib.Path(experiment.__file__)), "run", "--out", str(out)], capture_output=True, text=True)
            self.assertEqual(2, proc.returncode)
            result = experiment.json.loads((out / "result.json").read_text())
            self.assertIsNone(result["observed_replicators"])
            self.assertIsNone(result["frequency"])
            rows = experiment.json.loads((out / "records.json").read_text())
            self.assertEqual(17, len(rows))
            self.assertTrue(all(r["status"] == "blocked" and not r["started"] and not r["validated"] for r in rows))


if __name__ == "__main__":
    unittest.main()
