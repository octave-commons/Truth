#!/usr/bin/env python3
"""Audit closed files and /proc existence only; BB parses EDN as data.

No native contact, signals, project namespaces, driver import or Git operation.
Reports are exclusive-created; all original inputs are verified unchanged.
"""
from pathlib import Path
import collections, datetime, hashlib, json, math, re, struct, subprocess
HERE = Path(__file__).resolve().parent
WT = HERE.parents[2]
RUN = HERE / 'runs/attempt-02'
CLIENTS = HERE / 'attempt-02-clients'
sha = lambda b: hashlib.sha256(b).hexdigest()
readj = lambda p: json.loads(p.read_text())
fp = lambda p: {'bytes': p.stat().st_size, 'sha256': sha(p.read_bytes())}
now = lambda: datetime.datetime.now(datetime.timezone.utc).isoformat()
extra = ['attempt-02-formation-poller.json','attempt-02-calibration.json','root-attempt02-preparation.json','root-attempt02-execution-release.json','root-attempt02-target-selection.json','root-attempt02-closure-check.json','root-client-attempt02.py','root-setup-attempt02.py','root-formation-poller-attempt02.py']
inputs = sorted([p for d in (RUN, CLIENTS) for p in d.rglob('*') if p.is_file()] + [HERE / p for p in extra])
assert all(not p.is_symlink() for p in inputs)
before = {str(p.relative_to(WT)): fp(p) for p in inputs}
assert not (HERE/'attempt-02-audit.json').exists() and not (HERE/'attempt-02-audit.md').exists()
closed = readj(RUN/'closed.json')
assert closed == readj(RUN/'state.json') and closed['stage'] == 'closed' and closed['outcome'] == 'operator-stop'
assert (closed['holds'],closed['aim_invocations'],closed['looks'],closed['frames']) == (1,2,31,1)
assert not any(closed[k] for k in ['cleanup_errors','unreaped_children','budget_exceeded','total_budget_exceeded','key_pending','key_release_uncertain','mouse_pending','mouse_release_uncertain','terminal_commitment'])
assert closed['successful_admission']['geometry']['tick'] == 4884 and closed['successful_admission']['elapsed_seconds'] < 960
ops = [json.loads(s) for s in (RUN/'operations.jsonl').read_text().splitlines()]
bk = {k:[o for o in ops if o['kind']==k] for k in {o['kind'] for o in ops}}
expr = """(require '[clojure.edn :as edn] '[clojure.string :as str] '[cheshire.core :as json])
(println (json/generate-string (mapv (fn [p] (mapv edn/read-string (str/split-lines (slurp p)))) *command-line-args*)))"""
argv = ['/usr/local/bin/bb','-e',expr,str(RUN/'snapshot-requests.edn'),str(RUN/'snapshot-messages.edn')]
parsed = subprocess.run(argv,capture_output=True,timeout=30,check=True)
assert not parsed.stderr
requests,messages = json.loads(parsed.stdout)
starts=[r for r in requests if r['event']=='request-start']; ends=[r for r in requests if r['event']=='request-finish']
raw=(RUN/'snapshot-messages.edn').read_bytes(); lines=raw.splitlines(keepends=True)
consumed={r['id']:r for r in bk['snapshot-consumed']}
assert len(starts)==len(ends)==len(consumed)==274
ids=[f'{i:04}' for i in range(1,275)]
assert [s['id'] for s in starts]==ids
assert [r['id'] for r in bk['snapshot-request']]==[r['id'] for r in bk['snapshot-result']]==list(consumed)==ids
assert [r['event'] for r in requests]==['connection-open']+['request-start','request-finish']*274+['connection-close']
pos=idx=0; frames=[]; stdout_by_hash={}
for s,e in zip(starts,ends):
    count=e['messages']; batch=messages[idx:idx+count]; chunk=b''.join(lines[idx:idx+count])
    assert s['id']==e['id'] and e['outcome']=='ok'
    assert s['byte-start']==e['byte-start']==pos and len(chunk)==e['raw-bytes']==e['byte-end']-pos
    assert raw[pos:e['byte-end']]==chunk
    assert all(m['id']==s['id'] and not any(m.get(k) for k in ['err','ex','root-ex']) for m in batch)
    assert batch[-1].get('status')==['done'] and sum('value' in m for m in batch)==1
    assert all(set(m.get('status',[]))<={'done'} for m in batch)
    out=''.join(m.get('out','')+(m['value']+'\n' if 'value' in m else '') for m in batch)
    encoded=out.encode('utf-8'); assert len(encoded)==consumed[s['id']]['stdout_utf8_bytes']
    stdout_by_hash.setdefault(sha(encoded),[]).append(s['id'])
    assert re.findall(r'^TRUTH_APPROACH_ID (\d+) (\d+)$',out,re.M)==[(str(closed['world']),str(closed['window']))]
    cm=re.findall(r'^TRUTH_COMMITMENT (\d+) (\d+)(.*)$',out,re.M)
    assert len(cm)==1 and cm[0][1]=='0' and not cm[0][2].strip()
    f=next(x.split()[1:] for x in out.splitlines() if x.startswith('TRUTH_FLIGHT '))
    flight={'tick':int(f[0]),'sim_time':float(f[1]),'dt':float(f[2]),'observer':int(f[3]),'position':list(map(float,f[4:7])),'velocity':list(map(float,f[7:10])),'thrust':None if f[10:]==['nil']*3 else list(map(float,f[10:]))}
    targets={}
    for row in out.splitlines():
        if row.startswith('TRUTH_COMMIT_TARGET '):
            a=row.split()[1:];targets[int(a[0])]={'stored':a[2]=='true','ready':a[3]=='true','fresh':a[4]=='true','position':list(map(float,a[5:8])),'velocity':list(map(float,a[8:11]))}
    frame={'id':s['id'],'consumed_at':consumed[s['id']]['at'],'byte_start':pos,'byte_end':e['byte-end'],'flight':flight,'targets':targets}
    frames.append(frame);pos=e['byte-end'];idx+=count
assert pos==len(raw) and idx==len(messages)==len(lines)
assert requests[-1]['completed']==274 and requests[-1]['byte-end']==len(raw)
assert len(raw)<128*1024*1024 and max(e['raw-bytes'] for e in ends)<4*1024*1024
assert out.encode()==(RUN/'snapshot-current.stdout').read_bytes()
copied=[]
for p in inputs:
    if p.name.endswith('-snapshot.txt'):
        digest=sha(p.read_bytes()); assert digest in stdout_by_hash
        copied.append({'path':str(p.relative_to(WT)),'sha256':digest,'journal_ids':stdout_by_hash[digest]})
# All actual hold and coast positions are cross-checked against the fixed raw snapshot stream.
result=readj(RUN/'bounded-damped-flight-01/result.json'); interval=result['intervals'][0]
assert len(result['intervals'])==1 and result['error']=="AssertionError('Non-replenishing residual/pulse reservation exhausted')"
assert result['closure']==closed and not result['unreaped_client_pids'] and 'cleanup_error' not in result
hold=bk['hold-complete'][0]; assert interval['actual_hold']==hold
matches={}
for name in ['before','during','after']:
    matches[name]=[f['id'] for f in frames if f['flight']==hold[name]]
    assert matches[name]
for name in ['target_before','target_after']:
    t=hold[name]; fs=[f for f in frames if f['flight']['tick']==t['tick'] and f['flight']['position']==t['spark_position'] and f['targets'].get(1012,{}).get('position')==t['target_position']]
    assert fs; matches[name]=[f['id'] for f in fs]
    rho=[a-b for a,b in zip(t['target_position'],t['spark_position'])]
    assert rho==t['relative_position'] and math.hypot(*rho)==t['distance_m']
for name in ['released','coast_a','coast_b']:
    t=interval[name]; fs=[f for f in frames if f['flight']['tick']==t['tick'] and f['flight']['position']==t['spark_position'] and f['flight']['velocity']==t['velocity'] and f['flight']['thrust'] is None and f['targets'].get(1012,{}).get('position')==t['target_position']]
    assert fs;matches[name]=[f['id'] for f in fs]
    rho=[a-b for a,b in zip(t['target_position'],t['spark_position'])];assert math.hypot(*rho)==t['distance_m']
assert [hold[k]['tick'] for k in ['before','during','after']]==[5224,5229,5235]
assert [interval[k]['tick'] for k in ['coast_a','coast_b']]==[5237,5250]
assert interval['coast_b']['at']-interval['coast_a']['at']>=1
rho_b=[a-b for a,b in zip(interval['coast_b']['target_position'],interval['coast_b']['spark_position'])]
delta=interval['coast_b']['distance_m']-hold['target_before']['distance_m']
assert delta==interval['range_delta_m']==-761785674397908.0
assert [a-b for a,b in zip(rho_b,hold['target_before']['relative_position'])]==interval['relative_chord']
yaw=math.radians(interval['released']['yaw']);pitch=math.radians(interval['released']['pitch'])
expected=[-math.cos(pitch)*math.cos(yaw),-math.cos(pitch)*math.sin(yaw),-math.sin(pitch)]
assert max(abs(a-b) for a,b in zip(expected,hold['during']['thrust']))<1e-9
assert hold['before']['thrust'] is None and hold['after']['thrust'] is None
clogs={}
for sub in ['bounded-damped-flight-01','aim-01-1012','aim-02-1012']:
    log=[json.loads(s) for s in (RUN/sub/'controller.jsonl').read_text().splitlines()]
    commands=[x for x in log if x['kind']=='command'];reaps=[x for x in log if x['kind']=='reaped']
    assert len(commands)==len(reaps) and all(x['returncode']==0 for x in reaps)
    assert all(next(i for i,v in enumerate(log) if v is c)<next(i for i,v in enumerate(log) if v is r) for c,r in zip(commands,reaps))
    clogs[sub]={'commands':len(commands),'reaps':len(reaps),'all_exit0':True,'pids':[x['pid'] for x in reaps]}
controller=[json.loads(s) for s in (RUN/'bounded-damped-flight-01/controller.jsonl').read_text().splitlines()]
obs=[x for x in controller if x['kind']=='observation']; rates=[]
for a,b in zip(obs,obs[1:]):
    assert b['tick']>a['tick'] and b['at']>a['at'];rates.append({'from_tick':a['tick'],'to_tick':b['tick'],'seconds':b['at']-a['at'],'ticks_per_second':(b['tick']-a['tick'])/(b['at']-a['at'])})
max_tps=max(x['ticks_per_second'] for x in rates);N=math.ceil(4*max_tps)+2;D=1.5e14;r=.8;C=11;tail=r/(1-r)*D;reserve=tail+D*(C+N);margin=.25*obs[-1]['distance_m']
assert reserve>margin and C==result['consumed_tick_envelope']
assert obs[-1]['angle_degrees']<.5 and obs[-1]['velocity_tick_envelope_m']<=D
assert all(x['retention']==r and x['controls']['displacement']==D for x in obs)
assert all((x['yaw'],x['pitch'],x['sensitivity'])==(-107.61000000000001,34.,.01) for x in obs[2:])
spawned={x['label']:x for x in bk['spawn']};reaped={x['label']:x for x in bk['reaped']}
assert len(spawned)==len(bk['spawn'])==len(reaped)==len(bk['reaped'])==110 and set(spawned)==set(reaped)
assert all(spawned[k]['identity']['pid']==reaped[k]['pid'] for k in spawned)
outer=[(p,readj(p)) for p in sorted(CLIENTS.glob('*.json'))]
assert len(outer)==57 and all(x['state']=='reaped' for _,x in outer)
assert [(p.name,x['returncode']) for p,x in outer if x['returncode']!=0]==[('034-flight.json',1)]
rf=sorted((RUN/'results').glob('*.json')); assert len(rf)==len(bk['request'])==122
assert {x['request']['id'] for x in bk['request']}=={p.stem for p in rf} and all(readj(p)['ok'] for p in rf)
boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip();absence={}
for k in ['supervisor','app','xvfb','snapshot_client']:
    v=closed[k];assert v['boot']==boot;absence[k]={**v,'proc_exists_at_audit':Path('/proc',str(v['pid'])).exists()}
assert not any(v['proc_exists_at_audit'] for v in absence.values())
all_pids=set(x['pid'] for x in reaped.values())|{x['pid'] for _,x in outer}|{p for v in clogs.values() for p in v['pids']}|{v['pid'] for v in absence.values()}
remaining=[p for p in sorted(all_pids) if Path('/proc',str(p)).exists()];assert not remaining
source=readj(RUN/'source-hashes.json');prep=readj(RUN/'preparation-hashes.json')
assert len(source)==186 and all(sha((WT/p).read_bytes())==h for p,h in source.items())
assert len(prep)==11 and prep==readj(HERE/'preparation-hashes.json') and all(sha((HERE/p).read_bytes())==h for p,h in prep.items())
manifest=(HERE/'SHA256SUMS').read_text().splitlines()
for row in manifest:
    h,p=row.split(maxsplit=1);assert sha((HERE/p).read_bytes())==h
for a,b in [('root-client.py','root-client-attempt02.py'),('root-setup.py','root-setup-attempt02.py'),('root-formation-poller.py','root-formation-poller-attempt02.py')]:
    assert (HERE/a).read_bytes().replace(b'attempt-01',b'attempt-02')==(HERE/b).read_bytes()
poller=readj(HERE/'attempt-02-formation-poller.json');polls=poller['observations'];selection=readj(HERE/'root-attempt02-target-selection.json')
assert polls[-1]['tick']==4269 and polls[-1]['ready_ids']==['1010','1012'] and not poller['selects_target']
assert all(not p['ready_ids'] for p in polls[:-1])
cal=readj(HERE/'attempt-02-calibration.json');assert cal['state']=='passed'
assert len(bk['damping-knob-expectation'])==17 and len(bk['look-expectation'])==31
pngs=[]
for p in RUN.glob('*.png'):
    data=p.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n';pngs.append({'path':str(p.relative_to(WT)),**fp(p),'dimensions':list(struct.unpack('>II',data[16:24]))})
assert len(pngs)==1
warnings=(RUN/'app.stdout').read_bytes().count(b'K clamped') if (RUN/'app.stdout').exists() else None
report={'schema':'truth-damped-attempt02-offline-audit-v1','at':now(),'outcome':'PASS evidence integrity; one contracting W interval; controller rejected further pulse and closed cleanly','scope':'Closed-file and proc-existence audit only; no native contact, signals, project namespaces, driver import or Git','audit_code':fp(Path(__file__)),'edn_parser':{'argv':argv,'returncode':parsed.returncode,'stderr_bytes':len(parsed.stderr)},'closed':closed,
 'journal':{'reads':len(starts),'messages':len(messages),'utf8_bytes':len(raw),'sha256':sha(raw),'maximum_request_bytes':max(e['raw-bytes'] for e in ends),'exact_contiguous_spans_verified':True,'request_result_consume_order_equal':True,'one_connection_open_close':True,'same_world_window':True,'sampled_global_commitments_all_zero':True,'thrust_sample_count':sum(f['flight']['thrust'] is not None for f in frames),'thrust_tick_min_max':[min(f['flight']['tick'] for f in frames if f['flight']['thrust'] is not None),max(f['flight']['tick'] for f in frames if f['flight']['thrust'] is not None)],'first_tick':frames[0]['flight']['tick'],'last_tick':frames[-1]['flight']['tick'],'copied_snapshot_files':len(copied),'copied_snapshot_bindings':copied},
 'operations':{'events':len(ops),'kind_counts':dict(collections.Counter(x['kind'] for x in ops)),'request_counts':dict(collections.Counter(x['request']['operation'] for x in bk['request'])),'requests_all_ok':True,'spawned_reaped':110,'child_exit_codes':dict(collections.Counter(str(x['returncode']) for x in reaped.values())),'outer_client_exit_counts':dict(collections.Counter(str(x['returncode']) for _,x in outer)),'nested_controllers':clogs,'unique_recorded_child_and_service_pids':len(all_pids),'all_recorded_pid_paths_absent_at_audit':True},
 'owned_process_absence':absence,'poller':{'count':len(polls),'last_no_ready':polls[-2],'first_ready':polls[-1],'closed':poller['ended_at'],'selection':selection,'admission':closed['successful_admission']},
 'measurement':{'target':1012,'D_m_per_tick':D,'retention':r,'dt_seconds':hold['before']['dt'],'initial_aim_seconds':result['aim']['elapsed_seconds'],'initial_aim_looks':len(result['aim']['steps'])-1,'initial_aim_last_angle':result['aim']['steps'][-1]['angle_degrees'],'refinement_seconds':result['refinements'][0]['elapsed_seconds'],'refinement_looks':len(result['refinements'][0]['steps'])-1,'requested_hold_seconds':2,'hold_start':bk['hold-start'][0],'hold_release':bk['hold-release'][0],'hold_complete':hold,'raw_snapshot_binding':matches,'expected_thrust_source_formula':expected,'complete_interval':interval,'range_delta_AU':delta/149597870700.,'range_before_AU':hold['target_before']['distance_m']/149597870700.,'range_coast_b_AU':interval['coast_b']['distance_m']/149597870700.,'released_coast_gap_seconds':interval['coast_b']['at']-interval['coast_a']['at'],'failure':result['error'],'reservation_reconstruction':{'rates':rates,'max_tps':max_tps,'next_tick_envelope':N,'consumed_tick_envelope':C,'single_tail_m':tail,'reserved_m':reserve,'quarter_range_m':margin,'excess_m':reserve-margin,'final_observation':obs[-1]},'pulse_phase_seconds':result['command_phase_elapsed_seconds'],'controller_seconds':result['total_elapsed_seconds']},
 'pngs':pngs,'source_checks':{'production_hashes':len(source),'preparation_pins':len(prep),'frozen_manifest_rows':len(manifest),'manifest_sha256':sha((HERE/'SHA256SUMS').read_bytes()),'path_only_operator_copies_verified':True,'all_equal':True},
 'limits':['One sampled contracting interval is not capture, binding, commitment, embodiment, Gate or FPS proof.','Global commitment is zero in all274 sampled world snapshots, not a claim about every instant.','Same-world relative endpoint differences cancel common frame translation; they are endpoint chords, not path length or an isolated acceleration attribution.','Host camera/config and immutable world are sampled separately. Thrust matches source direction at observed driven sample;11 elapsed ticks do not establish11 continuously driven ticks.','Requested2s hold actually released after the recorded2.2010134s; OS/input scheduling is not a hard2s guarantee.','The non-replenishing reservation is a conservative diagnostic screen using sampled tick throughput, not a physical reachability theorem. No guard was relaxed and no second W was sent.','PID absence is audit-time filesystem evidence with saved identities and reaps; no primary service was contacted or stopped.'], 'inputs':before}
assert before=={str(p.relative_to(WT)):fp(p) for p in inputs}
with (HERE/'attempt-02-audit.json').open('x') as f:json.dump(report,f,indent=2,sort_keys=True);f.write('\n')
text=f'''# Damped attempt02: one contracting pulse, then reservation stop

The closed evidence is internally consistent. At ordinary menu settings **D=1.5e14m/tick and r=0.8**, one W pulse plus released coast reduced the selected natural planet’s range by **{-delta/149597870700.:,.3f}AU** ({-delta:,.0f}m), from {report['measurement']['range_before_AU']:,.3f} to {report['measurement']['range_coast_b_AU']:,.3f}AU. The next pulse was refused by the preserved cumulative reservation screen. This is measured approach, not capture or a three-pulse completion.

- The read-only poller first sampled stored/current-ready1010 and1012 at tick4269. Root explicitly selected1012. Supervisor admission was tick4884 at elapsed{closed['successful_admission']['elapsed_seconds']:.6f}s, before the960s cutoff; the target was stored/current-ready and fresh-handoff false.
- Initial orientation completed in {result['aim']['elapsed_seconds']:.6f}s with27 acknowledged looks. Four earlier calibration looks make31 total. The second aim took{result['refinements'][0]['elapsed_seconds']:.6f}s and required zero looks: its sampled angle was already within0.5°. No retarget or D change occurred.
- Fresh pre-keydown tick5224 had nil thrust and heading0.232616°. Tick5229 observed the source-derived W vector; release was confirmed nil at5235. The requested2s hold’s actual release latency was{bk['hold-release'][0]['release_elapsed_seconds']:.6f}s. Coasts5237/5250 were{report['measurement']['released_coast_gap_seconds']:.6f}s apart with nil thrust. The delta uses the actual5224 hold baseline, not earlier5218 inspection.
- The sampled rate maximum rose to{max_tps:.6f}ticks/s. For the next pulse, N={N}, consumed C={C}, and the single residual tail was{tail:.1f}m. The unchanged formula tail+D(C+N) yielded{reserve:.1f}m, exceeding one quarter of the final range ({margin:.1f}m) by{reserve-margin:.1f}m. The observed final heading{obs[-1]['angle_degrees']:.6f}° passed; the reservation assertion refused the second W. No extra tail was added per pulse and no C reset occurred.
- The controller returned1 with the explicit reservation error and completed STOP. Supervisor closed at{closed['ended_at']}, elapsed{closed['elapsed_seconds']:.6f}s, with no cleanup errors, pending input, uncertain release, unreaped child, terminal-commitment marker or lease overrun. Pulse phase was{result['command_phase_elapsed_seconds']:.6f}s; total controller duration{result['total_elapsed_seconds']:.6f}s.
- All274 persistent-client reads have matching IDs, contiguous UTF-8 spans and acknowledged consumption; {len(messages)} messages occupy{len(raw):,}bytes. Same world/window throughout. All122 runner operations succeeded. All110 native child spawns have matching reaps;57 outer clients comprise56 exit0 and the expected flight exit1. Nested controllers reaped all70 direct children with exit0. All{len(all_pids)} unique recorded PID paths, including the four owned service identities, were absent at audit time.
- All186 source hashes,11 preparation pins and68 frozen manifest rows match. Attempt02 setup/poller/root-client copies differ only by the intended attempt path. One young1280×720 PNG is preserved with its hash. All input bytes were unchanged across this audit.

All274 sampled global commitment markers are zero; this does not prove absence between observations. No binding, commitment, sculpt, embodiment or Gate outcome is established. Relative endpoint chords remove common recenter translation but do not isolate gravity, target motion, or thrust contributions. The reservation screen is diagnostic, not a physical impossibility proof. Source/config/model effects and future approach are unqualified by this single interval.

Exact timestamps, formulas, byte spans, PNG hash, identities and input inventory are in [the JSON audit](attempt-02-audit.json); [the standalone audit source](attempt-02-audit.py) parses EDN as data only. Original records and preparation remain untouched.
'''
with (HERE/'attempt-02-audit.md').open('x') as f:f.write(text)
print(json.dumps({'audit':fp(HERE/'attempt-02-audit.json'),'summary':fp(HERE/'attempt-02-audit.md'),'inputs':len(inputs),'raw_bytes':len(raw),'messages':len(messages),'rates':rates,'reservation':reserve,'margin':margin,'unique_pids':len(all_pids)}))
