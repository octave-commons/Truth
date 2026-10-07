#!/usr/bin/env python3
"""Lossless plain-text packaging from an explicit immutable file inventory.
Never deletes/rewrites an input, invokes Git, or runs any native/game operation.
"""
from pathlib import Path, PurePosixPath
import collections, hashlib, json

HERE = Path(__file__).resolve().parent
WT = HERE.parents[2]
sha = lambda b: hashlib.sha256(b).hexdigest()
relative = lambda p: str(p.relative_to(WT))
inventory_path = HERE/'attempt-03-publication-inputs.json'
inventory = json.loads(inventory_path.read_text())
entries = inventory['files']
assert len(entries)==594

def safe_path(value):
 p=PurePosixPath(value)
 assert isinstance(value,str) and value==p.as_posix() and not p.is_absolute() and '..' not in p.parts
 assert value.startswith('.ημ/diagnostics/')
 full=WT/value
 assert full.resolve().is_relative_to(WT.resolve()) and not full.is_symlink() and full.is_file()
 return full

def checked(entry):
 assert set(entry)=={'category','path','bytes','sha256'} and entry['category'] in {'run','outer-client','provenance'}
 b=safe_path(entry['path']).read_bytes()
 assert len(b)==entry['bytes'] and sha(b)==entry['sha256']
 return b

paths={e['path'] for e in entries};assert len(paths)==len(entries)
original_bytes={e['path']:checked(e) for e in entries}
# Enumeration is solely a completeness check against the explicit frozen list.
for category,folder in [('run',HERE.parent/'bounded-look-200/runs/attempt-03'),('outer-client',HERE/'attempt-03-clients')]:
 current={relative(p) for p in folder.rglob('*') if p.is_file()}
 assert current=={e['path'] for e in entries if e['category']==category}
core_names={'snapshot-messages.edn','snapshot-requests.edn','operations.jsonl','state.json','closed.json','ready.json','source-hashes.json','preparation-hashes.json','controller.jsonl','result.json'}
archives={c:HERE/f'attempt-03-{n}-auxiliary.jsonl' for c,n in [('run','run'),('outer-client','client')]}
new_paths=[*archives.values(),HERE/'attempt-03-AUXILIARY-MAP.json',HERE/'attempt-03-packaging-proof.json',HERE/'attempt-03-PACKAGING.md',HERE/'attempt-03-PUBLICATION-FILES.txt',HERE/'attempt-03-SHA256SUMS']
assert all(not p.exists() for p in new_paths if p not in archives.values()), 'Never overwrite an existing publication package'
# A failed first attempt wrote only these archives; exact-byte reuse is recorded.
buffers={c:bytearray() for c in archives}; line_counts=collections.Counter(); rows=[]
for e in sorted(entries,key=lambda x:x['path']):
 b=original_bytes[e['path']]; p=PurePosixPath(e['path']); reason=None
 try: text=b.decode('utf-8')
 except UnicodeDecodeError: text=None;reason='non-UTF8-original'
 if len(b)>8192: reason='original-larger-than8192B'
 if p.suffix=='.png': reason='actual-full-window-PNG'
 if e['category']=='provenance':reason='named-provenance-or-audit'
 if p.name in core_names:reason='core-journal-identity-closure-controller-or-result'
 if reason:
  rows.append({**e,'storage':'direct','reason':reason});continue
 assert text is not None and e['category'] in archives
 record={'path':e['path'],'bytes':len(b),'sha256':e['sha256'],'encoding':'utf-8','text':text}
 encoded=(json.dumps(record,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n').encode('utf-8')
 category=e['category'];start=len(buffers[category]);buffers[category].extend(encoded);line_counts[category]+=1
 rows.append({**e,'storage':'packed','archive':relative(archives[category]),'line':line_counts[category],'byte_start':start,'byte_end':len(buffers[category])})
for category,p in archives.items():
 if p.exists():
  assert p.read_bytes()==bytes(buffers[category]), 'First-attempt archive differs; stop'
 else:
  with p.open('xb') as f:f.write(buffers[category])
# Independent decode of the completed bytes, not the in-memory input strings.
reconstructed={}; schemas={'path','bytes','sha256','encoding','text'}
for category,p in archives.items():
 for n,line in enumerate(p.read_bytes().splitlines(keepends=True),1):
  entry=json.loads(line);assert set(entry)==schemas and entry['encoding']=='utf-8' and entry['path'] not in reconstructed
  safe_path(entry['path']);b=entry['text'].encode('utf-8');assert len(b)==entry['bytes'] and sha(b)==entry['sha256']
  reconstructed[entry['path']]=b
  row=next(r for r in rows if r['path']==entry['path'])
  assert row['archive']==relative(p) and row['line']==n and p.read_bytes()[row['byte_start']:row['byte_end']]==line
for r in rows:
 if r['storage']=='direct':
  assert r['path'] not in reconstructed;reconstructed[r['path']]=safe_path(r['path']).read_bytes()
assert reconstructed==original_bytes and set(reconstructed)==paths
# Hash references to the already published preparation, without modifying it.
references=[]
for directory in [HERE,HERE.parent/'bounded-look-200']:
 manifest=directory/'SHA256SUMS'
 for line in manifest.read_text().splitlines():
  digest,name=line.split('  ',1);p=directory/name;assert sha(p.read_bytes())==digest
  references.append({'path':relative(p),'bytes':p.stat().st_size,'sha256':digest})
 for name in ['SHA256SUMS','PREPARATION-FILES.txt']:
  p=directory/name;references.append({'path':relative(p),'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())})
unique_references={}
for entry in references:
 assert entry['path'] not in unique_references or unique_references[entry['path']]==entry
 unique_references[entry['path']]=entry
references=list(unique_references.values())
reference_paths=set(unique_references)
audit=json.loads((HERE/'attempt-03-audit.json').read_text());assert set(audit['inputs'])<=paths
assert all(original_bytes[p]==safe_path(p).read_bytes() for p in paths)
direct=[r for r in rows if r['storage']=='direct'];packed=[r for r in rows if r['storage']=='packed']
archive_info=[{'path':relative(p),'bytes':p.stat().st_size,'sha256':sha(p.read_bytes()),'entries':line_counts[c]} for c,p in archives.items()]
map_data={'schema':'truth-attempt03-lossless-auxiliary-map-v1','source_inventory':{'path':relative(inventory_path),'sha256':sha(inventory_path.read_bytes())},'defined_scope':inventory['description'],'original_count':len(rows),'original_bytes':sum(r['bytes'] for r in rows),'direct_count':len(direct),'packed_count':len(packed),'empty_originals':sum(r['bytes']==0 for r in rows),'threshold_bytes':8192,'archives':archive_info,'external_original_copy':inventory['external_copy'],'originals':rows,'already_published_preparation_references':references,'publication_head_authority':'Root catalog reports PR56 at073054c338bc3c589d329fb0ad3866458862f245; this script performs no Git/API verification.'}
map_path=HERE/'attempt-03-AUXILIARY-MAP.json';map_path.write_text(json.dumps(map_data,indent=2,ensure_ascii=False)+'\n')
proof={'schema':'truth-attempt03-lossless-packaging-proof-v1','result':'PASS','prior_failed_packaging_attempt':relative(HERE/'attempt-03-package-attempt1.json'),'input_inventory_sha256':sha(inventory_path.read_bytes()),'map_sha256':sha(map_path.read_bytes()),'archives':archive_info,'checks':{'explicit_unique_safe_inventory':True,'run_and_outer_tree_complete':True,'fixed_record_schema':True,'all_original_hashes_and_lengths_verified_before_and_after':True,'all_packed_text_exact_UTF8_roundtrip':True,'archive_byte_offsets_and_line_numbers_verified':True,'direct_and_packed_disjoint_complete_bijection':True,'all_empty_outputs_retained':True,'all_nonUTF8_and_larger_than8192B_originals_direct':True,'all583_offline_audit_input_refs_resolve':True,'all_preparation_reference_hashes_match':True,'no_original_delete_rename_or_write':True},'counts':{'originals':len(rows),'original_bytes':sum(r['bytes'] for r in rows),'direct':len(direct),'packed':len(packed),'empty_originals':sum(r['bytes']==0 for r in rows),'reference_files':len(references)},'limits':['Packaging supplies bytes, not native reviewer coverage or approval.','No board state, canonical ledger or receipt is packed; those remain their normal direct artifacts.','No review scope is omitted to meet a file-count ceiling. Large raw journals and every original above8KiB remain directly inspectable.','Only the explicitly defined closed03 scope is certified. Earlier runs and future runtime evidence are outside it.']}
(HERE/'attempt-03-packaging-proof.json').write_text(json.dumps(proof,indent=2,ensure_ascii=False)+'\n')
readme=f'''# Closed attempt03 lossless publication map

The physical cadence experiment failed before thrust; its closed evidence remains complete. See [the offline audit](attempt-03-audit.md). This package reduces auxiliary path count by storing small original files as plain UTF-8 JSONL records. It does not summarize, normalize, evaluate or discard their bytes, and it gives no native-review credit.

The explicit [input inventory](attempt-03-publication-inputs.json) names {len(rows)} originals ({sum(r['bytes'] for r in rows):,} bytes): all450 files in bounded-look-200/runs/attempt-03, all133 outer-client files, seven named adjacent release/calibration/poller/scope records, a byte-exact copy of the external root closure catalog, and the three new offline audit files. The [map](attempt-03-AUXILIARY-MAP.json) forms a disjoint complete partition: {len(direct)} direct originals and {len(packed)} packed originals. All{sum(r['bytes']==0 for r in rows)} empty outputs are represented explicitly.

Every original above8192 bytes, every non-UTF8 original and the PNG remains direct. Core raw journals, identity/state/closure, controller/result files and named provenance are also direct irrespective of size. No original local file was deleted or changed. No prior committed archive, preparation file, board card, event ledger or receipt is rewritten or packaged.

## Reading a packed original

Look up its exact worktree-relative path in the map. A packed row gives archive, one-based line number and exact UTF-8 byte offsets. Decode that JSONL record, then encode its `text` field as UTF-8; the resulting byte count and SHA256 must equal the row. The fixed record fields are `path`, `bytes`, `sha256`, `encoding` and `text`. Empty output is `text: ""`. This is content storage, not a conversion of EDN, event or board semantics. A fresh checkout needs no unpacking to read these records and can reconstruct each file independently without executing it.

The archive totals are {line_counts['run']} run entries and {line_counts['outer-client']} outer-client entries. All583 source paths in the offline audit resolve through this map. The root closure catalog's original absolute location, exact hash and local copied path are retained. Existing frozen preparation references remain ordinary committed files and are individually hash-checked here; publication ancestry comes from the root catalog, not a new Git/API query.

## Verification and publication

[The proof](attempt-03-packaging-proof.json) records exact roundtrip, safe paths, fixed schema, unique complete partition, original hash preservation and reference checks. The saved [packager](attempt-03-package.py) consumes the explicit inventory and refuses to overwrite package outputs. Its first invocation stopped on duplicate preparation-reference bookkeeping after producing only the two archives. [That failed invocation](attempt-03-package-attempt1.json) preserves its original source and archive hashes; the repaired pass deduplicates identical references and reuses only byte-identical archives. Its limited directory enumeration only checks that the two named closed trees match that inventory; it never selects arbitrary neighboring files.

`attempt-03-PUBLICATION-FILES.txt` is the exact direct-original plus package-artifact staging list; `attempt-03-SHA256SUMS` hashes every path in it except the checksum file itself. Packed originals must not also be staged separately. Root will verify this mapping independently, stage it, and add canonical card/ledger/provenance paths separately. No Git, provider, JVM or native call occurs in this packaging step. All original review material remains available as plain text or its direct PNG; actual reviewer coverage must still be assessed honestly.
'''
(HERE/'attempt-03-PACKAGING.md').write_text(readme)
artifacts=[inventory_path,Path(__file__).resolve(),HERE/'attempt-03-package-attempt1.json',*new_paths]
staging=sorted({r['path'] for r in direct}|{relative(p) for p in artifacts})
list_path=HERE/'attempt-03-PUBLICATION-FILES.txt';list_path.write_text('\n'.join(staging)+'\n')
sums=HERE/'attempt-03-SHA256SUMS'
sums.write_text(''.join(sha((WT/p).read_bytes())+'  '+p+'\n' for p in staging if p!=relative(sums)))
assert all(original_bytes[p]==safe_path(p).read_bytes() for p in paths)
print(json.dumps({'result':'PASS','counts':proof['counts'],'archive_info':archive_info,'staging_paths':len(staging),'checksum_rows':len(staging)-1,'map_sha256':sha(map_path.read_bytes()),'proof_sha256':sha((HERE/'attempt-03-packaging-proof.json').read_bytes()),'staging_sha256':sha(list_path.read_bytes())},indent=2))
