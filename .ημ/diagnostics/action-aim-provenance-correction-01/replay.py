"""Derived read-only replay, not the unknown historical regeneration command.

Run with Python 3 from any directory. No subprocess, Git, extraction, filesystem
writes, game, JVM or network. Packed originals are recovered only in memory;
retained local copies, when present, are also compared. Publication inputs stay
frozen. Historical metadata is explicitly carried forward, not rediscovered.
"""
from pathlib import Path, PurePosixPath
import ast
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
PREFIX = ".ημ/diagnostics/action-aim-protocol/"
PUBLICATION = PREFIX + "publication/"
PINS = {
    "package.py": "598aa247878bbc565575ce4e80f98c8370844aabec66a5244244641ba5a1e688",
    "PACKAGING.md": "9db0388447b1dd68a85d402f9e3becedfbc287e4caab2e591066f86791f61d3e",
    "INVENTORY-REPAIR.json": "528292a51c9ea1b1856e11dbf8326456c29bb22bfd1514b3233c26ff4c251bd6",
    "PROOF.json": "8be8eb2888e4068ca4da7cda7ec243999c9dc0bf4c8a3a214ca7e5b0ccd8a5f5",
    "PUBLICATION-FILES.txt": "a8f70af4b53b384f566611edc3e7998cdff41ec2f7e114aee7e2aa23c78752bc",
    "AUXILIARY-MAP.json": "bf263f55884b49aa1bac391708ff9f81c5765f83ffd6ca95f7d81e45e4636afb",
    "historical-evidence.jsonl": "482499697aaeb20a8084ccb1dee7f479cbc6b6233f87f710fe22a0fca549f062",
    "SHA256SUMS": "ba8adf8d347b27d1b3def7d7e29ed75b152cf70514ac767828684cb67503dd9e",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read(relative):
    path = PurePosixPath(relative)
    require(not path.is_absolute() and ".." not in path.parts, "unsafe input path")
    target = ROOT / relative
    require(not target.is_symlink(), "symlink input")
    return target.read_bytes()


def formatted(value):
    return (json.dumps(value, indent=2) + "\n").encode("utf-8")


def main():
    frozen = {name: read(PUBLICATION + name) for name in PINS}
    for name, expected in PINS.items():
        require(sha(frozen[name]) == expected, "frozen input changed: " + name)

    # Read only literal policy sets. Never import or execute the saved producer.
    syntax = ast.parse(frozen["package.py"].decode("utf-8"))
    policy = {}
    for node in syntax.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
            if isinstance(target, ast.Name) and target.id in {"source_direct", "qualified_direct"}:
                policy[target.id] = ast.literal_eval(node.value)
    require(set(policy) == {"source_direct", "qualified_direct"}, "missing literal policy")

    archive = frozen["historical-evidence.jsonl"]
    mapping = json.loads(frozen["AUXILIARY-MAP.json"])
    require(sha(archive) == mapping["archive_sha256"], "archive digest mismatch")
    require(mapping["archive"] == PUBLICATION + "historical-evidence.jsonl", "archive path")
    records = [json.loads(line) for line in archive.splitlines()]
    originals = {}
    for record in records:
        path = record["path"]
        raw = record["text"].encode("utf-8")
        require(path not in originals, "duplicate packed path")
        require(len(raw) == record["bytes"] and sha(raw) == record["sha256"], "packed bytes")
        originals[path] = raw
    for record in mapping["direct"]:
        path = record["path"]
        require(path not in originals, "partition overlap")
        raw = read(path)
        require(len(raw) == record["bytes"] and sha(raw) == record["sha256"], "direct bytes")
        originals[path] = raw

    direct, packed = [], []
    for folder, keep in [("green-source", policy["source_direct"]),
                         ("green-qualification", policy["qualified_direct"])]:
        prefix = PREFIX + folder + "/"
        for path in sorted(p for p in originals if p.startswith(prefix)):
            (direct if path[len(prefix):] in keep else packed).append(path)
    require(set(direct).isdisjoint(packed), "partition disjointness")
    require(set(direct) | set(packed) == set(originals), "partition completeness")

    # Reproduce archive order, JSON encoding, offsets and map using exact bytes.
    regenerated = bytearray()
    entries = []
    for number, path in enumerate(packed, 1):
        raw = originals[path]
        record = {"path": path, "bytes": len(raw), "sha256": sha(raw), "text": raw.decode("utf-8")}
        start = len(regenerated)
        regenerated.extend((json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8"))
        entries.append({key: record[key] for key in ["path", "bytes", "sha256"]} |
                       {"line": number, "byte_start": start, "byte_end": len(regenerated)})
    require(bytes(regenerated) == archive, "archive regeneration differs")
    regenerated_map = {
        "format": "lossless-utf8-jsonl-v1", "originals_retained_locally": True,
        "archive": PUBLICATION + "historical-evidence.jsonl", "archive_sha256": sha(archive),
        "entries": entries,
        "direct": [{"path": p, "bytes": len(originals[p]), "sha256": sha(originals[p])} for p in sorted(direct)],
    }
    require(formatted(regenerated_map) == frozen["AUXILIARY-MAP.json"], "map regeneration differs")

    repair = json.loads(frozen["INVENTORY-REPAIR.json"])
    draft = repair["original_inventory_utf8"]
    require(sha(draft.encode("utf-8")) == repair["original_inventory_sha256"], "draft digest")
    # This pinned draft contains Git octal-escaped UTF-8 display names. Literal
    # byte parsing decodes exactly these inputs, not arbitrary Git path syntax.
    decoded = [ast.literal_eval("b" + line).decode("utf-8") if line.startswith('"') else line
               for line in draft.splitlines()]
    require(len(decoded) == len(set(decoded)), "duplicate decoded draft path")
    publication = sorted(set(decoded) | {PUBLICATION + "INVENTORY-REPAIR.json"})
    inventory = ("\n".join(publication) + "\n").encode("utf-8")
    require(inventory == frozen["PUBLICATION-FILES.txt"], "inventory regeneration differs")
    require(all((ROOT / p).is_file() for p in publication), "missing direct publication path")

    # Historical base/source_head, note and staging-policy flag are carried from
    # pinned PROOF, not independently recovered from Git or an execution record.
    historical = json.loads(frozen["PROOF.json"])
    proof = {
        "base": historical["base"], "source_head": historical["source_head"],
        "original_files": len(originals), "original_bytes": sum(map(len, originals.values())),
        "direct_files": len(direct), "packed_files": len(packed),
        "packed_original_bytes": sum(len(originals[p]) for p in packed),
        "encoded_archive_bytes": len(archive), "empty_files_preserved": sum(not originals[p] for p in packed),
        "every_packed_file_byte_roundtrip_equal": True,
        "all_originals_unchanged": historical["all_originals_unchanged"],
        "partition_complete_disjoint": True,
        "effective_cumulative_publication_paths": len(publication),
        "root_only_staging_required": historical["root_only_staging_required"], "note": historical["note"],
    }
    require(formatted(proof) == frozen["PROOF.json"], "proof regeneration differs")

    local_matches, local_absent = [], []
    for path, raw in originals.items():
        if (ROOT / path).exists():
            require(read(path) == raw, "local original changed: " + path)
            local_matches.append(path)
        else:
            local_absent.append(path)
    require(set(local_absent).issubset(packed), "missing direct original")
    for folder in ["green-source", "green-qualification"]:
        actual = {str(p.relative_to(ROOT)) for p in (ROOT / PREFIX / folder).rglob("*") if p.is_file()}
        require(actual.issubset(originals), "unclassified local original")
    require(all(read(PUBLICATION + p) == raw for p, raw in frozen.items()), "input changed during replay")
    print(json.dumps({
        "result": "PASS", "kind": "derived-read-only-replay",
        "historical_regeneration_command": "UNKNOWN; this command is a newly derived verification recipe",
        "regenerated_exact": {p: sha(frozen[p]) for p in ["historical-evidence.jsonl", "AUXILIARY-MAP.json", "PROOF.json", "PUBLICATION-FILES.txt"]},
        "pinned_old_publication_files": len(PINS), "old_publication_bytes_unchanged": True,
        "draft_paths": len(decoded), "quoted_draft_paths": sum(p.startswith('"') for p in draft.splitlines()),
        "final_inventory_paths": len(publication), "original_files": len(originals),
        "original_bytes": sum(map(len, originals.values())), "packed_files": len(packed),
        "packed_bytes": sum(len(originals[p]) for p in packed), "empty_packed_files": sum(not originals[p] for p in packed),
        "direct_files": len(direct), "retained_local_originals_compared": len(local_matches),
        "packed_originals_absent_locally": len(local_absent),
        "carried_historical_metadata": ["base", "source_head", "root_only_staging_required", "note", "all_originals_unchanged", "map.originals_retained_locally"],
        "limits": "Byte-equivalent derived outputs do not identify or prove the historical regeneration command. No current Git inventory or current ledger-hash claim; frozen SHA256SUMS itself is pinned but later companion appends are outside this replay.",
    }, indent=2))


if __name__ == "__main__":
    main()
