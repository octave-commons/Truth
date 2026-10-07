from pathlib import Path
import subprocess,json,time,datetime,re,os
base=Path('.ημ/diagnostics/input-observation-window');run=base/'runs/attempt-01';logs=base/'attempt-01-clients'
plan=[('003-manual','tap',['r']),('004-open-spark','click',['spark']),('005-fine','click',['fine']),('006-cruise','click',['cruise']),('007-half','click',['thrust-down']),('008-quarter','click',['thrust-down']),('009-eighth','click',['thrust-down']),('010-close-spark','click',['spark']),('011-lock','tap',['Tab']),('012-final-inspect','inspect',[])]
for stemname,op,extra in plan:
 stem=logs/stemname;args=['python3',str(base/'runner.py'),op,str(run),*extra]
 start=time.monotonic();at=datetime.datetime.now(datetime.timezone.utc).isoformat()
 with stem.with_suffix('.stdout').open('xb') as out,stem.with_suffix('.stderr').open('xb') as err:
  child=subprocess.Popen(args,stdout=out,stderr=err)
  record={'args':args,'pid':child.pid,'started_at':at,'started_monotonic':start,'state':'running'}
  stem.with_suffix('.json').write_text(json.dumps(record,indent=2)+'\n');rc=child.wait()
 record.update(state='reaped',returncode=rc,elapsed_seconds=time.monotonic()-start)
 stem.with_suffix('.json').write_text(json.dumps(record,indent=2)+'\n')
 print(stemname,rc,stem.with_suffix('.stdout').read_text(),flush=True)
 if rc:print(stem.with_suffix('.stderr').read_text(),flush=True);raise SystemExit(rc)
 snap=(run/'snapshot-current.stdout').read_text()
 lines=[s for s in snap.splitlines() if s.startswith(('TRUTH_INPUT_STATE ','TRUTH_FLIGHT ','TRUTH_COMMITMENT '))]
 print('\n'.join(lines),flush=True)
 flight=re.findall(r'^TRUTH_FLIGHT (.+)$',snap,re.M)
 assert len(flight)==1 and flight[0].split()[-3:]==['nil']*3,'Unexpected thrust in settings-only sequence'

