import datetime, hashlib, json, os, signal, subprocess, sys, time
from pathlib import Path
root = Path.cwd()
base = root / ".ημ/diagnostics/life-account-kernel-green/coherence-and-retry"
mode = sys.argv[1]
assert mode in ("red", "green")
out = base / ("regression-red" if mode == "red" else "focused-green")
out.mkdir(exist_ok=True)
assert not (out / "command.json").exists()
expected = {
 "src/law/life_account.clj": "9de4ec6827ab9453fa54008e55780002e27ecde68f26d4f66b826c7bd0301810",
 "src/domain/life_account.clj": "ea01b902a2c2c776c2a593ed735fa493b1be6c67ab98db7e08afb9496c64db1c",
 "test/law/life_account_test.clj": "8a2aaa538039663e2dbc4bda81eacc7e20449e27fab5bc76fce346e45322888a",
 "test/domain/life_account_test.clj": "36a3bac05c70abe42d71135a38a5f610bace0549fb74d1ce741d1a01e32d17ec"}
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def live(): return {p: sha(root / p) for p in expected}
def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
def write(name, value):
 with (out / name).open("x") as f: json.dump(value, f, indent=2); f.write("\n")
assert live() == expected
inputs = {str(p.relative_to(root)): sha(p) for p in (base / "pre-fix").rglob("*") if p.is_file()}
if mode == "red":
 driver = out / "driver.clj"
 inputs[str(driver.relative_to(root))] = sha(driver)
 for p in (out / "test-inputs").rglob("*"):
  if p.is_file(): inputs[str(p.relative_to(root))] = sha(p)
 # -Sdeps paths replace project paths. The test alias adds current test/bench;
 # only old law/domain namespaces occur in pre-fix/src and the driver proves resolution.
 deps = '{:paths ["' + str((base / "pre-fix/src").relative_to(root)) + '" "src" "resources"] :aliases {:kernel-regression {:main-opts ["' + str(driver.relative_to(root)) + '"]}}}'
 argv = ["clojure", "-J-Xms256m", "-J-Xmx2g", "-Sdeps", deps, "-M:test:kernel-regression"]
else:
 argv = ["clojure", "-J-Xms256m", "-J-Xmx2g", "-M:test", "-n", "law.life-account-test", "-n", "domain.life-account-test"]
env = os.environ.copy(); env["JAVA_TOOL_OPTIONS"] = "-Xms256m -Xmx2g"
head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
record = {"argv": argv, "cwd": str(root), "head": head, "start": now(), "timeout_seconds": 180, "env_overrides": {"JAVA_TOOL_OPTIONS": env["JAVA_TOOL_OPTIONS"]}, "source_before": live(), "preserved_inputs": inputs, "wrapper_sha256": sha(Path(__file__))}
t0 = time.monotonic()
with (out / "stdout.log").open("xb") as stdout, (out / "stderr.log").open("xb") as stderr:
 proc = subprocess.Popen(argv, cwd=root, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
 record["pid"] = proc.pid
 try: record["proc_start_stat"] = Path(f"/proc/{proc.pid}/stat").read_text()
 except FileNotFoundError: record["proc_start_stat"] = None
 write("command.json", record)
 timed_out = False
 try: code = proc.wait(timeout=180)
 except subprocess.TimeoutExpired:
  timed_out = True
  os.killpg(proc.pid, signal.SIGTERM)
  try: code = proc.wait(timeout=5)
  except subprocess.TimeoutExpired:
   os.killpg(proc.pid, signal.SIGKILL); code = proc.wait(timeout=5)
record.update({"exit": code, "end": now(), "elapsed_seconds": time.monotonic() - t0, "timed_out": timed_out, "reaped": proc.poll() is not None, "pid_absent": not Path(f"/proc/{proc.pid}").exists(), "source_after": live(), "preserved_inputs_unchanged": all(sha(root / p) == v for p, v in inputs.items())})
record["sources_unchanged"] = record["source_before"] == record["source_after"]
write("result.json", record)
print(json.dumps({k: record[k] for k in ["pid", "exit", "end", "elapsed_seconds", "timed_out", "reaped", "pid_absent", "sources_unchanged", "preserved_inputs_unchanged"]}, indent=2))
print((out / "stdout.log").read_text()[-6000:])
print((out / "stderr.log").read_text())
assert record["reaped"] and record["pid_absent"] and record["sources_unchanged"] and record["preserved_inputs_unchanged"]
