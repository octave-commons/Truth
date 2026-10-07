from pathlib import Path
from datetime import datetime, timezone
import os,sys,json,time,signal,subprocess,hashlib
ROOT=Path.cwd(); OUT=ROOT/'.ημ/diagnostics/action-aim-protocol/green-qualification/canonical-gate-02'
PINS=json.loads((OUT/'source-test-pins.json').read_text())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert all(sha(ROOT/p)==h for p,h in PINS.items()),'Source changed before launch'
argv=['/home/err/.volta/tools/image/node/22.20.0/bin/node', '/home/err/spaces/review-repair/rheos-comment-rendering/dist/cli.cjs', 'move', 'focus-follows-pilot', '--to', 'review', '--config', 'openhax.kanban.edn']
env=os.environ.copy()
for key in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']:env.pop(key,None)
env['JAVA_OPTS']='-Xms256m -Xmx2g'
start=datetime.now(timezone.utc).isoformat(); t=time.monotonic(); identities={}; history=[]
def observe(pid):
 try:
  fields=Path(f'/proc/{pid}/stat').read_text().rsplit(') ',1)[1].split()
  record={'pid':pid,'starttime_ticks':int(fields[19]),'pgid':int(fields[2]),'state':fields[0]}
  for key in ['exe','cwd']:
   try:record[key]=os.readlink(f'/proc/{pid}/{key}')
   except FileNotFoundError:record[key]=None
  old=identities.get(pid)
  if old is None or old.get('exe')!=record['exe']:
   history.append(dict(record,elapsed_s=time.monotonic()-t))
  identities[pid]=record
  children=Path(f'/proc/{pid}/task/{pid}/children').read_text().split()
  for child in children:observe(int(child))
 except (FileNotFoundError,ProcessLookupError):pass
with (OUT/'gate.stdout').open('xb') as so,(OUT/'gate.stderr').open('xb') as se:
 p=subprocess.Popen(argv,env=env,stdout=so,stderr=se,start_new_session=True)
 timed_out=False
 while p.poll() is None:
  observe(p.pid)
  if time.monotonic()-t>=1490:
   timed_out=True;os.killpg(p.pid,signal.SIGTERM)
   try:p.wait(timeout=8)
   except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL)
   break
  time.sleep(.1)
 rc=p.wait(timeout=2)
remaining=[]
for pid,old in identities.items():
 try:
  f=Path(f'/proc/{pid}/stat').read_text().rsplit(') ',1)[1].split()
  if int(f[19])==old['starttime_ticks']:remaining.append({'pid':pid,'state':f[0]})
 except FileNotFoundError:pass
changed=[p for p,h in PINS.items() if sha(ROOT/p)!=h]
result={'argv':argv,'cwd':str(ROOT),'started_utc':start,'ended_utc':datetime.now(timezone.utc).isoformat(),'elapsed_s':time.monotonic()-t,'pid':p.pid,'process_group':p.pid,'owned_pids_observed':sorted(identities),'process_identity_history':history,'java_opts':env['JAVA_OPTS'],'removed_inherited_option_names':['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'],'work_timeout_s':1490,'cleanup_reserve_s':10,'timed_out':timed_out,'exit_code':rc,'main_process_reaped':True,'owned_pids_still_present':remaining,'source_test_pin_count':len(PINS),'source_test_changed':changed,'stdout_sha256':sha(OUT/'gate.stdout'),'stderr_sha256':sha(OUT/'gate.stderr'),'runner_sha256':sha(Path(__file__))}
(OUT/'gate-execution.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2));sys.exit(rc or bool(remaining) or bool(changed) or timed_out)
