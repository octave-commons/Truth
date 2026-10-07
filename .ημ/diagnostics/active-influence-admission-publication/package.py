"""Create once, verify, or extract exact admission evidence; no project execution."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import hashlib
import json
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
RAW = ROOT / ".ημ/diagnostics/active-influence-admission"
HEAD = "e77b3255212945602b96e1451347cbf9bc966676"
BASE = "b395c4049718f7ce015ddf25fc0a192d97821373"
ROOT_RESULT = "9eeedc936a0a9a377f2b32bbb81f32c00552d5923176bf612e65bdcc4696b320"
PACKAGE = ["package.py", "evidence.jsonl", "MAP.json", "STAGING.txt", "GENERATION.json"]
DIRECT = [
    "docs/designs/player-active-influence.md",
    "kanban/tasks/remove-passive-halo-invert-influence.md",
    "kanban/tasks/.events/ledger.edn",
    ".ημ/receipts.edn",
    ".ημ/session-mycology/ledger.md",
    ".ημ/diagnostics/active-influence-admission/root-result.json",
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def rel(path):
    return str(path.relative_to(ROOT))


def info(path):
    data = path.read_bytes()
    return {"path": rel(path), "bytes": len(data), "sha256": sha(data)}


def load(path):
    return json.loads(path.read_bytes())


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def create(path, data):
    with path.open("xb") as stream:
        stream.write(data)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def raw_paths():
    originals = [p for p in RAW.iterdir() if p.is_file() and p.name != "root-result.json"]
    originals += [p for p in (HERE / "inputs").iterdir() if p.is_file()]
    return sorted(originals, key=rel)


def safe_path(value):
    path = Path(value)
    assert not path.is_absolute() and ".." not in path.parts and str(path) == value
    return path


def verify_admission():
    assert sha((RAW / "root-result.json").read_bytes()) == ROOT_RESULT
    result = load(RAW / "root-result.json")
    assert len(result["protected_files"]) == 301
    for name, expected in result["protected_files"].items():
        assert sha((ROOT / name).read_bytes()) == expected, name
    initial = load(HERE / "inputs/before-publication.json")
    for name, row in initial["pins"].items():
        if name not in [".ημ/receipts.edn", ".ημ/session-mycology/ledger.md"]:
            assert info(ROOT / name) == {"path": name, **row}, name
    before_design = (HERE / "inputs/design-before-e77b325.md").read_bytes()
    assert before_design == git("show", HEAD + ":docs/designs/player-active-influence.md")
    current_design = (ROOT / "docs/designs/player-active-influence.md").read_bytes()
    # The tuning, consumer scope and acceptance text are preserved, not re-reviewed tuning.
    marker = b"Grounding and its limits:"
    end = b"## 6. Size and admission"
    assert before_design.split(marker, 1)[1].split(end, 1)[0] == current_design.split(marker, 1)[1].split(end, 1)[0]
    audit = load(RAW / "runtime-consumption-audit.json")
    before = json.loads(audit["read_task"]["stdout"])
    final_command = load(RAW / "admission-readback.json")
    assert final_command["exit"] == 0
    final = json.loads(final_command["stdout"])
    assert final["frontmatter"] == result["frontmatter"]
    assert {k: final["frontmatter"][k] for k in ["status", "points", "estimate", "design"]} == {
        "status": "in_progress", "points": "5", "estimate": "5",
        "design": "docs/designs/player-active-influence.md",
    }
    assert before["sections"][0] == final["sections"][0]
    assert final["sections"][1]["content"].startswith(before["sections"][1]["content"])
    move = load(RAW / "admission-move.json")
    assert move["exit"] == 0 and move["stderr"] == ""
    assert move["stdout"] == "moved remove-passive-halo-invert-influence: todo -> in_progress\n"
    for name in ["metadata.json", "metadata-readback.json", "admission-comment.json",
                 "admission-description.json"]:
        command = load(RAW / name)
        assert command["exit"] == 0 and command["stderr"] == ""
        json.loads(command["stdout"])
    previous_ledger = (RAW / "ledger-before.edn").read_bytes()
    current_ledger = (ROOT / "kanban/tasks/.events/ledger.edn").read_bytes()
    assert current_ledger.startswith(previous_ledger)
    appended_lines = len(current_ledger[len(previous_ledger):].splitlines())
    assert appended_lines == 6
    for row in load(HERE / "inputs/ledger-appends.json")["proof"]:
        data = (ROOT / row["path"]).read_bytes()
        assert len(data) == row["after_bytes"] and sha(data) == row["after_sha256"]
        assert sha(data[:row["before_bytes"]]) == row["before_sha256"]
    return {"protected_source_test_config_pins": 301, "body_preserved": True,
            "comment_prefix_preserved": True, "board_ledger_prefix_preserved": True,
            "board_ledger_appended_lines": appended_lines, "final_frontmatter": final["frontmatter"],
            "move_exit": 0, "move_stdout_format": "plain text; deliberately not JSON-decoded",
            "outer_decoder_failure": result["root_error_preserved"],
            "core_design_scope_and_acceptance_bytes_preserved": True,
            "runtime_test_build_execution_by_package": False}


def verify_archive():
    mapping = load(HERE / "MAP.json")
    assert info(HERE / "evidence.jsonl") == mapping["archive"]
    lines = (HERE / "evidence.jsonl").read_bytes().splitlines(keepends=True)
    assert len(lines) == len(mapping["packed"])
    offset = 0
    records = []
    for line, row in zip(lines, mapping["packed"]):
        decoded = json.loads(line)
        data = decoded["text"].encode("utf-8")
        safe_path(decoded["path"])
        assert row["path"] == decoded["path"]
        assert len(data) == row["bytes"] == decoded["bytes"]
        assert sha(data) == row["sha256"] == decoded["sha256"]
        assert row["offset"] == offset and row["line_bytes"] == len(line)
        records.append((decoded["path"], data))
        offset += len(line)
    assert [name for name, _ in records] == mapping["packed_paths"]
    assert len(set(mapping["packed_paths"])) == len(records)
    assert sum(len(data) for _, data in records) == mapping["decoded_bytes"]
    for row in mapping["package_files_before_MAP"]:
        assert info(ROOT / row["path"]) == row
    assert (HERE / "STAGING.txt").read_text().splitlines() == mapping["staging_paths"]
    return mapping, records


def verify_originals():
    mapping, records = verify_archive()
    assert [rel(p) for p in raw_paths()] == mapping["packed_paths"]
    for name, data in records:
        assert (ROOT / name).read_bytes() == data, name
    for row in mapping["direct_companions"]:
        assert info(ROOT / row["path"]) == row, row["path"]
    verify_admission()
    for row in mapping["previous_checkpoint_inventory"]:
        data = git("show", HEAD + ":" + row["path"])
        assert sha(data) == row["sha256"] and len(data) == row["bytes"]
        if row["path"] not in DIRECT:
            assert (ROOT / row["path"]).read_bytes() == data
    return mapping


def build():
    assert git("rev-parse", "HEAD").decode().strip() == HEAD
    assert all(not (HERE / name).exists() for name in PACKAGE if name != "package.py")
    verified = verify_admission()
    originals = raw_paths()
    archive = bytearray()
    entries = []
    for path in originals:
        data = path.read_bytes()
        text = data.decode("utf-8")
        assert text.encode("utf-8") == data
        line = (json.dumps({**info(path), "text": text}, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
        entries.append({**info(path), "line": len(entries) + 1, "offset": len(archive), "line_bytes": len(line)})
        archive.extend(line)
    previous = [p.decode() for p in git("diff", "--no-renames", "--name-only", "-z", BASE, HEAD).split(b"\0") if p]
    assert len(previous) == 10
    previous_rows = []
    for name in previous:
        data = git("show", HEAD + ":" + name)
        previous_rows.append({"path": name, "bytes": len(data), "sha256": sha(data)})
        if name not in DIRECT:
            assert data == (ROOT / name).read_bytes()
    stage = sorted(DIRECT + [rel(HERE / name) for name in PACKAGE])
    cumulative = sorted(set(previous) | set(stage))
    assert len(stage) == 11 and len(cumulative) == 16
    create(HERE / "evidence.jsonl", bytes(archive))
    create(HERE / "STAGING.txt", ("\n".join(stage) + "\n").encode())
    mapping = {
        "status": "ADMISSION_ONLY_DRAFT; ROOT_REVIEW_AND_PUBLICATION_PENDING",
        "at": datetime.now(timezone.utc).isoformat(), "base": BASE, "reviewed_planning_head": HEAD,
        "format": "Lossless UTF-8 JSONL with original paths/bytes/SHA256/line offsets; no omission or normalization.",
        "selection": "All12 original admission files:11 packed and root-result.json direct. All publication inputs packed. Source/card/ledgers remain direct; all original local files retained.",
        "archive": info(HERE / "evidence.jsonl"), "packed": entries,
        "packed_paths": [rel(p) for p in originals], "packed_count": len(entries),
        "decoded_bytes": sum(p.stat().st_size for p in originals),
        "empty_files": sum(p.stat().st_size == 0 for p in originals),
        "admission_direct_original": info(RAW / "root-result.json"),
        "direct_companions": [info(ROOT / name) for name in DIRECT],
        "verified_facts": verified,
        "previous_checkpoint_inventory": previous_rows,
        "previous_cumulative_paths": previous, "staging_paths": stage, "cumulative_paths": cumulative,
        "capacity": {"previous_no_rename_paths": 10, "proposed_no_rename_paths": 16,
                     "hosted_successor_included_count": "unobserved; no publication or review request here"},
        "generation": {"producer": info(HERE / "package.py"), "argv": sys.argv,
                       "initial_state": "Four outputs absent; exclusive creation. Producer saved before this initial build.",
                       "result": "GENERATION.json separately captures actual generation/verification commands after this immutable MAP; no circular self-hash or invented historical command."},
        "replay": {"verify": ["python3", rel(HERE / "package.py")],
                   "live_originals": ["python3", rel(HERE / "package.py"), "--verify-originals"],
                   "extract": ["python3", rel(HERE / "package.py"), "--extract", "/ABSOLUTE/NEW/DIRECTORY"],
                   "limits": "Read-only verification does not invoke Rheos, project source or runtime. Extraction creates only packed original paths in a fresh directory; direct companions are already published. Never execute extracted evidence implicitly."},
        "package_files_before_MAP": [info(HERE / name) for name in ["package.py", "evidence.jsonl", "STAGING.txt"]],
        "limits": ["Admission permits reviewed RED authoring, not runtime execution or completed acceptance.",
                   "Original move exit0/plain stdout is preserved. Outer decoder error is recorded by root; no missing traceback is fabricated and no move retry occurred.",
                   "No tests, benchmark, JVM, native, merge, index/commit/push, board mutation or hosted request is performed by this producer.",
                   "Later source or ledger edits invalidate exact live-original comparison; retain this immutable generation."]}
    create(HERE / "MAP.json", encode(mapping))
    verify_originals()
    return mapping


def extract(destination):
    mapping, records = verify_archive()
    target = Path(destination)
    assert target.is_absolute() and not target.exists()
    target.mkdir(parents=True, exist_ok=False)
    for name, data in records:
        path = target / safe_path(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        create(path, data)
        assert path.read_bytes() == data
    return mapping


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--build", action="store_true")
    modes.add_argument("--verify-originals", action="store_true")
    modes.add_argument("--extract")
    args = parser.parse_args()
    if args.build:
        mapping = build()
    elif args.verify_originals:
        mapping = verify_originals()
    elif args.extract:
        mapping = extract(args.extract)
    else:
        mapping, _ = verify_archive()
    print(json.dumps({"verified": True, "mode": vars(args), "packed": mapping["packed_count"],
                      "decoded_bytes": mapping["decoded_bytes"], "archive_bytes": mapping["archive"]["bytes"],
                      "empty_files": mapping["empty_files"], "direct_companions": len(DIRECT),
                      "staging_paths": len(mapping["staging_paths"]), "cumulative_paths": len(mapping["cumulative_paths"]),
                      "MAP_sha256": sha((HERE / "MAP.json").read_bytes())}, indent=2))


if __name__ == "__main__":
    main()
