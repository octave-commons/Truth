#!/usr/bin/env python3
"""Read closed diagnostic data only; create new immutable audit reports.

Babashka parses EDN as data. No project namespaces, driver imports, native
service contact, process signals, tests or Git operations are performed.
"""
from pathlib import Path
import collections
import datetime
import hashlib
import json
import re
import subprocess

HERE = Path(__file__).resolve().parent
WT = HERE.parents[2]
RUN = HERE / "runs/attempt-01"
CLIENTS = HERE / "attempt-01-clients"
OUTPUTS = [HERE / ("attempt-01-audit." + ext) for ext in ("json", "md")]
assert not any(p.exists() for p in OUTPUTS), "Closed audits cannot be overwritten"
sha = lambda b: hashlib.sha256(b).hexdigest()
readj = lambda p: json.loads(p.read_text())
seconds = lambda a, b: (datetime.datetime.fromisoformat(b) - datetime.datetime.fromisoformat(a)).total_seconds()
inputs = sorted([p for folder in (RUN, CLIENTS) for p in folder.rglob("*") if p.is_file()] +
                [HERE / "attempt-01-formation-poller.json", HERE / "attempt-01-calibration.json",
                 HERE / "root-attempt01-calibration-check.json", HERE / "root-execution-release.json"])
assert all(not p.is_symlink() for p in inputs)
fingerprint = lambda p: {"bytes": p.stat().st_size, "sha256": sha(p.read_bytes())}
before = {str(p.relative_to(WT)): fingerprint(p) for p in inputs}
closed = readj(RUN / "closed.json")
assert closed == readj(RUN / "state.json")
assert closed["outcome"] == "no-target-deadline"
assert closed["holds"] == closed["aim_invocations"] == 0
assert closed["successful_admission"] is None and closed["aim_target"] is None
assert not closed["cleanup_errors"] and not closed["unreaped_children"]
assert not closed["budget_exceeded"] and not closed["total_budget_exceeded"]
assert not closed["key_pending"] and not closed["key_release_uncertain"]
assert not closed["mouse_pending"] and not closed["mouse_release_uncertain"]
ops = [json.loads(x) for x in (RUN / "operations.jsonl").read_text().splitlines()]
bykind = {k: [o for o in ops if o["kind"] == k] for k in {o["kind"] for o in ops}}
expr = """(require '[clojure.edn :as edn] '[clojure.string :as str] '[cheshire.core :as json])
(println (json/generate-string (mapv (fn [p] (mapv edn/read-string (str/split-lines (slurp p)))) *command-line-args*)))"""
parsed = subprocess.run(["/usr/local/bin/bb", "-e", expr, str(RUN / "snapshot-requests.edn"), str(RUN / "snapshot-messages.edn")],
                        capture_output=True, timeout=30, check=True)
assert not parsed.stderr
requests, messages = json.loads(parsed.stdout)
starts = [r for r in requests if r["event"] == "request-start"]
ends = [r for r in requests if r["event"] == "request-finish"]
raw = (RUN / "snapshot-messages.edn").read_bytes()
lines = raw.splitlines(keepends=True)
consumed = {r["id"]: r for r in bykind["snapshot-consumed"]}
assert len(starts) == len(ends) == len(consumed) == 132
assert [s["id"] for s in starts] == [f"{i:04}" for i in range(1, 133)]
assert [r["id"] for r in bykind["snapshot-request"]] == [r["id"] for r in bykind["snapshot-result"]] == list(consumed)
pos = idx = 0
frames = []
for s, e in zip(starts, ends):
    count = e["messages"]
    batch = messages[idx:idx + count]
    chunk = b"".join(lines[idx:idx + count])
    assert s["id"] == e["id"] and e["outcome"] == "ok"
    assert s["byte-start"] == e["byte-start"] == pos
    assert len(chunk) == e["raw-bytes"] == e["byte-end"] - pos
    assert raw[pos:e["byte-end"]] == chunk
    assert all(m["id"] == s["id"] and not m.get("err") and not m.get("ex") and not m.get("root-ex") for m in batch)
    assert batch[-1].get("status") == ["done"] and sum("value" in m for m in batch) == 1
    assert all(set(m.get("status", [])) <= {"done"} for m in batch)
    out = "".join(m.get("out", "") + (m["value"] + "\n" if "value" in m else "") for m in batch)
    assert len(out.encode()) == consumed[s["id"]]["stdout_utf8_bytes"]
    assert re.findall(r"^TRUTH_APPROACH_ID (\d+) (\d+)$", out, re.M) == [(str(closed["world"]), str(closed["window"]))]
    commit = re.findall(r"^TRUTH_COMMITMENT (\d+) (\d+)(.*)$", out, re.M)
    assert len(commit) == 1 and commit[0][1] == "0" and not commit[0][2].strip()
    flight = next(x.split()[1:] for x in out.splitlines() if x.startswith("TRUTH_FLIGHT "))
    assert flight[10:] == ["nil"] * 3
    ready = []
    for row in out.splitlines():
        if row.startswith("TRUTH_COMMIT_TARGET "):
            fields = row.split()[1:]
            if fields[2:4] == ["true", "true"]:
                ready.append(fields[0])
    frames.append({"id": s["id"], "tick": int(flight[0]), "consumed_at": consumed[s["id"]]["at"],
                   "byte_start": pos, "byte_end": e["byte-end"], "messages": count, "ready_ids": ready})
    pos = e["byte-end"]
    idx += count
assert pos == len(raw) and idx == len(messages) == len(lines)
assert requests[0]["event"] == "connection-open" and requests[-1]["event"] == "connection-close"
assert requests[-1]["completed"] == 132 and requests[-1]["byte-end"] == len(raw)
assert len(raw) < 128 * 1024 * 1024 and max(e["raw-bytes"] for e in ends) < 4 * 1024 * 1024
assert out.encode() == (RUN / "snapshot-current.stdout").read_bytes() == (CLIENTS / "poll-20-snapshot.txt").read_bytes()
spawned = {r["label"]: r for r in bykind["spawn"]}
reaped = {r["label"]: r for r in bykind["reaped"]}
assert set(spawned) == set(reaped) and len(spawned) == 81
assert all(spawned[k]["identity"]["pid"] == reaped[k]["pid"] for k in spawned)
outer = [readj(p) for p in sorted(CLIENTS.glob("*.json"))]
assert all(x["state"] == "reaped" and x["returncode"] == 0 for x in outer)
result_files = sorted((RUN / "results").glob("*.json"))
assert len(result_files) == len(bykind["request"]) == 53
assert {x["request"]["id"] for x in bykind["request"]} == {p.stem for p in result_files}
assert all(readj(p)["ok"] for p in result_files)
boot = Path("/proc/sys/kernel/random/boot_id").read_text().strip()
absence = {}
for key in ("supervisor", "xvfb", "app", "snapshot_client"):
    value = closed[key]
    assert value["boot"] == boot
    absence[key] = {**value, "proc_exists_at_audit": Path("/proc", str(value["pid"])).exists()}
assert not any(x["proc_exists_at_audit"] for x in absence.values())
source = readj(RUN / "source-hashes.json")
prep = readj(RUN / "preparation-hashes.json")
assert len(source) == 186 and all(sha((WT / name).read_bytes()) == digest for name, digest in source.items())
assert len(prep) == 11 and all(sha((HERE / name).read_bytes()) == digest for name, digest in prep.items())
manifest = (HERE / "SHA256SUMS").read_text().splitlines()
for row in manifest:
    digest, name = row.split(maxsplit=1)
    assert sha((HERE / name).read_bytes()) == digest
poller = readj(HERE / "attempt-01-formation-poller.json")
polls = poller["observations"]
assert len(polls) == 21 and poller["reason"] == "stored-ready-observed-no-selection"
assert not poller["selects_target"] and polls[-1]["ready_ids"] == ["1010", "1011", "1012"]
assert all(not p["ready_ids"] for p in polls[:-1])
assert frames[-1]["ready_ids"] == polls[-1]["ready_ids"] and frames[-1]["tick"] == 4145
pollgaps = [seconds(a["at"], b["at"]) for a, b in zip(polls, polls[1:])]
cutoff = bykind["no-target-cutoff"]
assert len(cutoff) == 1 and cutoff[0]["elapsed_seconds"] >= 960
cal = readj(HERE / "attempt-01-calibration.json")
assert cal["state"] == "passed" and cal["steps"][-1]["retention"] == .8
assert cal["steps"][-1]["controls"][3] == "1.5E14"
report = {"schema": "truth-damped-attempt01-offline-audit-v1", "at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
          "outcome": "PASS-evidence-integrity; missed operator handoff before admission deadline; flight unmeasured",
          "scope": "Closed files plus read-only /proc absence; no native contact, signals, game namespaces, tests or Git",
          "closed": closed, "journal": {"reads": len(starts), "messages": len(messages), "utf8_bytes": len(raw),
          "sha256": sha(raw), "exact_contiguous_spans_verified": True, "maximum_request_bytes": max(e["raw-bytes"] for e in ends),
          "all_global_commitment_zero": True, "all_thrust_nil": True, "first": frames[0], "last": frames[-1]},
          "operations": {"events": len(ops), "requests": len(result_files), "request_counts": dict(collections.Counter(x["request"]["operation"] for x in bykind["request"])),
          "all_requests_ok": True, "spawned_and_reaped": len(spawned), "child_exit_codes": dict(collections.Counter(str(r["returncode"]) for r in reaped.values())),
          "outer_clients_reaped_exit0": len(outer)}, "owned_process_absence": absence,
          "poller": {"count": len(polls), "last_no_ready": polls[-2], "first_ready": polls[-1], "ended_at": poller["ended_at"],
          "start_gap_min_seconds": min(pollgaps), "start_gap_max_seconds": max(pollgaps), "selected_target": False,
          "ready_read_elapsed_seconds": polls[-1]["elapsed"], "remaining_admission_seconds_at_saved_read": 960 - polls[-1]["elapsed"],
          "poller_close_to_supervisor_cutoff_seconds": seconds(poller["ended_at"], cutoff[0]["at"])},
          "cutoff": cutoff[0], "calibration": {"outcome": cal["state"], "final_retention": .8, "final_D": 1.5e14, "looks": 4},
          "source_checks": {"production_hashes": len(source), "preparation_pins": len(prep), "manifest_hashes": len(manifest), "all_equal": True},
          "operator_report": {"poller_tool": 77820, "poller_returncode": 0, "root_resumed_at_UTC_approx": "2026-10-07T12:52:51Z", "evidence_tier": "parent agent report; not a saved run timestamp"},
          "limits": ["Ready bodies were observed; no-target means no supervisor-admitted target, not no planet or no ready body.",
                     "No post-poll snapshot exists; exact later planet readiness, tick and geometry at closure are unobserved.",
                     "No aim, thrust hold, physical approach, binding, commitment, sculpt, embodiment or Gate was measured.",
                     "This audit does not establish why the operator resumed late or predict a future flight outcome.",
                     "PID absence is an audit-time observation; saved start/boot identities and reap receipts supply ownership provenance."],
          "inputs": before}
assert before == {str(p.relative_to(WT)): fingerprint(p) for p in inputs}
OUTPUTS[0].write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
OUTPUTS[1].write_text(f"""# Closed damped attempt01: ready observation without admission

Evidence integrity passes. The flight trial did not begin: the supervisor closed at the original no-target admission deadline with zero aims and zero holds. This is a missed operator handoff, not a negative flight or damping result.

- The read-only poller made21 reads at {min(pollgaps):.6f}–{max(pollgaps):.6f}s start intervals. Tick3990 had no ready candidate; tick4145 observed stored/current-ready1010,1011,1012 with global commitment0. Its final saved read was at elapsed{polls[-1]['elapsed']:.6f}s, leaving {960-polls[-1]['elapsed']:.6f}s before admission cutoff. The poller closed at {poller['ended_at']} without selection.
- Supervisor cutoff was {cutoff[0]['at']} at elapsed{cutoff[0]['elapsed_seconds']:.6f}s. Cleanup completed {closed['ended_at']} at elapsed{closed['elapsed_seconds']:.6f}s. No successful-admission marker or selected target exists. `no-target-deadline` describes absent admission despite observed ready bodies.
- The root reports returning around12:52:51UTC, after closure. That report is distinct from saved runtime timestamps. No later snapshot establishes the planets' state at cutoff, and this audit does not infer why the operator handoff was late.
- {len(starts)} reads, {len(messages)} decoded messages and {len(raw):,} UTF-8 journal bytes have exact contiguous spans, matching request/result/consume records and a closed persistent connection. Same world{closed['world']}/window{closed['window']} throughout; sampled thrust is always nil and global commitment always0.
- All53 runner requests succeeded. Setup acknowledged17 damping decrements to0.8 at D1.5e14; four ±200 calibration looks returned to baseline and one frame was captured. No aim/hold request occurred.
- All81 child spawns have matching reap receipts; {len(outer)} outer clients have exit0/reaped records. Cleanup errors, pending/uncertain input flags and unreaped lists are empty. All four owned PID paths were independently absent under the recorded boot; no process was contacted or signaled.
- All186 production hashes,11 preparation pins and68 frozen preparation manifest rows still match. Original run/client inputs were hashed before and after and remained byte-identical.

The next useful operational correction is to reserve the observed admission window for an explicit root handoff. No driver, deadline, target policy or production change is justified by this run alone. There is no physical approach, binding, commitment, sculpt, embodiment, Gate or FPS claim.

Exact records, input hashes and limits are in [the JSON audit](attempt-01-audit.json). The audit script only reads closed files and process-presence metadata; its sole subprocess parses EDN data with Babashka.
""".replace("\n-", "\n\n- "))
print(json.dumps({"json_sha256": sha(OUTPUTS[0].read_bytes()), "md_sha256": sha(OUTPUTS[1].read_bytes()),
                  "inputs": len(inputs), "journal": report["journal"], "poller": report["poller"], "operations": report["operations"]}))
