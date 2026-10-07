"""Lossless UTF-8 publication of immutable host-cost preparation and runs."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
DIAG = HERE.parent

def sha(data):
    return hashlib.sha256(data).hexdigest()

inputs = []
for name in ["action-aim-host-cost", "action-aim-host-cost-02"]:
    inputs.extend(f for f in (DIAG / name).rglob("*") if f.is_file())
inputs.extend(f for f in DIAG.glob("action-aim-host-cost*.json") if f.is_file())
inputs = sorted(set(inputs))
archive = HERE / "host-cost-evidence.jsonl"
entries = []
position = 0
with archive.open("xb") as stream:
    for i, path in enumerate(inputs, 1):
        data = path.read_bytes()
        relative = str(path.relative_to(ROOT))
        row = {"path": relative, "bytes": len(data), "sha256": sha(data), "text": data.decode("utf-8")}
        encoded = (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        assert json.loads(encoded)["text"].encode("utf-8") == data
        stream.write(encoded)
        entries.append({k: row[k] for k in ["path", "bytes", "sha256"]} | {"line": i, "byte_start": position, "byte_end": position + len(encoded)})
        position += len(encoded)
packed = archive.read_bytes()
for item in entries:
    row = json.loads(packed[item["byte_start"]:item["byte_end"]])
    decoded = row["text"].encode("utf-8")
    assert decoded == (ROOT / item["path"]).read_bytes()
    assert sha(decoded) == item["sha256"] and len(decoded) == item["bytes"]
record = {"format": "lossless-utf8-jsonl-v1", "archive": str(archive.relative_to(ROOT)), "archive_sha256": sha(packed), "originals_retained_locally": True, "entries": entries}
with (HERE / "AUXILIARY-MAP.json").open("x") as stream:
    json.dump(record, stream, indent=2); stream.write("\n")
proof = {"files": len(entries), "original_bytes": sum(e["bytes"] for e in entries), "encoded_bytes": len(packed), "empty_files": sum(e["bytes"] == 0 for e in entries), "roundtrip_all": True, "no_inputs_modified": True, "source": "Both original failed baseline and repaired preparation/runs plus root release/closure companions, complete no-omission partition"}
with (HERE / "PROOF.json").open("x") as stream:
    json.dump(proof, stream, indent=2); stream.write("\n")
print(json.dumps(proof))
