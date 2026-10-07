"""Losslessly group historical text evidence; never remove or edit originals."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
BASE = "b395c4049718f7ce015ddf25fc0a192d97821373"
PREFIX = ".ημ/diagnostics/action-aim-protocol/"


def sha(data):
    return hashlib.sha256(data).hexdigest()


source_direct = {"README.md", "SOURCE-FREEZE.json", "source.patch"}
qualified_direct = {
    "RESULT.md", "VERDICT.json", "HOST-COST-PROTOCOL.md",
    "CLOSED-FILES.txt", "SHA256SUMS", "append-only-proof.json",
    "qualification-comment.txt", "qualification-comment.json",
    "qualification-readback.json",
    "focused-02/focused.stdout", "focused-02/focused.stderr",
    "focused-02/focused-execution.json", "focused-02/oracle-repair.json",
    "focused-02/oracle-repair.patch",
    "canonical-gate-02/before.json", "canonical-gate-02/source-test-pins.json",
    "canonical-gate-02/run-gate.py", "canonical-gate-02/gate.stdout",
    "canonical-gate-02/gate.stderr", "canonical-gate-02/gate-execution.json",
    "canonical-gate-02/style-repair.json", "canonical-gate-02/style-repair.patch",
}
originals = {}
direct = []
packed = []
for name, keep in [("green-source", source_direct),
                   ("green-qualification", qualified_direct)]:
    folder = ROOT / PREFIX / name
    for path in sorted(folder.rglob("*")):
        if not path.is_file():
            continue
        assert not path.is_symlink()
        relative = str(path.relative_to(ROOT))
        originals[relative] = path.read_bytes()
        (direct if str(path.relative_to(folder)) in keep else packed).append(relative)

entries = []
with (HERE / "historical-evidence.jsonl").open("xb") as stream:
    for number, relative in enumerate(packed, 1):
        raw = originals[relative]
        text = raw.decode("utf-8")
        assert text.encode("utf-8") == raw
        record = {"path": relative, "bytes": len(raw), "sha256": sha(raw), "text": text}
        encoded = (json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        start = stream.tell()
        stream.write(encoded)
        entries.append({key: record[key] for key in ["path", "bytes", "sha256"]} |
                       {"line": number, "byte_start": start, "byte_end": stream.tell()})

archive = (HERE / "historical-evidence.jsonl").read_bytes()
for entry in entries:
    restored = json.loads(archive[entry["byte_start"]:entry["byte_end"]])["text"].encode("utf-8")
    assert restored == originals[entry["path"]]
    assert sha(restored) == entry["sha256"]
assert all((ROOT / p).read_bytes() == b for p, b in originals.items())
mapping = {"format": "lossless-utf8-jsonl-v1", "originals_retained_locally": True,
           "archive": str((HERE / "historical-evidence.jsonl").relative_to(ROOT)),
           "archive_sha256": sha(archive), "entries": entries,
           "direct": [{"path": p, "bytes": len(originals[p]), "sha256": sha(originals[p])}
                      for p in sorted(direct)]}
(HERE / "AUXILIARY-MAP.json").write_text(json.dumps(mapping, indent=2) + "\n")
current = set(filter(None, subprocess.check_output(
    ["git", "diff", "--name-only", "-z", BASE], cwd=ROOT, text=True).split("\0")))
publication = (current | set(originals)) - set(packed)
publication.update(str((HERE / name).relative_to(ROOT)) for name in
                   ["package.py", "historical-evidence.jsonl", "AUXILIARY-MAP.json",
                    "PROOF.json", "PACKAGING.md", "PUBLICATION-FILES.txt", "SHA256SUMS",
                    "INVENTORY-REPAIR.json"])
proof = {"base": BASE, "source_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
         "original_files": len(originals), "original_bytes": sum(map(len, originals.values())),
         "direct_files": len(direct), "packed_files": len(packed),
         "packed_original_bytes": sum(len(originals[p]) for p in packed),
         "encoded_archive_bytes": len(archive), "empty_files_preserved": sum(not originals[p] for p in packed),
         "every_packed_file_byte_roundtrip_equal": True, "all_originals_unchanged": True,
         "partition_complete_disjoint": set(direct).isdisjoint(packed) and set(direct) | set(packed) == set(originals),
         "effective_cumulative_publication_paths": len(publication),
         "root_only_staging_required": True,
         "note": "No Git index mutation. Packed files stay local; root stages only the explicit publication list. Historical manifests can be verified after reconstructing exact original paths via this map. Current sources and passing output remain direct."}
(HERE / "PROOF.json").write_text(json.dumps(proof, indent=2) + "\n")
(HERE / "PUBLICATION-FILES.txt").write_text("\n".join(sorted(publication)) + "\n")
assert len(publication) <= 100
print(json.dumps(proof, indent=2))
