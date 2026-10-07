#!/usr/bin/env python3
"""One-shot lossless publication packaging; no runtime/board/source operations."""
from pathlib import Path
import datetime
import hashlib
import json

HERE = Path(__file__).resolve().parent
HISTORY = {"red", "refinement-red", "camera-screen-red", "single-aim-check",
           "green-01", "green-02", "green-03", "green-final"}
sha = lambda b: hashlib.sha256(b).hexdigest()
originals = (HERE / "PREPARATION-FILES.txt").read_text().splitlines()
assert len(originals) == len(set(originals)) == 69
inventory = {name: {"bytes": (HERE / name).stat().st_size,
                    "sha256": sha((HERE / name).read_bytes())} for name in originals}
packed = [n for n in originals if Path(n).parts[0] in HISTORY]
direct = [n for n in originals if n not in packed]
rows = []
with (HERE / "historical-preparation.jsonl").open("xb") as out:
    for name in packed:
        raw = (HERE / name).read_bytes()
        start = out.tell()
        encoded = (json.dumps({"path": name, "bytes": len(raw), "sha256": sha(raw),
                               "utf8": raw.decode("utf-8")}, ensure_ascii=False,
                              separators=(",", ":")) + "\n").encode("utf-8")
        out.write(encoded)
        rows.append({"path": name, **inventory[name], "byte_start": start,
                     "byte_end": start + len(encoded)})
    out.flush()
blob = (HERE / "historical-preparation.jsonl").read_bytes()
for row in rows:
    decoded = json.loads(blob[row["byte_start"]:row["byte_end"]])
    recovered = decoded["utf8"].encode("utf-8")
    assert recovered == (HERE / row["path"]).read_bytes()
    assert len(recovered) == row["bytes"] and sha(recovered) == row["sha256"]
assert all(sha((HERE / n).read_bytes()) == v["sha256"] for n, v in inventory.items())
mapdata = {"version": 1, "frozen_manifest": "SHA256SUMS",
           "frozen_manifest_sha256": inventory["SHA256SUMS"]["sha256"],
           "encoding": "one UTF8 JSON object per line; utf8 field reconstructs original bytes",
           "bundle": "historical-preparation.jsonl", "bundle_bytes": len(blob),
           "bundle_sha256": sha(blob), "direct": {n: inventory[n] for n in direct},
           "packed": rows}
with (HERE / "HISTORY-MAP.json").open("x") as out:
    json.dump(mapdata, out, indent=2); out.write("\n")
new = ["package-history.py", "historical-preparation.jsonl", "HISTORY-MAP.json",
       "PACKAGING.md", "packaging-proof.json", "PUBLICATION-FILES.txt"]
publication = direct + new
assert len(publication) <= 45 and len(publication) == len(set(publication))
with (HERE / "PUBLICATION-FILES.txt").open("x") as out:
    out.write("\n".join(publication) + "\n")
proof = {"at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
         "frozen_inputs": len(originals), "packed": len(packed), "direct": len(direct),
         "publication_paths": len(publication), "all_originals_retained": True,
         "all_original_bytes_unchanged": True, "every_packed_byte_roundtrip_equal": True,
         "packed_original_bytes": sum(inventory[n]["bytes"] for n in packed),
         "bundle_bytes": len(blob), "bundle_sha256": sha(blob),
         "map_sha256": sha((HERE / "HISTORY-MAP.json").read_bytes()),
         "publication_list_sha256": sha((HERE / "PUBLICATION-FILES.txt").read_bytes()),
         "script_sha256": sha(Path(__file__).read_bytes()),
         "final_green_kept_direct": "green-qualified",
         "source_or_runtime_changes": False}
with (HERE / "packaging-proof.json").open("x") as out:
    json.dump(proof, out, indent=2); out.write("\n")
print(json.dumps(proof, indent=2))
