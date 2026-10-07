#!/usr/bin/env python3
"""Losslessly package closed attempt01/02 auxiliary text; preserve all originals.

Only standard-library file/data operations. No runtime, imports of diagnostic
controllers, process tools, board semantics, Git changes or ledger edits.
"""
from pathlib import Path
import datetime, hashlib, json
HERE=Path(__file__).resolve().parent
WT=HERE.parents[2]
sha=lambda b:hashlib.sha256(b).hexdigest()
fp=lambda p:{'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())}
rel=lambda p:str(p.relative_to(WT))
readj=lambda p:json.loads(p.read_text())
extras={
 '01':['attempt-01-audit.py','attempt-01-audit.json','attempt-01-audit.md','attempt-01-audit-receipt.edn','attempt-01-calibration.json','attempt-01-formation-poller.json','root-attempt01-calibration-check.json','root-execution-release.json'],
 '02':['attempt-02-audit.py','attempt-02-audit.json','attempt-02-audit.md','attempt-02-audit-execution.json','attempt-02-calibration.json','attempt-02-formation-poller.json','root-attempt02-preparation.json','root-attempt02-execution-release.json','root-attempt02-target-selection.json','root-attempt02-closure-check.json','root-client-attempt02.py','root-setup-attempt02.py','root-formation-poller-attempt02.py']}
core={'closed.json','state.json','operations.jsonl','snapshot-requests.edn','snapshot-messages.edn','source-hashes.json','preparation-hashes.json','controller.jsonl','result.json','snapshot-current.stdout','002-runtime.stdout'}
summaries=[]
for attempt in ['01','02']:
 prefix='closed-attempt-'+attempt
 outputs={k:HERE/(prefix+suf) for k,suf in [('archive','-auxiliary.jsonl'),('map','-MAP.json'),('proof','-proof.json'),('readme','-PACKAGING.md'),('files','-PUBLICATION-FILES.txt'),('sums','-SHA256SUMS')]}
 assert not any(p.exists() for p in outputs.values()), 'Refuse overwrite of any packaging output'
 roots=[HERE/f'runs/attempt-{attempt}',HERE/f'attempt-{attempt}-clients']
 originals=sorted([p for root in roots for p in root.rglob('*') if p.is_file()]+[HERE/name for name in extras[attempt]])
 assert len(set(originals))==len(originals) and all(p.is_file() and not p.is_symlink() for p in originals)
 before={rel(p):fp(p) for p in originals}
 audit=readj(HERE/f'attempt-{attempt}-audit.json')
 assert all(name in before and before[name]==value for name,value in audit['inputs'].items()), 'Every exact audit input must be represented'
 direct=[];packed=[]
 for p in originals:
  is_direct=(p.parent==HERE or p.name in core or p.suffix=='.png' or p.stat().st_size>8192)
  try: p.read_bytes().decode('utf-8')
  except UnicodeDecodeError:is_direct=True
  (direct if is_direct else packed).append(p)
 entries=[];offset=0
 with outputs['archive'].open('xb') as out:
  for lineno,p in enumerate(packed,1):
   raw=p.read_bytes();row={'path':rel(p),**before[rel(p)],'content':raw.decode('utf-8')}
   encoded=(json.dumps(row,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')
   out.write(encoded)
   entries.append({'path':rel(p),**before[rel(p)],'storage':'packed','archive':rel(outputs['archive']),'line':lineno,'record_byte_start':offset,'record_byte_end':offset+len(encoded)})
   offset+=len(encoded)
 for p in direct:entries.append({'path':rel(p),**before[rel(p)],'storage':'direct'})
 entries.sort(key=lambda x:x['path'])
 mapping={'schema':'truth-closed-attempt-lossless-map-v1','attempt':attempt,'scope':'Complete closed run/client trees, named audit/closure/path-only setup companions; unrelated preparation archives, other attempts and mutable board/receipts excluded.','path_root':'repository root','input_files':len(originals),'input_bytes':sum(x['bytes'] for x in before.values()),'direct_files':len(direct),'packed_files':len(packed),'archive':{'path':rel(outputs['archive']),**fp(outputs['archive'])},'audit_input_files':len(audit['inputs']),'audit_input_coverage':'complete exact path/byte/hash match','entries':entries}
 with outputs['map'].open('x',encoding='utf-8') as out:
  # One compact entry per line keeps the original mapping readable without
  # adding thousands of indentation-only review lines.
  header={k:v for k,v in mapping.items() if k!='entries'}
  out.write(json.dumps(header,ensure_ascii=False,indent=2)[:-2]+',\n  "entries": [\n')
  out.write(',\n'.join('    '+json.dumps(x,ensure_ascii=False,separators=(',',':')) for x in entries))
  out.write('\n  ]\n}\n')
 # Independent decode of bytes just written, not merely the in-memory source.
 decoded_map=readj(outputs['map']);assert decoded_map==mapping
 archive=outputs['archive'].read_bytes();archive_lines=archive.splitlines(keepends=True)
 recovered={}
 for entry in decoded_map['entries']:
  path=Path(entry['path']);assert not path.is_absolute() and '..' not in path.parts
  assert entry['path'] not in recovered
  if entry['storage']=='direct':payload=(WT/path).read_bytes()
  else:
   line=archive_lines[entry['line']-1]
   assert archive[entry['record_byte_start']:entry['record_byte_end']]==line
   row=json.loads(line);assert row['path']==entry['path'] and row['sha256']==entry['sha256'] and row['bytes']==entry['bytes']
   payload=row['content'].encode('utf-8')
  assert len(payload)==entry['bytes'] and sha(payload)==entry['sha256'] and payload==(WT/path).read_bytes()
  recovered[entry['path']]={'bytes':len(payload),'sha256':sha(payload)}
 assert recovered==before
 assert set(rel(p) for p in direct).isdisjoint(rel(p) for p in packed)
 assert len(archive_lines)==len(packed)
 assert before=={rel(p):fp(p) for p in originals}
 for name,digest in readj(HERE/f'runs/attempt-{attempt}/source-hashes.json').items():assert sha((WT/name).read_bytes())==digest
 publication=sorted(set(direct+list(outputs.values())+[Path(__file__).resolve()]))
 proof={'schema':'truth-closed-attempt-publication-proof-v1','at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'attempt':attempt,'audit_sha256':sha((HERE/f'attempt-{attempt}-audit.json').read_bytes()),'packaging_script':{'path':rel(Path(__file__)),**fp(Path(__file__))},'originals':len(originals),'original_bytes':mapping['input_bytes'],'original_inventory_sha256':sha(json.dumps(before,sort_keys=True,separators=(',',':')).encode()),'map':{'path':rel(outputs['map']),**fp(outputs['map'])},'archive':mapping['archive'],'direct_files':len(direct),'packed_files':len(packed),'empty_files_preserved':sum(x['bytes']==0 for x in before.values()),'complete_disjoint_union':True,'independent_decoding_byte_equal':True,'original_hashes_unchanged':True,'all_audit_inputs_covered':True,'production_pins_verified':186,'publication_paths':len(publication),'outside_inventory_mutable_companions':['kanban/tasks/verify-native-fly-resolve-sculpt-loop-and-graceful-exit.md','kanban/tasks/.events/ledger.edn','.ημ/receipts.edn'],'limits':['Packaging changes representation only; it adds no native qualification and never deletes or changes originals.','Large journals and runtime stdout remain direct plaintext; there is no gzip or semantic filtering.','Path and byte census is not provider coverage, approval or a runtime/performance claim.','Source/preparation references inherited from the preceding preparation PR remain required; complete historical prep is separately represented by HISTORY-MAP.json.']}
 with outputs['proof'].open('x') as out:json.dump(proof,out,indent=2);out.write('\n')
 title='Ready observation without admitted flight' if attempt=='01' else 'One contracting pulse, then reservation rejection'
 readme=f'''# Closed damped attempt{attempt}: {title}

This independent publication group represents **{len(originals)} original files / {mapping['input_bytes']:,} bytes**: {len(direct)} direct originals and {len(packed)} packed auxiliary originals. All original bytes remain unchanged locally. It includes every input of [the closed audit](attempt-{attempt}-audit.json), plus its readable [summary](attempt-{attempt}-audit.md), named operator/provenance records and audit source. Attempt02's operators differ only by the explicitly verified run path.

The [map]({outputs['map'].name}) is a complete, disjoint path partition. Each packed entry resolves to its archive, one-based line and exact UTF-8 record byte span in [the plaintext JSONL archive]({outputs['archive'].name}). Decode `content`, encode UTF-8, and validate original byte length/SHA256. Newlines, trailing whitespace and {proof['empty_files_preserved']} empty files are preserved. Paths are relative to the repository root. Extract only to a new empty destination; never overwrite existing evidence.

Both complete snapshot journals, full runtime stdout, operations, closure/state, source/preparation hashes, final snapshot, controller/result artifacts, PNG and every input over8192 bytes remain direct and readable. Smaller helper command/output, request/result, lock and auxiliary records are bundled without omission. The [proof]({outputs['proof'].name}) records an independent decode/byte comparison against all originals and exact audit inventory coverage. [The packaging script](package-closed-attempts.py) has no runtime or Git effects and refuses existing outputs.

The [publication list]({outputs['files'].name}) contains {len(publication)} repository-relative paths, including this group's six packaging artifacts and the shared script. [Checksums]({outputs['sums'].name}) cover every listed path except the checksum file itself. The script may already be tracked in the first group when the second is published. This is a group census, not an exact PR-diff census; inherited paths and root-owned canonical companion updates change the diff. Mutable native-owner card, Rheos ledger and Receipt River files are explicitly outside this exact raw inventory and are staged separately by root.

The two attempts remain separately publishable. Frozen preparation/historical-preparation packages are inherited unchanged; other diagnostic groups and `__pycache__` are excluded. Packaging supplies no new gameplay proof. Attempt01 did not admit a target; attempt02 measured one contracting released interval and then failed closed on the preserved diagnostic reservation. Neither proves capture, binding, commitment, embodiment, Gate completion or FPS.
'''
 with outputs['readme'].open('x') as out:out.write(readme)
 with outputs['files'].open('x') as out:out.write(''.join(rel(p)+'\n' for p in publication))
 with outputs['sums'].open('x') as out:out.write(''.join(sha(p.read_bytes())+'  '+rel(p)+'\n' for p in publication if p!=outputs['sums']))
 for line in outputs['sums'].read_text().splitlines():
  digest,name=line.split(maxsplit=1);assert sha((WT/name).read_bytes())==digest
 text_bytes=binary_bytes=text_lines=0
 for p in publication:
  data=p.read_bytes()
  try:data.decode('utf-8');text_bytes+=len(data);text_lines+=len(data.splitlines())
  except UnicodeDecodeError:binary_bytes+=len(data)
 summaries.append({'attempt':attempt,'originals':len(originals),'direct':len(direct),'packed':len(packed),'publication_paths':len(publication),'text_bytes':text_bytes,'binary_bytes':binary_bytes,'text_lines':text_lines,'approx_added_diff_bytes_excluding_headers':text_bytes+text_lines,'map_sha256':sha(outputs['map'].read_bytes()),'archive_sha256':sha(outputs['archive'].read_bytes()),'manifest_sha256':sha(outputs['sums'].read_bytes())})
print(json.dumps(summaries,indent=2))
