"""Lossless offline publication packaging of the closed attempt-02 audit inputs.

Never imports game/driver code or calls a live service. Git invocations are
read-only tracked-path/head census. Only five fresh packaging outputs are written;
original files, audits, preparation, other attempts and other diagnostics stay put.
"""
from pathlib import Path, PurePosixPath
import hashlib
import json
import subprocess

BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
PREFIX=BASE.relative_to(ROOT).as_posix()
OUTPUTS=("attempt-02-PACKAGING.md","attempt-02-AUXILIARY-MAP.json",
         "attempt-02-run-auxiliary.jsonl","attempt-02-client-auxiliary.jsonl",
         "attempt-02-packaging-proof.json")
CORE={"runs/attempt-02/"+name for name in (
    "closed.json","state.json","operations.jsonl","snapshot-messages.edn",
    "snapshot-requests.edn","snapshot-current.stdout","snapshot-current.result",
    "source-hashes.json","preparation-hashes.json","supervisor-launch.json",
    "001-xvfb.stderr","aim-01-1010/controller.jsonl","aim-01-1010/result.json")}
AUDITS=("attempt-02-closure-audit.json","attempt-02-closure-audit.md","attempt-02-closure-audit.py")

def sha(data):
    return hashlib.sha256(data).hexdigest()

def metadata(data):
    return {"bytes":len(data),"sha256":sha(data)}

def json_bytes(value):
    return (json.dumps(value,ensure_ascii=False,indent=2)+"\n").encode("utf-8")

def safe(name):
    path=PurePosixPath(name)
    assert not path.is_absolute() and path.as_posix()==name
    assert all(part not in ("",".","..") for part in path.parts)
    assert "\\" not in name and "\x00" not in name
    target=BASE/path
    for ancestor in [target,*target.parents]:
        if ancestor==BASE.parent:
            break
        assert not ancestor.is_symlink(),name
    assert target.resolve().is_relative_to(BASE),name
    return target

def unique_object(pairs):
    result={}
    for key,value in pairs:
        assert key not in result,key
        result[key]=value
    return result

def main():
    assert all(not (BASE/name).exists() for name in OUTPUTS),"No overwrite"
    audit_raw=(BASE/AUDITS[0]).read_bytes()
    audit=json.loads(audit_raw)
    inventory=audit["input_inventory"]
    names=[];raw={}
    for line in inventory["input_file_map"]:
        name,size,digest=line.split("\t")
        assert name not in raw
        data=safe(name).read_bytes()
        assert metadata(data)=={"bytes":int(size),"sha256":digest},name
        names.append(name);raw[name]=data
    assert names==sorted(names)
    canonical="".join(f"{sha(raw[name])}  {len(raw[name])}  {name}\n" for name in names).encode()
    assert len(names)==inventory["count"]==707
    assert sum(map(len,raw.values()))==inventory["total_bytes"]
    assert sha(canonical)==inventory["canonical_rows_sha256"]
    assert json.loads(raw["runs/attempt-02/closed.json"])["stage"]=="closed"
    tracked=set(subprocess.check_output(["git","ls-files","-z"],cwd=ROOT).decode().split("\x00"))
    inspected_head=subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()
    archives={"attempt-02-run-auxiliary.jsonl":[],"attempt-02-client-auxiliary.jsonl":[]}
    entries=[]
    for name in names:
        data=raw[name]
        try:
            text=data.decode("utf-8")
        except UnicodeDecodeError:
            text=None
        reasons=[]
        if not name.startswith(("runs/attempt-02/","attempt-02-clients/")):
            reasons.append("frozen-preparation-or-root-provenance")
        if name in CORE:
            reasons.append("core-journal-state-or-aim-evidence")
        if len(data)>8192:
            reasons.append("larger-than-8192-bytes")
        if name.lower().endswith(".png"):
            reasons.append("png")
        if text is None:
            reasons.append("non-UTF8")
        if f"{PREFIX}/{name}" in tracked:
            reasons.append("already-tracked")
        entry={"path":name,**metadata(data)}
        if reasons:
            entry.update(storage="direct",publication="already-tracked" if f"{PREFIX}/{name}" in tracked else "new-direct",reasons=reasons)
        else:
            archive="attempt-02-run-auxiliary.jsonl" if name.startswith("runs/attempt-02/") else "attempt-02-client-auxiliary.jsonl"
            archives[archive].append({"path":name,**metadata(data),"content":text})
            entry.update(storage="packed",archive=archive,line=len(archives[archive]))
        entries.append(entry)
    encoded={name:b"".join((json.dumps(item,ensure_ascii=False,separators=(",",":"))+"\n").encode() for item in items) for name,items in archives.items()}
    lookup={e["path"]:e for e in entries}
    recovered={e["path"]:raw[e["path"]] for e in entries if e["storage"]=="direct"}
    for archive,data in encoded.items():
        for line_number,line in enumerate(data.splitlines(),1):
            item=json.loads(line,object_pairs_hook=unique_object)
            assert list(item)==["path","bytes","sha256","content"]
            name=item["path"];safe(name)
            assert name not in recovered
            payload=item["content"].encode("utf-8")
            assert metadata(payload)=={k:item[k] for k in ("bytes","sha256")}
            assert payload==raw[name]
            assert lookup[name]["archive"]==archive and lookup[name]["line"]==line_number
            recovered[name]=payload
    assert recovered==raw and set(recovered)==set(names)
    source=json.loads(raw["SOURCE-EQUALITY.json"])["files_by_path"]
    for name,expected in source.items():
        assert name in tracked
        assert metadata((ROOT/name).read_bytes())==expected,name
    audit_records={name:metadata((BASE/name).read_bytes()) for name in AUDITS}
    direct={e["path"] for e in entries if e["storage"]=="direct"}
    packed={e["path"] for e in entries if e["storage"]=="packed"}
    old=json.loads((BASE/"attempt-01-AUXILIARY-MAP.json").read_text())
    old_direct=set(old["direct_paths"])
    old_supplement={"closure-audit.json","closure-audit.md","attempt-01-PACKAGING.md","attempt-01-AUXILIARY-MAP.json",
                    "attempt-01-run-auxiliary.jsonl","attempt-01-client-auxiliary.jsonl",
                    "attempt-01-package.py","attempt-01-packaging-proof.json"}
    own_supplement=set(AUDITS)|set(OUTPUTS)|{Path(__file__).name}
    union=direct|old_direct|old_supplement|own_supplement
    census={"head_at_readonly_census":inspected_head,"attempt02_direct":len(direct),
            "attempt02_already_tracked_direct":sum(e.get("publication")=="already-tracked" for e in entries),
            "attempt02_new_direct":sum(e.get("publication")=="new-direct" for e in entries),
            "attempt02_packed_originals":len(packed),"attempt02_package_and_audit_paths":len(own_supplement),
            "attempt02_complete_represented_path_set":len(direct|own_supplement),
            "attempt01_direct":len(old_direct),"both_attempt_direct_union":len(direct|old_direct),
            "both_attempt_complete_evidence_union":len(union),
            "union_already_tracked":sum(f"{PREFIX}/{n}" in tracked for n in union),
            "union_new_paths":sum(f"{PREFIX}/{n}" not in tracked for n in union),
            "union_paths":sorted(union),
            "limit":"Diagnostic evidence union, not an exact PR diff or provider admission result; board/receipt/other PR changes may add paths. More than100 paths remains visible."}
    mapping={"format":"truth.closed-auxiliary-utf8-jsonl/v1","scope":"Exactly707 closed attempt02 audit inputs; attempt01 read only for publication union census.",
             "paths_relative_to":PREFIX,"audit":{"path":AUDITS[0],**metadata(audit_raw)},
             "original_inventory_sha256":sha(canonical),
             "counts":{"originals":len(names),"original_bytes":sum(map(len,raw.values())),"direct":len(direct),"packed":len(packed),
                       "empty_originals":sum(not data for data in raw.values())},
             "archives":{name:{**metadata(data),"entries":len(archives[name])} for name,data in encoded.items()},
             "direct_paths":sorted(direct),"new_direct_paths":sorted(e["path"] for e in entries if e.get("publication")=="new-direct"),
             "entries":entries,"audit_and_script_paths":audit_records,"path_census":census,
             "unaccounted_audit_input_paths":[],
             "publication_note":"Publish all direct paths, both archives, this mapping/proof/README/script and all three audit artifacts. Original files stay byte-identical locally. JSONL content decodes to exact UTF8 bytes including empty outputs; no game or EDN evaluation is required."}
    readme=f"""# Closed bounded-look-200 attempt 02: lossless publication map

This packages only the closed attempt02's previously uncommitted, small UTF-8
auxiliary evidence. All originals stay unchanged locally. The [audit](attempt-02-closure-audit.md)
records successful orientation at one natural target and two released W pulses,
followed by a bounded operator stop with no binding or commitment. Packaging adds
no native qualification and does not touch another attempt, runtime, board, or the
new bounded-flight-cadence preparation.

The [map](attempt-02-AUXILIARY-MAP.json) represents all **{len(names)} original audit
inputs ({sum(map(len,raw.values())):,} bytes)** with {len(direct)} direct files and
{len(packed)} packed files. The historical input digest remains
{sha(canonical)}. The three audit outputs accompany publication separately.

- [Run auxiliary](attempt-02-run-auxiliary.jsonl): {len(archives['attempt-02-run-auxiliary.jsonl'])} files.
- [Outer-client auxiliary](attempt-02-client-auxiliary.jsonl): {len(archives['attempt-02-client-auxiliary.jsonl'])} files.

Each line has exactly path, bytes, sha256, content. Decode content and encode UTF-8
to recover the original bytes; JSON escaping preserves line breaks and all
{mapping['counts']['empty_originals']} empty files. Every input larger than8,192
bytes, PNG, non-UTF8 file, previously tracked file, frozen preparation/provenance,
and named core journal/state/aim artifact remains direct. Both full snapshot
journals, operations, complete game stdout, final state, aim controller/result and
two original PNGs remain individually readable.

For a fresh checkout, each mapping entry resolves to its direct path or to its
named archive and one-based line. Check unique safe relative paths, SHA256 and
byte counts before optional extraction into a new directory. Never overwrite
evidence. The [saved script](attempt-02-package.py) implements an explicit complete
bijection and refuses existing outputs; the [proof](attempt-02-packaging-proof.json)
records successful independent decoding and byte comparison. No original is
removed, renamed, rewritten, or filtered. All186 recorded production references
are independently hash-checked and already tracked.

## Publication-path census

At inspected HEAD {inspected_head}, attempt02 needs {len(direct)} direct original
paths ({census['attempt02_already_tracked_direct']} already tracked,
{census['attempt02_new_direct']} new), plus {len(own_supplement)} audit/package paths.
Its complete represented set has {census['attempt02_complete_represented_path_set']} paths.

The union with attempt01 contains {len(direct|old_direct)} direct original paths,
and **{len(union)} total represented evidence paths** after both packages/audits.
Of that union, {census['union_already_tracked']} paths are already tracked and
{census['union_new_paths']} are new. This is not an exact PR-diff count: board,
receipt or other changes may add paths, while ancestor paths depend on the chosen
PR base. The full explicit union is in the map.

The union therefore exceeds100 paths. No evidence was discarded or recompressed
to manufacture review eligibility. Fewer filesystem paths do not mean fewer
review-input bytes, completed coverage, or provider approval. A native reviewer
must still inspect all relevant direct files, archives, images and provenance,
and disclose any unavailable or unprocessed input.
"""
    outputs={**encoded,"attempt-02-AUXILIARY-MAP.json":json_bytes(mapping),
             "attempt-02-PACKAGING.md":readme.encode()}
    for name in names:
        assert safe(name).read_bytes()==raw[name],name
    proof={"status":"PASS","scope":"Offline closed attempt02 publication packaging only",
           "script":{"path":Path(__file__).name,**metadata(Path(__file__).read_bytes())},
           "audit_inventory":{"count":len(names),"bytes":sum(map(len,raw.values())),"canonical_rows_sha256":sha(canonical)},
           "checks":{"unique_complete_direct_packed_bijection":True,
                     "all_archive_content_decoded_byte_equal":True,
                     "safe_paths_no_symlinks_or_duplicate_JSON_keys":True,
                     "large_png_non_UTF8_and_already_tracked_inputs_direct":True,
                     "all_original_bytes_unchanged_before_output":True},
           "counts":mapping["counts"],"production_hashes_verified":len(source),"path_census":census,
           "outputs":{name:metadata(data) for name,data in outputs.items()},
           "limit":"Packaging integrity only, not a new runtime/audit result or hosted review approval."}
    for name,data in outputs.items():
        with (BASE/name).open("xb") as stream:
            stream.write(data)
        assert (BASE/name).read_bytes()==data
    for name in names:
        assert safe(name).read_bytes()==raw[name],name
    for name,expected in audit_records.items():
        assert metadata((BASE/name).read_bytes())==expected,name
    proof["checks"]["all_original_and_audit_bytes_unchanged_after_output"]=True
    with (BASE/"attempt-02-packaging-proof.json").open("xb") as stream:
        stream.write(json_bytes(proof))
    print(json.dumps({"counts":mapping["counts"],"path_census":{k:v for k,v in census.items() if k!="union_paths"},
                      "outputs":{name:metadata((BASE/name).read_bytes()) for name in OUTPUTS}},indent=2))

if __name__=="__main__":
    main()

