#!/usr/bin/env python3
"""Offline data-only audit. Never imports a game/driver or contacts nREPL.
Writes only new audit reports; Babashka reads EDN data without project namespaces.
"""
from pathlib import Path
import collections, datetime, hashlib, json, math, re, subprocess

HERE = Path(__file__).resolve().parent
WT = HERE.parents[2]
RUN = HERE.parent / 'bounded-look-200/runs/attempt-03'
CLIENTS = HERE / 'attempt-03-clients'
assert not (HERE/'attempt-03-audit.json').exists() and not (HERE/'attempt-03-audit.md').exists(), 'Audit reports already exist; never overwrite closed evidence'
sha = lambda b: hashlib.sha256(b).hexdigest()
readj = lambda p: json.loads(p.read_text())
linesj = lambda p: [json.loads(s) for s in p.read_text().splitlines()]
seconds = lambda a, b: (datetime.datetime.fromisoformat(b)-datetime.datetime.fromisoformat(a)).total_seconds()
inputs = sorted(p for d in [RUN, CLIENTS] for p in d.rglob('*') if p.is_file())
assert all(not p.is_symlink() for p in inputs)
fingerprints = {str(p.relative_to(WT)): {'bytes': p.stat().st_size, 'sha256': sha(p.read_bytes())} for p in inputs}
closed = readj(RUN/'closed.json')
ops = linesj(RUN/'operations.jsonl')
raw = (RUN/'snapshot-messages.edn').read_bytes()
# This subprocess only parses two immutable EDN journals as data.
expr = '''(require '[clojure.edn :as edn] '[clojure.string :as str] '[cheshire.core :as json])
(let [read-lines (fn [p] (mapv edn/read-string (str/split-lines (slurp p))))]
 (println (json/generate-string {:requests (read-lines (first *command-line-args*))
                               :messages (read-lines (second *command-line-args*))})))'''
p = subprocess.run(['/usr/local/bin/bb','-e',expr,str(RUN/'snapshot-requests.edn'),str(RUN/'snapshot-messages.edn')],capture_output=True,timeout=30,check=True)
assert not p.stderr, p.stderr.decode()
data=json.loads(p.stdout)
requests=data['requests']; messages=data['messages']; raw_lines=raw.splitlines(keepends=True)
starts=[v for v in requests if v['event']=='request-start']
ends=[v for v in requests if v['event']=='request-finish']
assert len(starts)==len(ends)==178 and len(messages)==len(raw_lines)==5041
assert [s['id'] for s in starts]==[f'{n:04}' for n in range(1,179)]
bykind={k:[v for v in ops if v['kind']==k] for k in {v['kind'] for v in ops}}
consumed={v['id']:v for v in bykind['snapshot-consumed']}
assert set(consumed)=={s['id'] for s in starts}
assert [v['id'] for v in bykind['snapshot-request']]==[v['id'] for v in bykind['snapshot-result']]==list(consumed)
position=0; message_index=0; frames={}; spans=[]
for s,e in zip(starts,ends):
 assert s['id']==e['id'] and e['outcome']=='ok' and s['byte-start']==e['byte-start']==position
 count=e['messages']; chunk=b''.join(raw_lines[message_index:message_index+count]); batch=messages[message_index:message_index+count]
 assert len(chunk)==e['raw-bytes']==e['byte-end']-position and raw[position:e['byte-end']]==chunk
 assert all(m['id']==s['id'] and not m.get('err') and not m.get('ex') and not m.get('root-ex') for m in batch)
 assert batch[-1].get('status')==['done'] and sum('value' in m for m in batch)==1
 assert all(set(m.get('status',[]))<={'done'} for m in batch)
 out=''.join(m.get('out','')+(m['value']+'\n' if 'value' in m else '') for m in batch)
 assert len(out.encode())==consumed[s['id']]['stdout_utf8_bytes']
 ident=re.findall(r'^TRUTH_APPROACH_ID (\d+) (\d+)$',out,re.M)
 assert ident==[(str(closed['world']),str(closed['window']))]
 commit=re.findall(r'^TRUTH_COMMITMENT (\d+) (\d+)(.*)$',out,re.M)
 assert len(commit)==1 and commit[0][1]=='0' and not commit[0][2].strip()
 flight=next(x.split()[1:] for x in out.splitlines() if x.startswith('TRUTH_FLIGHT '))
 assert flight[10:]==['nil']*3
 frames[s['id']]={'text':out,'tick':int(flight[0]),'start_wall_ms':s['wall-ms'],'consumed_at':consumed[s['id']]['at']}
 spans.append({'id':s['id'],'byte_start':position,'byte_end':e['byte-end'],'messages':count,'stdout_utf8_bytes':len(out.encode()),'tick':int(flight[0]),'elapsed_ms':e['elapsed-ms']})
 position=e['byte-end'];message_index+=count
assert position==len(raw) and message_index==len(messages)
assert requests[0]['event']=='connection-open' and requests[-1]['event']=='connection-close'
assert requests[-1]['completed']==178 and requests[-1]['byte-end']==len(raw)
assert len(raw)<128*1024*1024 and max(e['raw-bytes'] for e in ends)<4*1024*1024 and len(ends)<2048
assert frames['0178']['text'].encode()==(RUN/'snapshot-current.stdout').read_bytes()==(RUN/'bounded-cadence-01/001-inspect-snapshot.txt').read_bytes()

def geometry(text):
 c=next(x.split()[1:] for x in text.splitlines() if x.startswith('TRUTH_INPUT_STATE '))
 f=next(x.split()[1:] for x in text.splitlines() if x.startswith('TRUTH_FLIGHT '))
 t=next(x.split()[1:] for x in text.splitlines() if x.startswith('TRUTH_COMMIT_TARGET 1010 '))
 spark=list(map(float,f[4:7])); target=list(map(float,t[5:8])); delta=[a-b for a,b in zip(target,spark)]; distance=math.hypot(*delta); direction=[v/distance for v in delta]
 yaw,pitch=map(float,c[4:6]); yr,pr=map(math.radians,[yaw,pitch]); forward=[-math.cos(pr)*math.cos(yr),-math.cos(pr)*math.sin(yr),-math.sin(pr)]
 return {'tick':int(f[0]),'sim_time':float(f[1]),'dt':float(f[2]),'spark_position':spark,'target_position':target,'relative_position':delta,'distance_m':distance,'yaw':yaw,'pitch':pitch,'D':float(c[3]),'stored_candidate':t[2]=='true','ready_to_commit':t[3]=='true','fresh_handoff':t[4]=='true','heading_degrees':math.degrees(math.acos(max(-1,min(1,sum(a*b for a,b in zip(direction,forward))))))}
first=geometry(frames['0177']['text']);last=geometry(frames['0178']['text']);aim=readj(RUN/'aim-02-1010/result.json');a=aim['steps'][-1]
assert first['tick']==a['tick'] and abs(first['heading_degrees']-a['angle_degrees'])<1e-12
assert first['yaw']==last['yaw'] and first['pitch']==last['pitch'] and first['D']==last['D']==3.75e13
cadence=readj(RUN/'bounded-cadence-01/result.json'); ce=linesj(RUN/'bounded-cadence-01/controller.jsonl')
assert cadence['outcome']=='failed' and cadence['intervals']==[] and cadence['closure']==closed
assert cadence['error']=="AssertionError('Fresh heading exceeds0.5 degrees; no automatic aim')"
assert [v['operation'] for v in ce if v['kind']=='command']==['inspect','stop']
assert not bykind.get('hold-complete') and closed['holds']==0
last_aim=readj(CLIENTS/'022-aim-refresh.json'); cadence_start=ce[0]['at']
timeline={'final_aim_snapshot':{**{k:v for k,v in frames['0177'].items() if k!='text'},**first},'final_aim_geometry_logged_at':next(x['at'] for x in linesj(RUN/'aim-02-1010/controller.jsonl') if x['kind']=='geometry' and x['tick']==5056),'aim_end_client_reaped_at':linesj(RUN/'aim-02-1010/controller.jsonl')[-1]['at'],'outer_aim_client_reaped_at':last_aim['ended_at'],'cadence_first_command_at':cadence_start,'rejected_snapshot':{**{k:v for k,v in frames['0178'].items() if k!='text'},**last},'cadence_inspect_client_reaped_at':ce[1]['at'],'stop_command_at':next(v['at'] for v in ce if v['kind']=='command' and v['operation']=='stop'),'closed_at':closed['ended_at']}
delta_time=seconds(frames['0177']['consumed_at'],frames['0178']['consumed_at']); chord=[b-a for a,b in zip(first['relative_position'],last['relative_position'])]
derived={'snapshot_receive_interval_s':delta_time,'world_tick_delta':last['tick']-first['tick'],'observed_ticks_per_receive_second':(last['tick']-first['tick'])/delta_time,'outer_aim_exit_to_cadence_command_s':seconds(last_aim['ended_at'],cadence_start),'cadence_inspect_command_to_reap_s':seconds(cadence_start,ce[1]['at']),'heading_increase_degrees':last['heading_degrees']-first['heading_degrees'],'heading_error_average_degrees_per_receive_second':(last['heading_degrees']-first['heading_degrees'])/delta_time,'relative_endpoint_chord_m':chord,'relative_chord_length_m':math.hypot(*chord),'range_increase_m':last['distance_m']-first['distance_m'],'conditional_linear_margin_s':(0.5-first['heading_degrees'])/((last['heading_degrees']-first['heading_degrees'])/delta_time)}
# Reap receipts are evidence, separate from present /proc absence.
spawned={v['label']:v for v in bykind['spawn']}; reaped={v['label']:v for v in bykind['reaped']}
assert len(spawned)==len(reaped)==61 and set(spawned)==set(reaped)
assert all(spawned[k]['identity']['pid']==reaped[k]['pid'] for k in spawned)
outer=[readj(p) for p in sorted(CLIENTS.glob('*.json'))]
assert len(outer)==37 and all(v['state']=='reaped' and v['returncode']==0 for v in outer)
controllers=[RUN/'aim-01-1010/controller.jsonl',RUN/'aim-02-1010/controller.jsonl',RUN/'bounded-cadence-01/controller.jsonl']
controller_reaps=[]
for p in controllers:
 events=linesj(p); commands=[v for v in events if v['kind']=='command']; rr=[v for v in events if v['kind']=='reaped']
 assert len(commands)==len(rr) and all(v['returncode']==0 for v in rr)
 controller_reaps.extend(rr)
boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip(); absent={}
for key in ['supervisor','app','xvfb','snapshot_client']:
 value=closed[key]; assert value['boot']==boot
 absent[key]={**value,'proc_exists_at_audit':Path('/proc',str(value['pid'])).exists()}
assert all(not v['proc_exists_at_audit'] for v in absent.values())
all_pids={v['identity']['pid'] for v in spawned.values()}|{v['pid'] for v in outer+controller_reaps}|{closed['supervisor']['pid']}
remaining_pids=[p for p in sorted(all_pids) if Path('/proc',str(p)).exists()]
assert not remaining_pids
source=readj(RUN/'source-hashes.json');prep=readj(RUN/'preparation-hashes.json')
assert len(source)==186 and all(sha((WT/p).read_bytes())==h for p,h in source.items())
assert len(prep)==6 and all(sha((RUN.parent.parent/p).read_bytes())==h for p,h in prep.items())
results=sorted((RUN/'results').glob('*.json'))
assert len(results)==91 and all(readj(p)['ok'] for p in results)
request_ids={v['request']['id'] for v in bykind['request']};assert request_ids=={p.stem for p in results}
poller=readj(HERE/'attempt-03-formation-poller.json'); polls=poller['observations']
pollgaps=[seconds(a['at'],b['at']) for a,b in zip(polls,polls[1:])]
firstaim=readj(RUN/'aim-01-1010/result.json')
report={'schema':'truth-attempt03-offline-audit-v1','at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Closed saved bytes and current read-only /proc absence; no native service contact, game execution, input or source mutation','outcome':'PASS-evidence-integrity; cadence-measurement-failed-before-thrust','production_base':closed['base'],'closed':closed,'journal':{'connection_open':requests[0],'connection_close':requests[-1],'successful_reads':178,'decoded_messages':len(messages),'raw_utf8_bytes':len(raw),'raw_sha256':sha(raw),'requests_utf8_bytes':(RUN/'snapshot-requests.edn').stat().st_size,'exact_contiguous_spans_verified':True,'maximum_request_raw_bytes':max(e['raw-bytes'] for e in ends),'request_cap':2048,'response_cap_bytes':4194304,'run_cap_bytes':134217728,'all_global_commitment_counts_zero':True,'all_published_thrust_nil':True,'spans':spans},'operations':{'events':len(ops),'kinds':dict(collections.Counter(v['kind'] for v in ops)),'request_operations':dict(collections.Counter(v['request']['operation'] for v in bykind['request'])),'successful_requests':91,'failed_requests':0,'input_observations_matched':dict(collections.Counter(str(v['matched']) for v in bykind['input-observation'])),'subprocesses_spawned_and_reaped':61,'subprocess_exit_codes':dict(collections.Counter(str(v['returncode']) for v in reaped.values())),'outer_clients_reaped':37,'controller_clients_reaped':len(controller_reaps),'unique_pid_paths_now_absent':len(all_pids)},'owned_process_absence':absent,'source_verification':{'current_186_source_hashes_match_run_pins':True,'current_six_preparation_hashes_match_run_pins':True,'limits':'Hash equality to recorded pins, not a new Git ancestry/loaded-namespace verification'},'timeline':timeline,'derived':derived,'first_aim':{'steps':len(firstaim['steps']),'outcome':firstaim['outcome'],'final':firstaim['steps'][-1]},'poller':{'count':len(polls),'maximum_polls':poller['maximum_polls'],'cadence_seconds':30,'start_interval_min_s':min(pollgaps),'start_interval_max_s':max(pollgaps),'last_no_ready':polls[-2],'first_observed_ready':polls[-1],'reason':poller['reason'],'selection':False,'gap_last_poll_to_first_admission_s':closed['successful_admission']['elapsed_seconds']-polls[-1]['elapsed']},'limits':['Snapshots are sparse; receive timing is not the exact simulation fold time or display FPS.','Same yaw/pitch with changed relative endpoints explains the sampled heading change geometrically; no exact crossing instant or causal ejection/formation assertion.','The approximate linear heading-margin duration is descriptive of these two endpoints, not a guaranteed safe interval or interception law.','The cadence assertion failed before observation logging; the rejected angle is independently derived from its preserved raw snapshot.','Both aims demonstrate alignment at one published observation only. No physical approach, sustained overlap, commitment, sculpt, embodiment or Gate proof.','Fresh handoff remains false for selected1010 in the cited frames; stored-candidate/current-ready is the explicitly scoped game admission.'],'inputs':fingerprints}
assert fingerprints=={str(p.relative_to(WT)):{'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())} for p in inputs}
(HERE/'attempt-03-audit.json').write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
md=f'''# Closed attempt03: re-aim to cadence boundary

Observed evidence integrity passes. The intended physical-flight measurement failed before its first W request. Supervisor closure is `operator-stop`; cadence outcome is `failed` with the preserved heading assertion. These are different records, not a successful approach.

| Saved boundary (UTC) | Observation |
| --- | --- |
| {timeline['final_aim_snapshot']['consumed_at']} | Snapshot0177, tick5056, heading {first['heading_degrees']:.11f}° |
| {timeline['final_aim_geometry_logged_at']} | Aim controller records that geometry |
| {timeline['aim_end_client_reaped_at']} | Inner aim-end CLI reaped |
| {timeline['outer_aim_client_reaped_at']} | Outer re-aim process reaped |
| {timeline['cadence_first_command_at']} | Cadence first inspect command starts |
| {timeline['rejected_snapshot']['consumed_at']} | Snapshot0178, tick5105, derived heading {last['heading_degrees']:.11f}° |
| {timeline['stop_command_at']} | Cadence requests owned stop after assertion |
| {timeline['closed_at']} | All owned runtime children closed |

The camera stayed at yaw−103.78°, pitch26.3°. The selected target and Spark relative endpoints changed across49 simulation ticks. Range increased by {derived['range_increase_m']:.6e}m, and the relative endpoint chord was {derived['relative_chord_length_m']:.6e}m. The receive-to-receive interval was {delta_time:.6f}s. Of that, {derived['outer_aim_exit_to_cadence_command_s']:.6f}s elapsed after the outer aim exited and before the cadence inspect began; the cadence inspect command itself took {derived['cadence_inspect_command_to_reap_s']:.6f}s. This is a material operator handoff gap, not evidence that the fresh inspection alone consumed14s.

The measured endpoint heading-error increase averages {derived['heading_error_average_degrees_per_receive_second']:.6f}°/receive-second. Linear interpolation would use the initial0.40814° margin in about {derived['conditional_linear_margin_s']:.2f}s, but neither the exact threshold-crossing time nor future angular rate was observed. A subsequent separately reviewed experiment can remove the handoff gap by completing released baseline reads before its one aim, then immediately applying the unchanged fresh-heading guard. This run does not justify widening that guard or claiming a successful future pulse.

## Integrity and closure

-178 successful reads;5041 decoded nREPL messages;4,479,322 raw UTF-8 bytes. Every per-request span is contiguous and exact; all178 request/result/consume records agree, all terminal statuses are done, and the persistent connection closes after178. Each snapshot carries the same world1308072265/window134192227468400.
-91 successful runner operations:48inspect,29look,7click,2tap,2aim-start,2aim-end,1frame; no hold request. All published thrust vectors are nil. Global commitment count is zero in all178 frames.
-61 spawned children have matching reap receipts;37 outer clients and {len(controller_reaps)} aim/cadence CLI children have exit0 reap receipts. The cadence controller itself failed by assertion, reported by root tool86609 rc1. Cleanup flags, uncertain inputs and unreaped lists are empty. All four exact owned PID paths were independently absent at audit under the same boot; {len(all_pids)} distinct recorded child/outer PID paths were also absent.
-Current186 production-file bytes and six preparation pins match the run's preserved hashes. This is offline hash verification, not new native execution or Git-based ancestry proof.
-The automatic formation poller made15 reads, with start intervals {min(pollgaps):.6f}–{max(pollgaps):.6f}s. It observed no ready candidate at tick3989 and four at tick4096, then stopped without selection. No exact formation instant is inferred. First actual supervisor admission of root-selected1010 was tick4545, {report['poller']['gap_last_poll_to_first_admission_s']:.6f}s after the last poll's elapsed reading.

## Coverage limits

Selected1010 remained stored+ready and fresh-handoff-false in the two cited snapshots. Two successful finite aims establish only single-observation orientation. Zero holds means no measured physical approach, binding, commitment, sculpt, embodiment or Gate. Sparse snapshots and host readbacks are not consumer-fold or native-FPS proof. Complete input hashes and per-read raw byte spans are in [the JSON audit](attempt-03-audit.json); the original journals and all client evidence remain unchanged.
'''
# Keep normal CommonMark spacing for the readable evidence list.
md=md.replace('\n-', '\n\n- ')
(HERE/'attempt-03-audit.md').write_text(md)
print(json.dumps({'audit_json_sha256':sha((HERE/'attempt-03-audit.json').read_bytes()),'audit_md_sha256':sha((HERE/'attempt-03-audit.md').read_bytes()),'input_files':len(inputs),'derived':derived,'controller_clients':len(controller_reaps),'subprocess_reaped_codes':report['operations']['subprocess_exit_codes']},indent=2))
