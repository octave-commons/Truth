#!/usr/bin/env python3
"""Offline closed-attempt audit; reads records only and writes its two reports."""
from pathlib import Path
import collections, datetime, hashlib, json, math, re
ROOT = Path('/home/err/spaces/foresight/.worktrees/truth-bounded-look-200')
P = ROOT / '.ημ/diagnostics/bounded-look-200'
R = P / 'runs/attempt-02'
C = P / 'attempt-02-clients'
checks = 0
def check(value, label):
    global checks
    checks += 1
    if not value:
        raise AssertionError(label)
def sha(data):
    return hashlib.sha256(data).hexdigest()
def readj(path):
    return json.loads(path.read_text())
def row(path):
    data = path.read_bytes()
    return {'path':str(path.relative_to(P)), 'bytes':len(data), 'sha256':sha(data)}
def rows(kind):
    return [o for o in ops if o['kind'] == kind]
def approx(a,b):
    return math.isclose(a,b,rel_tol=1e-13,abs_tol=1e-9)
def norm(xs):
    return math.sqrt(sum(x*x for x in xs))
main_names = (P/'PREPARATION-FILES.txt').read_text().splitlines()
top = sorted(q for q in P.iterdir() if q.is_file() and
             (q.name.startswith('root-attempt02-') or q.name in
              ['attempt-02-calibration.json','attempt-02-client-directory-preflight.json',
               'attempt-02-formation-poller.json','attempt-02-root-client.py']))
inputs = sorted(set([P/n for n in main_names] + top + [
    q for folder in (R,C,P/'attempt-02-preparation')
    for q in folder.rglob('*') if q.is_file() and '__pycache__' not in q.parts]),
    key=lambda q:str(q.relative_to(P)))
inventory = [row(q) for q in inputs]
closed=readj(R/'closed.json')
ops=[json.loads(s) for s in (R/'operations.jsonl').read_text().splitlines()]
origin=closed['supervisor_monotonic_started']
check(readj(R/'state.json')==closed,'closed state matches final state')
check(closed['outcome']=='operator-stop','outcome')
check(closed['elapsed_seconds']<1470 and not closed['budget_exceeded'] and not closed['total_budget_exceeded'],'closed within lease')
for k in ['cleanup_errors','unreaped_children']:
    check(closed[k]==[],k)
for k in ['key_release_uncertain','mouse_pending','mouse_release_uncertain']:
    check(closed[k] is False,k)
for k in ['key_pending','active_aim','aim_deadline','terminal_commitment']:
    check(closed[k] is None,k)
check([closed[k] for k in ['frames','looks','holds','aim_invocations']]==[2,29,2,1],'counters')
check(closed['aim_target']==1010,'fixed target')
root_close=readj(P/'root-attempt02-closure.json')
check(root_close['closed']==closed,'root closure matches')
check(root_close['root_principal_pids_absent']=={str(closed[k]['pid']):True for k in ['supervisor','xvfb','app','snapshot_client']},'root recorded absence')
launchpins=readj(R/'source-hashes.json')
source=readj(P/'SOURCE-EQUALITY.json')
check(len(launchpins)==186 and source['production_base']==closed['base'],'source base')
for name,digest in launchpins.items():
    check(sha((ROOT/name).read_bytes())==digest==source['files_by_path'][name]['sha256'],'source pin '+name)
prep=readj(P/'preparation-hashes.json')
check(prep==readj(R/'preparation-hashes.json') and len(prep)==6,'runtime six pins')
for name,digest in prep.items():
    check(sha((P/name).read_bytes())==digest,'preparation pin '+name)
manifest_counts={}
for folder in (P,P/'attempt-02-preparation'):
    sums=(folder/'SHA256SUMS').read_text().splitlines()
    manifest_counts[str(folder.relative_to(P))]=len(sums)
    for line in sums:
        digest,name=line.split('  ',1)
        check(sha((folder/name).read_bytes())==digest,'manifest '+name)
check(manifest_counts=={'.':16,'attempt-02-preparation':10},'manifest sizes')
repair=readj(P/'manifest-shape-repair.json')
for key in ['original_manifest','original_closed_checksums','original_inventory','original_static']:
    item=repair[key]
    check(sha(item['utf8'].encode())==item['sha256'],'preserved bad manifest evidence '+key)
old=json.loads(repair['original_manifest']['utf8'])
check('at' in old and all(old['files'][n]['sha256']==h for n,h in prep.items()),'manifest shape only repair')
review=readj(P/'root-attempt02-client-review.json')
check(sha((P/'attempt-02-root-client.py').read_bytes())==review['sha256'],'later thin client pin')
# Explicit lexical extraction of EDN string fields, never eval EDN or import a driver.
string_pattern=r'"(?:[^"\\]|\\.)*"'
def string_field(line,key):
    match=re.search(':'+re.escape(key)+r' ('+string_pattern+')',line)
    return json.loads(match[1]) if match else None
def integer_field(line,key):
    values=re.findall(':'+re.escape(key)+r' ([0-9]+)',line)
    check(len(values)==1,'one journal number '+key)
    return int(values[0])
raw=(R/'snapshot-messages.edn').read_bytes()
by=collections.defaultdict(list)
for data in raw.splitlines(keepends=True):
    line=data.decode('utf-8')
    check(line.startswith('{') and line.endswith('}\n'),'complete message framing')
    key=string_field(line,'id')
    check(key is not None,'message id')
    by[key].append((data,line))
journal=(R/'snapshot-requests.edn').read_text().splitlines()
starts=[l for l in journal if ':event :request-start' in l]
finishes=[l for l in journal if ':event :request-finish' in l]
check(len(starts)==len(finishes)==209 and len(journal)==420,'209 complete requests plus connection boundaries')
check(sum(':event :connection-open' in l for l in journal)==1 and sum(':event :connection-close' in l for l in journal)==1,'connection boundaries')
snaps={}; end=0
for number,(start,finish) in enumerate(zip(starts,finishes),1):
    sid=f'{number:04d}'
    check(string_field(start,'id')==string_field(finish,'id')==sid,'ordered snapshot ids')
    bs=integer_field(finish,'byte-start');be=integer_field(finish,'byte-end')
    nb=integer_field(finish,'raw-bytes');nm=integer_field(finish,'messages')
    check(integer_field(start,'byte-start')==bs==end and be-bs==nb,'contiguous byte span')
    check(raw[bs:be]==b''.join(b for b,l in by[sid]) and nm==len(by[sid]),'exact raw messages')
    check(':outcome :ok' in finish,'worker outcome')
    lines=[l for b,l in by[sid]]
    out=''.join(string_field(l,'out') or '' for l in lines)
    values=[string_field(l,'value') for l in lines if string_field(l,'value') is not None]
    check(len(values)==1 and sum(':status ["done"]' in l for l in lines)==1,'one value/done')
    check(not any(string_field(l,'err') is not None for l in lines),'no nREPL error')
    def marker(name):
        found=re.findall('^'+name+r' (.+)$',out,re.M)
        check(len(found)==1,'unique marker '+name)
        return found[0].split()
    f=marker('TRUTH_FLIGHT');ctrl=marker('TRUTH_INPUT_STATE')
    tick=int(f[0])
    check(marker('TRUTH_COMMITMENT')==[str(tick),'0'],'same-world zero global commitment')
    check(marker('TRUTH_APPROACH_ID')==[str(closed['world']),str(closed['window'])],'fixed identity')
    value=values[0]
    check(':demo/scenario :nebula' in value and ':demo/fixture? false' in value,'ordinary nebula')
    check(f':pid {closed["app"]["pid"]}' in value and ':max-heap-bytes 2147483648' in value,'runtime identity/heap')
    check(':published-focus-overlap? true' not in value,'no published focus-body overlap')
    flight={'tick':tick,'sim_time':float(f[1]),'dt':float(f[2]),'observer':int(f[3]),
            'position':list(map(float,f[4:7])),'velocity':list(map(float,f[7:10])),
            'thrust':None if f[10:]==['nil']*3 else list(map(float,f[10:]))}
    targets={int(t[0]):t for t in [l.split() for l in re.findall(r'^TRUTH_COMMIT_TARGET (.+)$',out,re.M)]}
    snaps[sid]={'id':sid,'tick':tick,'flight':flight,'controls':ctrl,'targets':targets,
                'out':out,'value':value,'text':out+value+'\n','byte_start':bs,'byte_end':be,'messages':nm}
    end=be
check(end==len(raw),'all raw bytes covered once')
check(len(snaps)<=2048 and len(raw)<134217728,'snapshot count and raw byte caps')
check(rows('ready')[0]['elapsed_seconds']<180,'native startup ready within180')
for kind in ['snapshot-request','snapshot-result','snapshot-consumed']:
    check([o['id'] for o in rows(kind)]==list(snaps),'ops snapshot order '+kind)
for op in rows('snapshot-consumed'):
    check(op['stdout_utf8_bytes']==len(snaps[op['id']]['text'].encode()),'consumed stdout bytes')
check((R/'snapshot-current.stdout').read_text()==snaps['0209']['text'],'final snapshot exact reconstruction')
bytext={s['text']:s for s in snaps.values()}
for q in C.glob('*-snapshot.txt'):
    check(q.read_text() in bytext,'copied snapshot matches complete raw '+q.name)
consumed=None
for op in ops:
    if op['kind']=='snapshot-consumed':
        consumed=snaps[op['id']]
    if op['kind'] in ['hold-observation','hold-release-observation']:
        check(op['state']==consumed['flight'],'hold observation equals preceding raw world')
    if op['kind']=='input-observation':
        state=op['state'];c=consumed['controls']
        check([state['mode'],str(state['free']).lower(),state['domain']]==c[:3],'ack controls raw')
        check(all(approx(state[k],float(c[i])) for k,i in [('displacement',3),('yaw',4),('pitch',5),('sensitivity',6)]),'ack numeric controls raw')
requests=rows('request')
request_mix=collections.Counter(o['request']['operation'] for o in requests)
check(len(requests)==102,'IPC count')
for op in requests:
    request=op['request'];rid=request['id']
    check(readj(R/'requests'/f'{rid}.json')==request,'IPC request persisted')
    check(readj(R/'results'/f'{rid}.json')['ok'] is True,'IPC success')
check(len(list((R/'requests').glob('*.json')))==len(list((R/'results').glob('*.json')))==102,'IPC full inventory')
commands=rows('command');spawns=rows('spawn');reaps=rows('reaped')
check(len(commands)==len(spawns)==len(reaps)==66,'balanced native children')
check({o['identity']['pid'] for o in spawns}=={o['pid'] for o in reaps},'every native child reaped')
check(all(o['elapsed_seconds']<closed['elapsed_seconds'] for o in reaps),'native children reaped before closure')
check(collections.Counter(o['returncode'] for o in reaps)=={0:65,143:1},'native exit codes')
for op in commands:
    check(op['cwd']==str(ROOT) and op['port']==7898 and op['display'] in [None,':1'],'owned command surface')
    for key in ['stdout','stderr']:
        check((R/op[key]).stat().st_size<op['file_limit_bytes'],'child file limit')
clients=[]
for q in sorted(C.glob('*.json')):
    a=readj(q)
    check(a['state']=='reaped' and a['returncode']==0,'outer client reaped successfully')
    if a.get('source_pins'):
        check(all(sha((P/n).read_bytes())==h for n,h in a['source_pins'].items()),'outer client source pins')
        check(a['started_at']>review['at'],'review before thin-client use')
    if a.get('snapshot_copied'):
        qcopy=P/Path(a['snapshot_path']).relative_to(P)
        check(sha(qcopy.read_bytes())==a['snapshot_sha256'] and qcopy.stat().st_size==a['snapshot_bytes'],'thin snapshot copy pin')
    clients.append({'path':str(q.relative_to(P)),'pid':a['pid'],'returncode':a['returncode'],
                    'start':a['started_monotonic']-origin,'end':a['started_monotonic']+a['elapsed_seconds']-origin,
                    'duration':a['elapsed_seconds']})
check(len(clients)==52,'30 explicit clients plus22 polling clients')
aimdir=R/'aim-01-1010'
aim=readj(aimdir/'result.json')
aimops=[json.loads(s) for s in (aimdir/'controller.jsonl').read_text().splitlines()]
aimcommands=[o for o in aimops if o['kind']=='command']
aimreaps=[o for o in aimops if o['kind']=='reaped']
check(len(aimcommands)==len(aimreaps)==53 and all(o['returncode']==0 for o in aimreaps),'53 aim subclients reaped successfully')
check(aim['unreaped_client_pids']==[] and aim['elapsed_seconds']<120,'aim bounded')
check(aim['outcome']=='aimed-at-one-published-observation' and len(aim['steps'])==26,'aim one successful geometry')
check(rows('aim-completed')[0]['elapsed_seconds']<rows('aim-admitted')[0]['deadline']-origin,'supervisor aim completed within shared deadline')
check(all(s['target']==1010 for s in aim['steps']),'no retarget')
groups=[]
for op in rows('input-observation'):
    if op['attempt']==0:groups.append([])
    groups[-1].append(op)
for group in groups:
    check([o['attempt'] for o in group]==list(range(len(group))) and group[-1]['matched'] and not any(o['matched'] for o in group[:-1]),'ack order')
    check(group[-1]['elapsed_seconds']-group[0]['elapsed_seconds']<20,'ack read sequence within20seconds')
looks=[g for g in groups if g[0]['label']=='look']; expectations=rows('look-expectation')
check(len(looks)==len(expectations)==29,'29 acknowledged looks')
for group,expected in zip(looks,expectations):
    state=group[-1]['state']
    check(max(map(abs,expected['requested_pixels']))<=200,'look200 cap')
    check(abs(state['yaw']-expected['expected_yaw'])<=.02 and abs(state['pitch']-expected['expected_pitch'])<=.02,'native camera matches expected')
check([e['requested_pixels'] for e in expectations[:4]]==[[200,0],[-200,0],[0,200],[0,-200]],'calibration')
AU=149597870700.0
def geometry(snapshot,target=1010):
    t=snapshot['targets'][target]; f=snapshot['flight'];c=snapshot['controls']
    delta=[float(t[5+i])-f['position'][i] for i in range(3)]
    dist=norm(delta);yaw,pitch=math.radians(float(c[4])),math.radians(float(c[5]))
    forward=[-math.cos(yaw)*math.cos(pitch),math.sin(yaw)*-math.cos(pitch),-math.sin(pitch)]
    angle=math.degrees(math.acos(max(-1,min(1,sum(x*y for x,y in zip(forward,delta))/dist))))
    return {'snapshot_id':snapshot['id'],'tick':f['tick'],'range_au':dist/AU,'distance_m':dist,
            'heading_degrees':angle,'target_flags':t[2:5],'dt':f['dt'],'sim_time':f['sim_time']}
# Match each controller row to the exact world positions and camera, not merely a repeated tick.
for step in aim['steps']:
    found=[s for s in snaps.values() if s['tick']==step['tick'] and s['flight']['position']==step['spark_position'] and
           float(s['controls'][4])==step['yaw'] and float(s['controls'][5])==step['pitch']]
    check(len(found)>=1,'aim step raw world/camera exists')
    g=geometry(found[0])
    check(approx(g['distance_m'],step['distance_m']) and abs(g['heading_degrees']-step['angle_degrees'])<1e-8,'independent aim geometry')
check(aim['steps'][-1]['angle_degrees']<.5 and aim['steps'][-1]['tick']==5000,'aim acceptance')

# Hold records are compared with raw snapshots and independently recomputed heading/direction.
holds=rows('hold-complete'); hold_starts=rows('hold-start'); hold_releases=rows('hold-release')
hold_rows=[]
for ix,(h,hs,hr) in enumerate(zip(holds,hold_starts,hold_releases),1):
    check(h['key']==hs['key']==hr['key']=='w' and hs['requested_seconds']==hr['requested_seconds']==2,'requested W bounds')
    check(h['before']['thrust'] is None and h['after']['thrust'] is None,'observed nil before/after')
    yaw=math.radians(hs['controls']['yaw']);pitch=math.radians(hs['controls']['pitch'])
    expected=[-math.cos(yaw)*math.cos(pitch),-math.sin(yaw)*math.cos(pitch),-math.sin(pitch)]
    check(all(abs(a-b)<=1e-9 for a,b in zip(expected,h['during']['thrust'])),'actual held thrust agrees with camera')
    check(all(abs(a-b)<=1e-9 for a,b in zip(expected,hs['expected_thrust'])),'recorded oracle agrees')
    state_ids={}
    for phase in ['before','during','after']:
        matches=[s for s in snaps.values() if s['flight']==h[phase]]
        check(bool(matches),'hold phase raw '+phase)
        state_ids[phase]=matches[0]['id']
    before=snaps[state_ids['before']];after=snaps[state_ids['after']]
    gb=geometry(before);ga=geometry(after)
    check(gb['target_flags']==ga['target_flags']==['true']*3 and gb['heading_degrees']<=.5,'held current target and heading')
    check(all(approx(h['target_before'][k],gb[v]) for k,v in [('distance_m','distance_m'),('heading_error_degrees','heading_degrees')]),'pre-hold geometry')
    check(approx(h['target_after']['distance_m'],ga['distance_m']),'after-hold geometry')
    observations=[o for o in rows('hold-observation') if hs['elapsed_seconds']<=o['elapsed_seconds']<hr['elapsed_seconds']]
    releaseobs=[o for o in rows('hold-release-observation') if hr['elapsed_seconds']<o['elapsed_seconds']<=h['elapsed_seconds']]
    check(sum(o['direction_matches'] for o in observations)==1 and observations[-1]['direction_matches'],'one positive held acknowledgement')
    check([o['attempt'] for o in releaseobs]==[0,1,2] and [o['matched'] for o in releaseobs]==[False,False,True],'nil release third read')
    check(hr['release_elapsed_seconds']<10 and not hr['exceeds_ten_seconds'],'release uncertainty ceiling')
    ca=bytext[(C/f'{24 if ix==1 else 27:03d}-pulse-{"one" if ix==1 else "two"}-coast-a-snapshot.txt').read_text()]
    cb=bytext[(C/f'{25 if ix==1 else 28:03d}-pulse-{"one" if ix==1 else "two"}-coast-b-snapshot.txt').read_text()]
    gca=geometry(ca);gcb=geometry(cb)
    hold_rows.append({'pulse':ix,'requested_hold_seconds':2,'recorded_release_elapsed_seconds':hr['release_elapsed_seconds'],
       'snapshot_ids':dict(state_ids,coast_a=ca['id'],coast_b=cb['id']),
       'ticks':[h['before']['tick'],h['during']['tick'],h['after']['tick'],ca['tick'],cb['tick']],
       'range_au':{'before':gb['range_au'],'after':ga['range_au'],'coast_a':gca['range_au'],'coast_b':gcb['range_au']},
       'heading_before_degrees':gb['heading_degrees'],'heading_after_degrees':ga['heading_degrees'],
       'post_release_to_coast_b_change_au':gcb['range_au']-ga['range_au'],
       'pre_keydown_to_coast_b_change_au':gcb['range_au']-gb['range_au'],
       'hold_snapshot_change_au':ga['range_au']-gb['range_au'],
       'after_to_coast_a_change_au':gca['range_au']-ga['range_au'],
       'held_ack_read_count':len(observations),'release_ack_read_count':len(releaseobs),
       'held_ack_elapsed_since_hold_start':observations[-1]['elapsed_seconds']-hs['elapsed_seconds'],
       'nil_ack_after_key_release_seconds':releaseobs[-1]['elapsed_seconds']-hr['elapsed_seconds'],
       'expected_and_observed_thrust':expected,'velocity_after_m_per_s':h['after']['velocity'],
       'velocity_after_coast_b_m_per_s':cb['flight']['velocity'],
       'observed_before_after_tick_delta':h['observed_tick_delta'],
       'tick_delta_is_not_a_count_of_accepted_thrust_ticks':True})
    root_interval=readj(P/('root-attempt02-pulse-one-interval.json' if ix==1 else 'root-attempt02-pulse-two-analysis.json'))
    check(approx(root_interval['interval_range_change_AU'],hold_rows[-1]['pre_keydown_to_coast_b_change_au']),'root full pulse/coast range change reproduced')
    check(hold_rows[-1]['pre_keydown_to_coast_b_change_au']>0,'full interval noncontracting')
    for rootrow in root_interval['rows']:
        q=C/(rootrow['client']+'-snapshot.txt')
        g=geometry(bytext[q.read_text()])
        check(approx(rootrow['range_AU'],g['range_au']) and abs(rootrow['angle_degrees']-g['heading_degrees'])<1e-8,'root geometry row independently reproduced')
keydown=[o for o in commands if o['command']==['xdotool','keydown','w']]
keyup=[o for o in commands if o['command']==['xdotool','keyup','w']]
check(len(keydown)==len(keyup)==2,'exactly2 W press/release commands')
for ix,(down,up) in enumerate(zip(keydown,keyup)):
    hold_rows[ix]['keydown_to_keyup_command_seconds']=up['elapsed_seconds']-down['elapsed_seconds']
    check(2<=hold_rows[ix]['keydown_to_keyup_command_seconds']<3,'actual native command interval bounded but not exact2')
post_aim_controls=[s['controls'] for s in snaps.values() if s['tick']>=5000]
check(all(c[:3]==['manual','false','none'] and list(map(float,c[3:7]))==[3.75e13,-117.38,29.92,.01] for c in post_aim_controls),'camera/settings unchanged after aim')
poll=readj(P/'attempt-02-formation-poller.json')
check(poll['state']=='closed' and len(poll['observations'])==22 and poll['selects_target'] is False,'poller closure')
check(all(not o['ready_ids'] and o['candidate_rows']==0 for o in poll['observations'][:-1]),'poll00..20 no planets')
first=next(s for s in snaps.values() if s['targets'])
check(first['tick']==4228 and len(first['targets'])==12,'first observed planet state')
check(poll['observations'][-1]['tick']==4228 and poll['observations'][-1]['ready_ids']==['1010','1012'],'first observed ready')
check(all(o['commitment']==str(o['tick'])+' 0' for o in poll['observations']),'poll same-world commitment0')
polls=[readj(C/f'poll-{n:02d}.json') for n in range(22)]
poll_cadence=[b['started_monotonic']-a['started_monotonic'] for a,b in zip(polls,polls[1:])]
check(min(poll_cadence)>=30 and max(poll_cadence)<30.1,'bounded poll cadence')
admission=closed['successful_admission']
check(admission['target']==1010 and admission['elapsed_seconds']<960 and admission['no_prior_commitment'] is True,'admission cutoff and commitment')
# Exact stored time is recovered from the recorded geometry-bearing admission.
admission_time=admission['elapsed_seconds']
check(abs((rows('aim-admitted')[0]['deadline']-120-origin)-admission_time)<.001,'adjacent captured deadline and admission agree within1ms')
check(admission_time<960 and admission_time>800 and 1470-admission_time>=160,'late admission uses original1470 reserve')
check(approx(rows('aim-admitted')[0]['geometry']['distance_m'],geometry(next(s for s in snaps.values() if s['tick']==4686))['distance_m']),'admission raw geometry')
coasts=[geometry(bytext[(C/(n+'-snapshot.txt')).read_text()]) for n in ['019-coast-before-aim','020-coast-before-aim']]
screen=readj(P/'root-attempt02-preaim-screen.json')
check(approx(coasts[-1]['range_au'],screen['range_AU']),'preaim range')
rate=[(b['tick']-a['tick'])/(b['wall_monotonic']-a['wall_monotonic']) for a,b in zip(screen['endpoints'],screen['endpoints'][1:])]
check(all(approx(a,b) for a,b in zip(rate,screen['observed_rates'])),'screen rates')
nstar=math.ceil(4*max(rate))+2
coastfactor=.97/.03;D=3.75e13
for doc,g in [(screen,coasts[-1]),(readj(P/'root-attempt02-first-pulse-screen.json'),geometry(bytext[(C/'022-before-first-pulse-snapshot.txt').read_text()]))]:
    cf=coastfactor*D/g['distance_m'];tf=(nstar+coastfactor)*D/g['distance_m']
    check(approx(cf,doc['coast_fraction']) and approx(tf,doc['combined_fraction']),'screen arithmetic')
    check(cf<=.1 and tf<=.25 and doc['passes'],'screen heuristic passed')
aimclient=readj(C/'021-aim-1010.json');w1=readj(C/'023-pulse-one.json');w2=readj(C/'026-pulse-two.json')
delays={'first_ready_completed_poll_to_admission_seconds':admission_time-poll['observations'][-1]['elapsed'],
        'aim_outer_client_reap_to_first_W_client_start_seconds':w1['started_monotonic']-(aimclient['started_monotonic']+aimclient['elapsed_seconds']),
        'first_W_client_start_to_second_W_client_start_seconds':w2['started_monotonic']-w1['started_monotonic'],
        'first_W_client_reap_to_second_W_client_start_seconds':w2['started_monotonic']-(w1['started_monotonic']+w1['elapsed_seconds']),
        'aim_completed_protocol_to_first_native_W_command_seconds':keydown[0]['elapsed_seconds']-rows('aim-completed')[0]['elapsed_seconds']}
pngs=sorted(R.glob('*.png'))
check(len(pngs)==2,'two images')
for png in pngs:
    data=png.read_bytes()
    check(data[:8]==b'\x89PNG\r\n\x1a\n' and int.from_bytes(data[16:20],'big')==1280 and int.from_bytes(data[20:24],'big')==720,'PNG1280x720')
stderr=[row(q) for parent in (R,C) for q in sorted(parent.rglob('*.stderr')) if q.stat().st_size]
check([x['path'] for x in stderr]==['runs/attempt-02/001-xvfb.stderr'],'only nonempty recorded native/client stderr')
runtime=(R/'002-runtime.stdout').read_text()
check('Dev window stopped.' in runtime,'runtime stop marker')
check(readj(P/'root-attempt02-pulse-one-analysis.json')['last_hold'] is None,'original missing-kind calculation retained')
check(readj(P/'root-attempt02-pulse-one-interval.json')['last_hold']==holds[0],'corrected calculation has actual hold')
check('ValueError' in readj(P/'root-attempt02-first-pulse-screen.json')['parser_probe_note'],'parser failure disclosure retained')
preflight=readj(P/'attempt-02-client-directory-preflight.json')
check(preflight['run_directory_absent'] is True and preflight['at']<closed['started_at'],'preflight before launch')
for q,oldrow in zip(inputs,inventory):
    check(row(q)==oldrow,'immutable audit input '+oldrow['path'])
inventory_text=''.join(f"{x['sha256']}  {x['bytes']}  {x['path']}\n" for x in inventory)
non_nil=[s for s in snaps.values() if s['flight']['thrust'] is not None]
report={
 'audit_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'auditor':'/root/intent_research','context':'己','confidence':1.0,
 'scope':'Closed attempt-02, offline file/JSON/lexical EDN checks and arithmetic only. No driver imports, subprocesses, JVM, game, native input, live process probe, board, or Git operations. Only this script and its two reports written.',
 'verdict':'The ordinary native run admitted one naturally existing stored/current-ready target, completed a measured aim, and executed two released W pulses. It stopped within its lease after two net noncontracting full pulse/coast intervals. No binding, commitment, sculpt, embodiment or Gate was observed; this is not proof that flight or capture is impossible.',
 'source':{'base':closed['base'],'source_files_equal_launch_and_recorded_base':186,
    'source_equality_method':'Byte SHA256 against launch source-hashes and the previously frozen SOURCE-EQUALITY manifest. No fresh Git operation.',
    'runtime_pins':prep,'original_preparation_files':17,'original_checksum_rows':16,
    'operator_preparation_files':11,'operator_checksum_rows':10,
    'thin_root_client':row(P/'attempt-02-root-client.py'),
    'thin_client_review':row(P/'root-attempt02-client-review.json'),
    'thin_client_separate_from_original_launch_bundle':True},
 'identity':{k:closed[k] for k in ['cwd','port','display','world','window','x-window','supervisor','xvfb','app','snapshot_client']},
 'journal':{'snapshots':len(snaps),'first_id':'0001','last_id':'0209','raw_message_maps':sum(map(len,by.values())),
    'raw_utf8_bytes':len(raw),'request_journal_events':len(journal),'first_tick':snaps['0001']['tick'],'last_tick':snaps['0209']['tick'],
    'all_ids_ordered_and_bytes_partitioned_once':True,'all_complete_value_done':True,'all_worker_results_ok':True,
    'nrepl_error_messages':0,'same_world_global_commitment_zero_all_snapshots':True,
    'all_ordinary_nebula_fixture_false':True,'all_published_focus_overlap_false':True,
    'non_nil_thrust_snapshot_ids':[s['id'] for s in non_nil],
    'non_nil_thrust_ticks':[s['tick'] for s in non_nil],
    'all_copied_snapshot_files_equal_reconstructed_raw':True,
    'host_config_and_camera_not_atomically_captured_with_world':True},
 'operations':{'event_count':len(ops),'ipc_requests':len(requests),'ipc_by_operation':dict(request_mix),
    'ipc_all_success':True,'native_commands':66,'native_menu_presses':sum('mousedown' in o['command'] for o in commands),
    'calibration_looks':4,'aim_looks':25,'W_requested_pulses':2,'frames':2,
    'input_ack_groups':len(groups),'input_ack_reads':sum(map(len,groups)),
    'ack_read_count_distribution':dict(collections.Counter(map(len,groups))),
    'max_ack_reads':max(map(len,groups)),'more_than_three_reads_exercised':True,
    'four_read_look_group_times':[[g[0]['elapsed_seconds'],g[-1]['elapsed_seconds']] for g in groups if len(g)==4],
    'four_read_exercise_is_not_causal_proof_of_prior_failures':True,
    'ordinary_input_sequence':'R; Spark open; Fine; Cruise; thrust-down x3 each acknowledged; Spark close; Tab lock; +200x,-200x,+200y,-200y; target1010 aim25 relative looks; W2s, two coast reads; W2s, two coast reads; operator stop.',
    'provisional_D_m_per_tick':D,'D_AU_per_tick':D/AU,
    'all_postaim_observed_camera_and_D_unchanged':True},
 'formation':{'poll_count':22,'poll00_to20_no_planets_observed':True,'first_observed_poll':'poll-21',
    'first_observed_tick':4228,'first_observed_snapshot_id':first['id'],'planet_rows':12,
    'stored_current_ready_ids':[int(t[0]) for t in first['targets'].values() if t[2:4]==['true','true']],
    'fresh_handoff_ids':[int(t[0]) for t in first['targets'].values() if t[4]=='true'],
    'cadence_start_intervals_seconds':[min(poll_cadence),max(poll_cadence)],
    'poller_selected_no_target':True,'poller_closed_at':poll['ended_at'],'poller_reason':poll['reason'],
    'attempt01_ready_before_commit_report_bug_fixed_in_frozen_attempt02_operator_prep':True,
    'all_actual_poll_commitment_observations_zero':True,'poll_completions_do_not_pin_exact_birth_tick':True,
    'preaim_coast_endpoints':coasts,'preaim_screen':{'observed_ticks_per_second':rate,'N_star':nstar,
    'coast_fraction':screen['coast_fraction'],'combined_fraction':screen['combined_fraction'],'passes':True,'heuristic_not_physical_guarantee':True}},
 'aim':{'fixed_target':1010,'admitted_elapsed_seconds':admission_time,'admitted_tick':4686,
    'stored_current_ready_and_fresh_at_admission':True,'admitted_before960_after800':True,
    'original1470_work_reserve_seconds':1470-admission_time,'look_count':25,'geometry_rows':26,
    'initial_degrees':aim['steps'][0]['angle_degrees'],'last_geometry':aim['steps'][-1],
    'inner_elapsed_seconds':aim['elapsed_seconds'],'outer_elapsed_seconds':aimclient['elapsed_seconds'],
    'outcome':aim['outcome'],'aim_never_emitted_W':True,'aim_is_one_observation_not_tracking_or_capture':True},
 'pulse_intervals':hold_rows,'operator_delays':delays,
 'range_endpoint_verification':{'root_recorded_changes_are':'pre-keydown published snapshot to second coast',
    'root_numbers_preserved':True,'both_full_pre_keydown_intervals_positive':True,
    'transient_contractions':'Pulse1 pre→post shrank; pulse2 post→first coast shrank. Neither proves full capture or continued progress.',
    'moving_target_method':'Distances recomputed from simultaneous published Spark and target positions. No constant target-motion extrapolation or velocity*dt orbital displacement assumption.'},
 'cleanup':{'outcome':closed['outcome'],'ended_at':closed['ended_at'],'elapsed_seconds':closed['elapsed_seconds'],
    'all66_native_children_reaped_before_closed':True,'native_exit_counts':dict(collections.Counter(o['returncode'] for o in reaps)),
    'all52_outer_clients_reaped_exit0':True,'outer_client_finishes_after_closure':[c for c in clients if c['end']>closed['elapsed_seconds']],
    'all53_aim_subclients_reaped_exit0':True,'aim_subclient_unreaped':aim['unreaped_client_pids'],
    'setup_poller_parent_process_limit':'Saved setup/poller records show passed/closed completion; their parent process wait status is not independently recorded in these files. They are distinct from the 52 direct operation clients and four principal PIDs.',
    'root_principal_pid_absence_record':row(P/'root-attempt02-closure.json'),
    'root_pid_absence_at':root_close['at'],'no_new_live_probe_by_auditor':True,
    'release_or_cleanup_uncertainties':[],'game143_was_intentional_cleanup_not_a_normal_game_exit':True,
    'final_no_pending_key_is_host_state_and_observed_nil_thrust_precedes_stop':True},
 'bounds':{'startup180_seconds':True,'admission960_seconds':True,'work1470_seconds':True,'total1500_seconds':True,
    'aim120_seconds':True,'aims1_of3':True,'looks29_of360':True,'holds2_of20':True,'frames2_of3':True,
    'requested_hold_2_seconds_is_not_exact_native_duration':True,
    'runtime_heap_bytes':2147483648,'snapshot_worker_heap_MiB':256,
    'native_command_log_files_below_recorded_limits':True,'max_input_file_bytes':max(x['bytes'] for x in inventory),
    'snapshot_requests209_of2048':True,
    'entire_raw_journal_below128MiB':len(raw)<134217728},
 'images':[dict(row(pngs[0]),dimensions=[1280,720],visible_tick=432,visible_stars=0,visible_planets=0,
    nearest_prior_snapshot_id='0045',nearest_prior_tick=430),
    dict(row(pngs[1]),dimensions=[1280,720],visible_tick=6665,visible_stars=1,visible_disks=5,visible_planets=12,
    nearest_prior_snapshot_id='0209',nearest_prior_tick=6473)],
 'image_limits':'Both files viewed directly. Ordinary X11 process provenance is recorded, but frame capture is asynchronous with snapshots. The late image shows distant natural-system glyphs/nebula and reports planets; it is not target-specific close-up, trail-fade movie, embodiment or native FPS evidence.',
 'stderr_and_diagnostics':{'nonempty_stderr_files':stderr,'runtime_stdout':row(R/'002-runtime.stdout'),
    'literal_K_clamp_occurrences':runtime.count('K clamp binds:'),'clamp_text_is_not_causal_ejection_or_flight_proof':True,
    'runtime_stop_marker_present':True,'no_GL_error_sampling_or_render_performance_qualification':True},
 'preserved_failures':{'directory_preflight':row(P/'attempt-02-client-directory-preflight.json'),
    'preflight_limitation':'Root record says absent outer log directory caused open to fail before Popen. It precedes the actual run; no separate raw traceback is in the audited files.',
    'offline_parser_ValueError':'Disclosed in root-attempt02-first-pulse-screen.json; no separate raw exception traceback located. Corrected ranges were independently recomputed here.',
    'first_missing_kind_analysis':row(P/'root-attempt02-pulse-one-analysis.json'),
    'corrected_interval':row(P/'root-attempt02-pulse-one-interval.json'),
    'old_malformed_preparation_manifest':row(P/'manifest-shape-repair.json'),
    'old_manifest_repair_was_before_both_attempts_and_did_not_change_executables':True},
 'audit_development_notes':'Before final report generation, an audit assertion caught a mistaken provisional interpretation of the root interval endpoint; independent coordinate arithmetic confirmed the root used pre-keydown to second coast correctly. A separate exact-time assertion was narrowed to compare adjacent admission/deadline captures within1ms; reported admission uses the authoritative stored elapsed timestamp. Neither issue was a runtime defect or an alteration to raw evidence.',
 'limitations':['Only2 requested2s W pulses, not an exhaustive controller or capture trial.',
    'Large recorded root/operator gaps allowed continuous natural evolution between ready, aim and pulses.',
    'Observed before/after tick spans12 and16 include queued input and release latency, not known exact thrust-accepted tick counts.',
    'Direction ack arrived after several nil observations; nonnil thrust persisted into two release observations before nil.',
    'No binding/commitment or one-AU residence; no sculpt/embodiment/Gate acceptance.',
    'No comparative physics baseline, no native-FPS inference, no universal impossibility claim.',
    'Readbacks sample worlds; they do not observe every physical tick or a continuously atomic host/world snapshot.',
    'Four-PID absence is root-recorded evidence, not a new auditor process probe.'],
 'input_inventory':{'count':len(inventory),'total_bytes':sum(x['bytes'] for x in inventory),
    'canonical_rows_format':'sha256 + two spaces + bytes + two spaces + diagnostic-relative path + newline',
    'canonical_rows_sha256':sha(inventory_text.encode()),
    'groups':dict(collections.Counter(x['path'].split('/')[0] for x in inventory)),
    'all_original_bytes_rechecked':True,
    'input_file_map_format':'path<TAB>bytes<TAB>sha256',
    'input_file_map':[f"{x['path']}\t{x['bytes']}\t{x['sha256']}" for x in inventory],
    'excluded':'Attempt01 and its later packaging; __pycache__; these three audit outputs; any future packaging or summary added after this audit snapshot.'},
 'static_checks_passed':checks,
 'audit_script':row(P/'attempt-02-closure-audit.py')
}
json_path=P/'attempt-02-closure-audit.json'
md_path=P/'attempt-02-closure-audit.md'
check(not json_path.exists() and not md_path.exists(),'new audit outputs only')
md=f"""# Closed attempt 02 — independent offline audit

The ordinary native attempt completed one measured aim at natural target 1010 and two released W pulses, then stopped cleanly at T={closed['elapsed_seconds']:.6f}s. It did not reach binding, commitment, sculpting, embodiment or a Gate. Two noncontracting sampled intervals support the bounded stop; they do not establish that flight or capture is impossible.

## Provenance and complete journal coverage

- Source: {closed['base']}; all 186 launch/source-manifest hashes still match current bytes. The original 17-file preparation (16 checksums), six runtime pins, and separate 11-file operator preparation (10 checksums) match.
- The later thin root client has its own review-before-use record and SHA256 {review['sha256']}. It is not part of the original launch bundle.
- Exactly 209 snapshot IDs, 0001–0209, cover {len(raw):,} UTF-8 bytes and {sum(map(len,by.values())):,} raw EDN message maps with no gaps or overlap. All 420 request-journal events and all snapshot request/result/consumed operations agree. Every request returned one complete value and done record; there were no nREPL error messages.
- Every sampled world reports the same world atom/window, ordinary nebula, fixture false, and zero same-world global commitment. Host camera/config sampling is separate from the published-world read.
- {len(inventory)} raw/preparation input files, {sum(x['bytes'] for x in inventory):,} bytes, were hashed and rechecked. The JSON includes a complete path/byte/SHA256 map for later lossless packaging. Its canonical inventory digest is {sha(inventory_text.encode())}.

## Inputs and natural target

There were 102 successful supervisor IPC operations: {dict(request_mix)}. The actual sequence was R, Spark open, Fine, Cruise, three individually acknowledged halvings to D=3.75e13 m/tick, Spark close, Tab lock, four ±200-pixel calibration gestures returning to the baseline, 25 aiming gestures, two W pulses, and operator stop. No retarget, follow-camera command, fixture, world setter or achievement injection is recorded.

The read-only poller completed 22 polls. Polls 00–20 reported no planets; poll 21 first reported 12 planets at tick 4228, stored/current-ready 1010 and 1012, and fresh handoff 1010. This is the first observation, not an exact birth time. The attempt02 poller already corrects attempt01's ready-before-commit reason-priority bug; all actual commitment counts here remained zero. It selected no target.

Two further coast reads at ticks {coasts[0]['tick']} and {coasts[1]['tick']} preceded the root's explicit target choice. The pre-aim screen used observed rates {', '.join(f'{x:.6f}' for x in rate)} ticks/s, N*=25 and D≈{D/AU:.6f} AU/tick: coast fraction {screen['coast_fraction']:.9f}, combined fraction {screen['combined_fraction']:.9f}. These heuristic screens passed without guaranteeing capture.

## Aim and input acknowledgement

Target 1010 was admitted at T={admission_time:.6f}s, tick 4686: stored candidate, actual ready-to-commit, fresh handoff, finite geometry and global commitment zero. Admission occurred after 800s but before the 960s cutoff, with {1470-admission_time:.3f}s remaining against the original 1470s work deadline.

The aim used 25 native relative looks and 26 geometry rows. Independent vector/camera arithmetic reproduces the final tick-5000 error of {aim['steps'][-1]['angle_degrees']:.12f} degrees at yaw −117.38°, pitch 29.92°. It took {aim['elapsed_seconds']:.6f}s internally and {aimclient['elapsed_seconds']:.6f}s for the outer client. This is successful orientation at one observation, not continuous interception.

There were {len(groups)} input-acknowledgement groups and {sum(map(len,groups))} readbacks. The distribution by number of reads is {dict(collections.Counter(map(len,groups)))}. Three aim gestures required four reads; the greater-than-three observation allowance was actually exercised. This does not establish the cause of any earlier failed run.

## Thrust, coast endpoints and stop

Both pulses requested 2 seconds. Native keydown→keyup command intervals were {hold_rows[0]['keydown_to_keyup_command_seconds']:.6f}s and {hold_rows[1]['keydown_to_keyup_command_seconds']:.6f}s; recorded release completion spans were {hold_releases[0]['release_elapsed_seconds']:.6f}s and {hold_releases[1]['release_elapsed_seconds']:.6f}s. Requested duration is not an exact native-duration claim.

Both observed thrust vectors match the camera-derived direction [0.3985969690, 0.7696289233, −0.4987903134] within 1e-9. Each pulse began with nil thrust, eventually acknowledged nonnil thrust, and acknowledged nil on the third post-release read. Camera and D remained unchanged in all sampled post-aim states. The before→after tick spans of 12 and 16 include publication/queue/release latency; they are not exact counts of thrust-accepted ticks.

| Pulse | Before / held / released / coast A / coast B ticks | Before AU | Released AU | Coast A AU | Coast B AU |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | {' / '.join(map(str,hold_rows[0]['ticks']))} | {hold_rows[0]['range_au']['before']:.6f} | {hold_rows[0]['range_au']['after']:.6f} | {hold_rows[0]['range_au']['coast_a']:.6f} | {hold_rows[0]['range_au']['coast_b']:.6f} |
| 2 | {' / '.join(map(str,hold_rows[1]['ticks']))} | {hold_rows[1]['range_au']['before']:.6f} | {hold_rows[1]['range_au']['after']:.6f} | {hold_rows[1]['range_au']['coast_a']:.6f} | {hold_rows[1]['range_au']['coast_b']:.6f} |

The root's +{hold_rows[0]['pre_keydown_to_coast_b_change_au']:.9f} AU and +{hold_rows[1]['pre_keydown_to_coast_b_change_au']:.9f} AU are correctly pre-keydown→second-coast changes. Released-snapshot→second-coast changes are +{hold_rows[0]['post_release_to_coast_b_change_au']:.9f} AU and +{hold_rows[1]['post_release_to_coast_b_change_au']:.9f} AU. Both definitions remain noncontracting. Pulse 1 briefly contracted by {abs(hold_rows[0]['hold_snapshot_change_au']):.6f} AU before its released snapshot; pulse 2 contracted by {abs(hold_rows[1]['after_to_coast_a_change_au']):.6f} AU between release and its first coast. These transient decreases remain in the evidence.

Important operator gaps were {delays['first_ready_completed_poll_to_admission_seconds']:.3f}s from first ready poll completion to admission, {delays['aim_outer_client_reap_to_first_W_client_start_seconds']:.3f}s from aim-client completion to the first W request, and {delays['first_W_client_start_to_second_W_client_start_seconds']:.3f}s between W requests. Natural motion continued through those gaps. Distances use paired world positions; no constant target velocity or velocity×dt orbital displacement was assumed.

## Images and cleanup

The two unchanged 1280×720 PNGs were viewed directly:

- frame-1791369829536127035-02a8d2de.png visibly reports tick 432, zero stars and zero planets; nearest preceding snapshot was tick 430.
- frame-1791371010177321862-d4589e86.png visibly reports tick 6665, one star and twelve planets; the separate disks row reports five disks. The last independent snapshot was tick 6473, about 40.7 seconds before the frame request. Its pixels show distant glyphs and nebula, not a target-specific close-up or temporal fade measurement.

All 66 spawned native children were reaped before closed.json: 65 exited 0, the game exited 143 during deliberate cleanup. All 53 aim subclients exited 0. All 52 recorded outer clients exited 0; the stop client reaped after the supervisor's closed record, as expected for the caller awaiting closure. No pending key, uncertain release, cleanup error or unreaped child remains in the closed state. Root separately recorded all four principal PIDs absent at {root_close['at']}; this audit did not probe live processes.

The run used its private :1/7898 surface, 2 GiB game heap and 256 MiB snapshot worker. It closed before the 1470/1500-second work/total bounds, using 1/3 aims, 29/360 looks, 2/20 W pulses and 2/3 frames. The only nonempty recorded stderr is Xvfb's 2,480-byte display-selection/keymap warnings. Runtime stdout contains {runtime.count('K clamp binds:'):,} literal K-clamp messages and the final stop marker. Those messages are not causal proof of ejection or approach failure; no GL-error sampling, hardware FPS or isolated-performance claim is made.

## Preserved unsuccessful preparations and calculations

The missing outer-log-directory preflight is retained separately and predates the real run; root records it failed before Popen. The first offline range parser's ValueError is disclosed in first-pulse-screen.json, but no separate raw traceback is present in the audited input set. The original pulse-one-analysis.json retains its missing-kind result (last_hold null); pulse-one-interval.json retains the correction. Their corrected geometry and range arithmetic were reproduced independently here. The older malformed preparation manifest and exact repair evidence also remain unchanged.

This audit performed {checks:,} static checks and changed no raw evidence. It establishes successful bounded orientation/input and truthful cleanup, with unsuccessful capture in this limited trial. It does not establish a general flight limitation, physical impossibility, or completion of the user's playable-Gate goal.
"""
json_path.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
md_path.write_text(md)
for q,oldrow in zip(inputs,inventory):
    check(row(q)==oldrow,'post-write immutable input '+oldrow['path'])
print(json.dumps({'checks':checks,'outputs':[row(json_path),row(md_path)],'input_count':len(inventory),'hold_rows':hold_rows,'operator_delays':delays},indent=2))
