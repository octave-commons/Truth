#!/usr/bin/env python3
"""Run the root-authorized sequential correctness checks with source guards."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import subprocess


ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
EXPECTED_HEAD = "8487159bfcf6f7d805018e270174c9f3cd35e8ea"
ENV_OVERRIDES = {
    "JAVA_TOOL_OPTIONS": "-Xms256m -Xmx2g",
    "_JAVA_OPTIONS": "-Xms256m -Xmx2g",
}
SOURCE_SCOPE = [
    "src", "test", "test-native", "dev", "bench", "bin", "resources",
    "deps.edn", ".github", ".clj-kondo", ".lsp", ".splint.edn",
    ".cljfmt.edn", ".jscpd.json", "AGENTS.md", "PROCESS.md",
    "README.md", "docs/demo/README.md", "openhax.kanban.edn",
]
STAGES = [
    ("demo-test", ["clojure", "-J-Xms256m", "-J-Xmx2g", "-M:demo-test"]),
    ("full-suite", ["clojure", "-J-Xms256m", "-J-Xmx2g", "-M:test"]),
    ("strict", ["bin/analyze", "--strict"]),
    ("dev-kondo", ["clj-kondo", "--lint", "dev/demo.clj",
                   "dev/demo_client.clj", "dev/demo_test.clj"]),
    ("demo-capture-syntax", ["bash", "-n", "bin/demo-capture"]),
    ("formation-capture-syntax", ["bash", "-n", "bin/formation-capture"]),
]


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    with path.open("x") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


paths = git("ls-files", "-z", "--", *SOURCE_SCOPE).decode().split("\0")
paths = sorted(path for path in paths if path)
sources = {path: sha(ROOT / path) for path in paths}


def guard():
    head = git("rev-parse", "HEAD").decode().strip()
    assert head == EXPECTED_HEAD, ("HEAD changed", head)
    assert not git("diff", "--name-only", "HEAD", "--", *SOURCE_SCOPE), "dirty source"
    current = {path: sha(ROOT / path) for path in paths}
    assert current == sources, "source bytes changed"
    return {"head": head, "source_hashes_match": True,
            "tracked_source_count": len(current), "source_diff_empty": True}


save(OUT / "source-before.json", {
    "recorded_at": now(), "guard": guard(), "cwd": str(ROOT),
    "parents": git("rev-list", "--parents", "-n", "1", "HEAD").decode().strip(),
    "source_sha256": sources, "environment_overrides": ENV_OVERRIDES,
    "native_overlap": "Root-owned PID131581 remains on unchanged source9c5c889; no live calls or control by this runner.",
    "scope": "Correctness/static checks only; no performance or native gameplay claim.",
})
env = dict(os.environ, **ENV_OVERRIDES)
outcomes = []
for name, argv in STAGES:
    stage = OUT / name
    stage.mkdir()
    before = guard()
    started = now()
    with (stage / "stdout.log").open("xb") as stdout, (stage / "stderr.log").open("xb") as stderr:
        proc = subprocess.Popen(argv, cwd=ROOT, env=env, stdout=stdout, stderr=stderr)
        save(stage / "command.json", {
            "command": argv, "cwd": str(ROOT), "started_at": started,
            "pid": proc.pid, "environment_overrides": ENV_OVERRIDES,
            "before": before,
        })
        print(f"START {name} PID {proc.pid} {started}", flush=True)
        status = proc.wait()
    result = {
        "name": name, "pid": proc.pid, "exit_code": status,
        "reaped": True, "finished_at": now(),
        "stdout_sha256": sha(stage / "stdout.log"),
        "stderr_sha256": sha(stage / "stderr.log"),
    }
    # Preserve the process outcome even if a subsequent source guard refuses.
    save(stage / "result.json", result)
    outcomes.append(result)
    print(f"END {name} exit {status} reaped {result['finished_at']}", flush=True)
    save(stage / "source-after.json", guard())
    if status:
        save(OUT / "sequence-result.json", {
            "complete": False, "blocked_at": name, "outcomes": outcomes,
            "not_run": [entry[0] for entry in STAGES[len(outcomes):]],
        })
        raise SystemExit(status)
save(OUT / "sequence-result.json", {
    "complete": True, "outcomes": outcomes, "final_guard": guard(),
    "runner_sha256": sha(Path(__file__).resolve()),
})
