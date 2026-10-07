"""Lossless, closed attempt-01 publication packaging. Never imports game code.

Writes only the five new attempt-01 packaging artifacts. Existing outputs cause
failure before writing. Original evidence is read as bytes and remains in place.
"""

from pathlib import Path, PurePosixPath
import hashlib
import json
import subprocess


BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[2]
PREFIX = BASE.relative_to(ROOT).as_posix()
OUTPUTS = (
    "attempt-01-PACKAGING.md",
    "attempt-01-AUXILIARY-MAP.json",
    "attempt-01-run-auxiliary.jsonl",
    "attempt-01-client-auxiliary.jsonl",
    "attempt-01-packaging-proof.json",
)
CORE = {
    "runs/attempt-01/closed.json", "runs/attempt-01/state.json",
    "runs/attempt-01/operations.jsonl", "runs/attempt-01/snapshot-messages.edn",
    "runs/attempt-01/snapshot-requests.edn", "runs/attempt-01/snapshot-current.stdout",
    "runs/attempt-01/snapshot-current.result", "runs/attempt-01/source-hashes.json",
    "runs/attempt-01/preparation-hashes.json", "runs/attempt-01/supervisor-launch.json",
    "runs/attempt-01/001-xvfb.stderr",
    "attempt-01-clients/020-spark-open.json",
    "attempt-01-clients/020-spark-open.stdout",
    "attempt-01-clients/020-spark-open.stderr",
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def metadata(data):
    return {"bytes": len(data), "sha256": sha(data)}


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def safe(relative):
    path = PurePosixPath(relative)
    assert not path.is_absolute() and path.as_posix() == relative
    assert all(part not in (".", "..", "") for part in path.parts)
    assert "\\" not in relative and "\x00" not in relative
    for ancestor in [BASE / path, *(BASE / path).parents]:
        if ancestor == BASE.parent:
            break
        assert not ancestor.is_symlink(), relative
    assert (BASE / path).resolve().is_relative_to(BASE)
    return BASE / path


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, key
        result[key] = value
    return result


def main():
    assert (ROOT / ".git").exists(), ROOT
    assert all(not (BASE / name).exists() for name in OUTPUTS), "Never overwrite packaging"
    audit_bytes = (BASE / "closure-audit.json").read_bytes()
    audit = json.loads(audit_bytes)
    inventory = audit["raw_input_inventory"]
    top = inventory["top_level_paths"]
    auxiliary = []
    for directory in ("runs/attempt-01", "attempt-01-clients"):
        for path in (BASE / directory).rglob("*"):
            assert not path.is_symlink(), path
            if path.is_file():
                auxiliary.append(path.relative_to(BASE).as_posix())
    names = sorted(top + auxiliary)
    assert len(names) == len(set(names)) == inventory["files"]
    raw = {name: safe(name).read_bytes() for name in names}
    inventory_bytes = "".join(f"{sha(raw[name])}  {len(raw[name])}  {name}\n"
                              for name in names).encode("utf-8")
    assert sha(inventory_bytes) == inventory["inventory_sha256"]
    assert sum(map(len, raw.values())) == inventory["bytes"]
    assert json.loads(raw["runs/attempt-01/closed.json"])["stage"] == "closed"
    tracked_result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT,
                                    capture_output=True, check=True)
    tracked = set(tracked_result.stdout.decode("utf-8").split("\x00"))
    archives = {"attempt-01-run-auxiliary.jsonl": [], "attempt-01-client-auxiliary.jsonl": []}
    entries = []
    for name in names:
        data = raw[name]
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            text = None
        reasons = []
        if name in top:
            reasons.append("historical-top-level-audit-input")
        if name in CORE:
            reasons.append("core-or-failure-evidence")
        if len(data) > 8192:
            reasons.append("larger-than-8192-bytes")
        if name.lower().endswith(".png"):
            reasons.append("png")
        if text is None:
            reasons.append("non-utf8")
        if f"{PREFIX}/{name}" in tracked:
            reasons.append("already-tracked")
        entry = {"path": name, **metadata(data)}
        if reasons:
            entry.update(storage="direct", publication=("already-tracked" if
                         f"{PREFIX}/{name}" in tracked else "new-direct"), reasons=reasons)
        else:
            archive = ("attempt-01-run-auxiliary.jsonl" if name.startswith("runs/attempt-01/")
                       else "attempt-01-client-auxiliary.jsonl")
            row = {"path": name, **metadata(data), "content": text}
            archives[archive].append(row)
            entry.update(storage="packed", archive=archive, line=len(archives[archive]))
        entries.append(entry)
    encoded = {name: b"".join((json.dumps(row, ensure_ascii=False, separators=(",", ":"))
                               + "\n").encode("utf-8") for row in rows)
               for name, rows in archives.items()}
    recovered = {entry["path"]: raw[entry["path"]] for entry in entries if entry["storage"] == "direct"}
    for archive, data in encoded.items():
        for index, line in enumerate(data.splitlines(), 1):
            row = json.loads(line, object_pairs_hook=unique_object)
            assert list(row) == ["path", "bytes", "sha256", "content"]
            name = row["path"]
            safe(name)
            assert name not in recovered
            payload = row["content"].encode("utf-8")
            assert metadata(payload) == {"bytes": row["bytes"], "sha256": row["sha256"]}
            assert payload == raw[name]
            ref = next(entry for entry in entries if entry["path"] == name)
            assert ref["archive"] == archive and ref["line"] == index
            recovered[name] = payload
    assert recovered == raw and set(recovered) == set(names)
    for artifact in audit["critical_artifacts"] + audit["stderr"]:
        assert artifact["path"] in recovered
        assert metadata(recovered[artifact["path"]]) == {k: artifact[k] for k in ("bytes", "sha256")}
    source = json.loads(raw["SOURCE-EQUALITY.json"])
    source_refs = source["files_by_path"]
    for name, expected in source_refs.items():
        assert name in tracked, name
        assert metadata((ROOT / name).read_bytes()) == expected, name
    direct = [entry["path"] for entry in entries if entry["storage"] == "direct"]
    packed = [entry["path"] for entry in entries if entry["storage"] == "packed"]
    mapping = {
        "format": "truth.closed-auxiliary-utf8-jsonl/v1",
        "scope": "Exactly the 335 closed attempt-01 audit inputs; no attempt02, board or ledger input.",
        "paths_relative_to": PREFIX,
        "audit": {"path": "closure-audit.json", **metadata(audit_bytes)},
        "original_inventory_sha256": sha(inventory_bytes),
        "counts": {"originals": len(names), "original_bytes": sum(map(len, raw.values())),
                   "auxiliary_originals": len(auxiliary), "top_level_originals": len(top),
                   "direct": len(direct), "packed": len(packed),
                   "empty_originals": sum(not data for data in raw.values())},
        "archives": {name: {**metadata(data), "entries": len(archives[name])}
                     for name, data in encoded.items()},
        "direct_paths": direct,
        "new_direct_paths": [entry["path"] for entry in entries
                             if entry.get("publication") == "new-direct"],
        "entries": entries,
        "unaccounted_audit_input_paths": [],
        "existing_tracked_production_references_verified": len(source_refs),
        "publication_note": "Packed originals stay unchanged locally. Include both archives, this mapping and every direct path. No source evidence is filtered out. Original audit digests resolve through the mapping; bytes can be reconstructed without evaluating EDN or game code.",
    }
    readme = f"""# Closed bounded-look-200 attempt 01: lossless publication map

This packages **new, previously uncommitted auxiliary evidence only** from the
failed closed attempt 01. Every original remains unchanged locally. It does not
touch the active attempt 02, frozen preparation, game source, board or ledgers.
The [closed audit](closure-audit.md) retains the failure and its limits: four
young-world 200-pixel calibration looks, followed by an operator menu request
while cursor-free was false; no target admission, aim, W, approach or commitment.

## Complete readable evidence

The [mapping](attempt-01-AUXILIARY-MAP.json) accounts for all **{len(names)}** audit
inputs, **{sum(map(len, raw.values())):,} original bytes**: {len(direct)} direct files
and {len(packed)} packed files. The original audit inventory digest is
`{sha(inventory_bytes)}`. The two archives are ordinary UTF-8 JSONL:

- [Run auxiliary](attempt-01-run-auxiliary.jsonl): {len(archives['attempt-01-run-auxiliary.jsonl'])} entries.
- [Outer-client auxiliary](attempt-01-client-auxiliary.jsonl): {len(archives['attempt-01-client-auxiliary.jsonl'])} entries.

Each line has the fixed fields `path`, `bytes`, `sha256`, `content`. JSON escaping
preserves newlines, CRLF and empty files; decoding `content` and encoding UTF-8
reconstructs the original bytes. All {mapping['counts']['empty_originals']} empty
originals remain represented. No EDN/event content is parsed or reinterpreted.
Every original larger than 8,192 bytes, PNG and non-UTF-8 file stays direct, as
do the state/closure, key provenance and selected failure outputs. Raw snapshot
journals, operations, full game stdout and the PNG remain direct and inspectable.

For a fresh checkout, use each mapping entry's direct `path`, or the named
archive and one-based `line`. Check safe relative paths, unique entries, byte
count and SHA-256 before optional extraction into a **new directory**; never
overwrite existing evidence. The mapping provides `direct_paths` and
`new_direct_paths` explicitly. All 335 audit references resolve through existing
tracked files, those new direct paths and the two archives, with no unaccounted
input. The 186 production references are already tracked and their recorded
hashes were separately checked. The two audit reports and these packaging
artifacts must also accompany publication; they are not part of the historical
335-input digest. Canonical card/ledger records remain direct and outside this map.

The [packaging proof](attempt-01-packaging-proof.json) records independent decode
and exact-byte comparison of every archive member, the direct/packed bijection,
safe paths and original-byte stability. The [saved script](attempt-01-package.py)
shows the transformation; it refuses existing packaging outputs and is not a
runtime or game tool. No originals were deleted or renamed.

Fewer paths do **not** mean fewer review-input bytes or completed review. A native
reviewer must still inspect the full journals, archives, image and relevant
provenance, and disclose any inaccessible input. No file-cap, runtime-budget,
approval, performance or gameplay qualification is claimed by this packaging.
"""
    outputs = {**encoded, "attempt-01-AUXILIARY-MAP.json": json_bytes(mapping),
               "attempt-01-PACKAGING.md": readme.encode("utf-8")}
    for name in names:
        assert safe(name).read_bytes() == raw[name], name
    proof = {
        "status": "PASS", "scope": "Closed attempt01 evidence packaging only",
        "script": {"path": Path(__file__).name, **metadata(Path(__file__).read_bytes())},
        "audit_inventory": inventory,
        "checks": {"unique_complete_direct_packed_bijection": True,
                   "all_archive_payloads_decoded_and_byte_equal": True,
                   "all_paths_safe_and_no_symlinks": True,
                   "fixed_schema_and_no_duplicate_json_keys": True,
                   "all_large_png_non_utf8_inputs_direct": True,
                   "all_critical_artifact_hashes_match": True,
                   "all_original_bytes_unchanged_before_output": True,
                   "all_audit_inputs_resolvable": True},
        "counts": mapping["counts"], "production_hashes_verified": len(source_refs),
        "unaccounted_outside_paths": [],
        "outputs": {name: metadata(data) for name, data in outputs.items()},
        "verification_limit": "Exact packaging and published-path coverage only; no audit conclusions, native behavior or external review approval are generated by this transformation.",
    }
    for name, data in outputs.items():
        with (BASE / name).open("xb") as stream:
            stream.write(data)
        assert (BASE / name).read_bytes() == data
    for name in names:
        assert safe(name).read_bytes() == raw[name], name
    proof["checks"]["all_original_bytes_unchanged_after_output"] = True
    with (BASE / "attempt-01-packaging-proof.json").open("xb") as stream:
        stream.write(json_bytes(proof))
    print(json.dumps({"counts": mapping["counts"], "archives": mapping["archives"],
                      "new_direct_paths": mapping["new_direct_paths"],
                      "map_sha256": sha(outputs["attempt-01-AUXILIARY-MAP.json"]),
                      "proof_sha256": sha((BASE / "attempt-01-packaging-proof.json").read_bytes())}, indent=2))


if __name__ == "__main__":
    main()
