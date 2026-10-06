"""Summarize closed native bin/bench output; never modifies a source checkout."""

import hashlib
import json
import pathlib
import re


HERE = pathlib.Path(__file__).resolve().parent
PATTERN = re.compile(
    r"^--- (.*?) ---\n  Mean:   (.*?)\n(?:  Std:.*?\n)?  Range:  (.*?)$", re.M
)


def milliseconds(text):
    value, unit = text.split()
    return float(value) * {"ns": 1e-6, "μs": 1e-3, "µs": 1e-3, "ms": 1, "s": 1000}[unit]


def read_run(name):
    start = json.loads((HERE / f"{name}-start.json").read_text())
    end = json.loads((HERE / f"{name}-end.json").read_text())
    raw = (HERE / f"{name}.log").read_bytes()
    assert end["exit"] == 0, (name, end)
    assert end["log_sha256"] == hashlib.sha256(raw).hexdigest()
    assert start["source"]["stdout"] == end["source"]["stdout"]
    text = raw.decode()
    assert "=== BENCHMARK COMPLETE ===" in text
    items = [{"label": m[1], "mean": m[2], "range": m[3]} for m in PATTERN.finditer(text)]
    assert items
    (HERE / f"{name}-summary.json").write_text(json.dumps(items, indent=2) + "\n")
    return start, end, items


before_start, before_end, before = read_run("before")
after_start, after_end, after = read_run("after")
assert before_start["hashes"] == after_start["hashes"]
assert before_start["environment"] == after_start["environment"]
assert before_start["cpu_affinity"] == after_start["cpu_affinity"]
assert [row["label"] for row in before] == [row["label"] for row in after]
rows = []
for index, (old, new) in enumerate(zip(before, after), 1):
    old_ms = milliseconds(old["mean"])
    new_ms = milliseconds(new["mean"])
    rows.append({"index": index, "label": old["label"], "before": old, "after": new,
                 "before_mean_ms": old_ms, "after_mean_ms": new_ms,
                 "delta_percent_from_rounded_means": 100 * (new_ms / old_ms - 1)})

comparison = {"before_source": before_start["source"]["stdout"].strip(),
              "after_source": after_start["source"]["stdout"].strip(),
              "before_seconds": before_end["elapsed_seconds"],
              "after_seconds": after_end["elapsed_seconds"], "measurements": rows}
(HERE / "benchmark-comparison.json").write_text(json.dumps(comparison, indent=2) + "\n")
selected = [r for r in rows if "tick-world" in r["label"] or "10 ticks" in r["label"]
            or "step-physics parallel (on world1" in r["label"]]
lines = ["# Body trails: isolated Phase 0 benchmark comparison", "",
         f"Before: `{comparison['before_source']}` in `truth-validation`.",
         f"After: `{comparison['after_source']}` in `truth-motion-trails`.", "",
         "Both runs executed the unchanged `bin/bench :phase0` under `/usr/bin/time -v`.",
         "Runner, dependency, benchmark-definition hashes, selected JVM environment and",
         "CPU affinity match. Both report OpenJDK 21.0.12.1, 22 processors, and a 6 GiB",
         "maximum heap. Each process exited 0 and was reaped before the next load began.", "",
         "Root stopped other owned simulation, native, and validation loads for each",
         "measurement. Retained native Java PID 2372912 remained suspended. Unrelated",
         "desktop and host services were not stopped; their command-name snapshots are",
         "preserved in the start/end metadata. This is controlled owned concurrency,",
         "not a claim of a dedicated or otherwise idle machine.", "",
         "| Case | Before mean | After mean | Approx. change | Before reported range | After reported range |",
         "|---|---:|---:|---:|---|---|"]
for row in selected:
    lines.append(f"| {row['label'].strip()} | {row['before']['mean']} | {row['after']['mean']} | "
                 f"{row['delta_percent_from_rounded_means']:+.1f}% | {row['before']['range']} | {row['after']['range']} |")
lines += ["", "Percentages use the benchmark's rounded printed means. Reported ranges are",
          "Criterium's lower/upper quantile outputs, not confidence intervals. One",
          "before/after pair does not establish a general speedup or a narrow regression",
          "bound; full raw output and all case comparisons are retained.", "",
          "The suite repeatedly ticks fresh 100/500/1000-gas genesis worlds and includes",
          "a ten-tick sequence. It measures the newly added hot-path owner in early",
          "nebula conditions, not mature worlds with 64 retained samples on many bodies",
          "or GPU line rendering. Bounded-history/segment behavior has focused tests;",
          "native visible fading, mature-scene performance, and gameplay acceptance",
          "remain separate evidence. Initial one-sample subsystem profiles are not a",
          "substitute for the repeated whole-tick measurements.", "",
          "`before-start.json`/`after-start.json` preserve command, environment, hashes",
          "and source state; the corresponding end files preserve elapsed time, exit",
          "status and raw-output hash. `*-timing.txt` contains complete resource timing.",
          "`compare-benchmarks.py` reproduces this derived report after both logs close.", ""]
(HERE / "benchmark-comparison.md").write_text("\n".join(lines))
files = [f"{name}{suffix}" for name in ["before", "after"]
         for suffix in [".log", "-start.json", "-end.json", "-timing.txt", "-summary.json"]]
files += ["compare-benchmarks.py", "benchmark-comparison.json", "benchmark-comparison.md"]
files += ["benchmark-CLOSED-FILES.txt", "benchmark-SHA256SUMS"]
(HERE / "benchmark-CLOSED-FILES.txt").write_text("".join(
    f".ημ/diagnostics/body-trails/{name}\n" for name in files))
(HERE / "benchmark-SHA256SUMS").write_text("".join(
    f"{hashlib.sha256((HERE/name).read_bytes()).hexdigest()}  {name}\n"
    for name in files if name != "benchmark-SHA256SUMS"))
print(json.dumps({"measurements": len(rows), "selected": selected}, indent=2))
