from pathlib import Path
import subprocess,json,time,datetime,re,os
base=Path('.ημ/diagnostics/bounded-look-200');run=base/'runs/attempt-01';logs=base/'attempt-01-clients'
recordpath=base/'formation-poller.json'
start=time.monotonic();origin=json.loads((run/'supervisor-launch.json').read_text())['lease_started_monotonic']
record={'pid':os.getpid(),'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'started_monotonic':start,'original_no_target_deadline':origin+960,'maximum_polls':32,'cadence_seconds':30,'selects_target':False,'state':'running','observations':[]}
recordpath.write_text(json.dumps(record,indent=2)+'\n')
reason='maximum-polls'
try:
 for i in range(32):
  if (run/'closed.json').exists():reason='run-closed';break
  if time.monotonic()>=origin+960:reason='original-no-target-deadline';break
  at=datetime.datetime.now(datetime.timezone.utc).isoformat();t=time.monotonic()
  stem=logs/f'poll-{i:02d}';args=['python3',str(base/'runner.py'),'inspect',str(run)]
  with stem.with_suffix('.stdout').open('xb') as out,stem.with_suffix('.stderr').open('xb') as err:
   child=subprocess.Popen(args,stdout=out,stderr=err)
   client={'args':args,'pid':child.pid,'started_at':at,'started_monotonic':t,'state':'running'}
   stem.with_suffix('.json').write_text(json.dumps(client,indent=2)+'\n');rc=child.wait()
  client.update(state='reaped',returncode=rc,elapsed_seconds=time.monotonic()-t);stem.with_suffix('.json').write_text(json.dumps(client,indent=2)+'\n')
  if rc:reason='inspect-failed';record['client_error']=stem.with_suffix('.stderr').read_text();break
  snap=(run/'snapshot-current.stdout').read_text()
  (logs/f'poll-{i:02d}-snapshot.txt').write_text(snap)
  flight=re.findall(r'^TRUTH_FLIGHT (.+)$',snap,re.M);commit=re.findall(r'^TRUTH_COMMITMENT (.+)$',snap,re.M)
  assert len(flight)==len(commit)==1
  candidates=[s.split() for s in re.findall(r'^TRUTH_COMMIT_TARGET (.+)$',snap,re.M)]
  ready=[s[0] for s in candidates if s[2:4]==['true','true']]
  observation={'at':at,'elapsed':time.monotonic()-origin,'tick':int(flight[0].split()[0]),'commitment':commit[0],'ready_ids':ready,'candidate_rows':len(candidates),'client':stem.name}
  record['observations'].append(observation);recordpath.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(observation),flush=True)
  if ready:reason='stored-ready-observed-no-selection';break
  if commit[0].split()[1]!='0':reason='commitment-observed';break
  time.sleep(max(0,min(t+30,origin+960)-time.monotonic()))
finally:
 record.update(state='closed',ended_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),elapsed=time.monotonic()-start,reason=reason)
 recordpath.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({'poller_closed':reason}),flush=True)
