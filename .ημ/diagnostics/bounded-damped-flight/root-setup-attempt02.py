from pathlib import Path
import subprocess,json,time,datetime,re,os
here=Path(__file__).resolve().parent;root=here.parents[2];base=here;run=base/'runs/attempt-02';logs=here/'attempt-02-clients'
logs.mkdir(exist_ok=True)
def invoke(stemname,op,extra):
 stem=logs/stemname;args=['python3',str(base/'runner.py'),op,str(run),*extra]
 start=time.monotonic();at=datetime.datetime.now(datetime.timezone.utc).isoformat()
 with stem.with_suffix('.stdout').open('xb') as out,stem.with_suffix('.stderr').open('xb') as err:
  child=subprocess.Popen(args,cwd=root,stdout=out,stderr=err)
  record={'args':args,'pid':child.pid,'started_at':at,'started_monotonic':start,'state':'running'}
  stem.with_suffix('.json').write_text(json.dumps(record,indent=2)+'\n');rc=child.wait()
 record.update(state='reaped',returncode=rc,elapsed_seconds=time.monotonic()-start)
 stem.with_suffix('.json').write_text(json.dumps(record,indent=2)+'\n')
 print(stemname,rc,stem.with_suffix('.stdout').read_text(),flush=True)
 if rc:print(stem.with_suffix('.stderr').read_text(),flush=True);raise RuntimeError('Submitted action failed; no retry: '+stemname)
 return (run/'snapshot-current.stdout').read_text()
plan=[('002-inspect', 'inspect', []), ('003-manual', 'tap', ['r']), ('004-open-spark', 'click', ['spark']), ('005-fine', 'click', ['fine']), ('006-cruise', 'click', ['cruise']), ('007-half', 'click', ['thrust-down']), ('008-damping-01', 'click', ['damping-down']), ('009-damping-02', 'click', ['damping-down']), ('010-damping-03', 'click', ['damping-down']), ('011-damping-04', 'click', ['damping-down']), ('012-damping-05', 'click', ['damping-down']), ('013-damping-06', 'click', ['damping-down']), ('014-damping-07', 'click', ['damping-down']), ('015-damping-08', 'click', ['damping-down']), ('016-damping-09', 'click', ['damping-down']), ('017-damping-10', 'click', ['damping-down']), ('018-damping-11', 'click', ['damping-down']), ('019-damping-12', 'click', ['damping-down']), ('020-damping-13', 'click', ['damping-down']), ('021-damping-14', 'click', ['damping-down']), ('022-damping-15', 'click', ['damping-down']), ('023-damping-16', 'click', ['damping-down']), ('024-damping-17', 'click', ['damping-down']), ('025-close-spark', 'click', ['spark']), ('026-lock', 'tap', ['Tab']), ('027-baseline', 'inspect', []), ('028-x-plus', 'look', ['200', '0']), ('029-x-minus', 'look', ['-200', '0']), ('030-y-plus', 'look', ['0', '200']), ('031-y-minus', 'look', ['0', '-200']), ('032-calibration-final', 'inspect', [])]
report={'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'pid':os.getpid(),'state':'running','steps':[]}
path=here/'attempt-02-calibration.json';assert not path.exists(),'Existing calibration record';path.write_text(json.dumps(report,indent=2)+'\n');baseline=None
try:
 for stem,op,args in plan:
  snap=invoke(stem,op,args);flight=re.findall(r'^TRUTH_FLIGHT (.+)$',snap,re.M);controls=re.findall(r'^TRUTH_INPUT_STATE (.+)$',snap,re.M);commit=re.findall(r'^TRUTH_COMMITMENT (.+)$',snap,re.M)
  assert len(flight)==len(controls)==len(commit)==1
  f=flight[0].split();c=controls[0].split()
  retention=re.findall(r'^TRUTH_DAMPING_STATE (\S+)$',snap,re.M);assert len(retention)==1
  r=float(retention[0])
  assert f[-3:]==['nil']*3 and commit[0].split()[1]=='0','Unexpected thrust/commitment during setup'
  step={'client':stem,'tick':int(f[0]),'controls':c,'nil_thrust':True,'retention':r};report['steps'].append(step)
  if 'damping-' in stem:
   n=int(stem.rsplit('-',1)[1]);expected=max(.8,.97-.01*n)
   assert abs(r-expected)<=1e-12 and float(c[3])==1.5e14,'Damping/D setup acknowledgement mismatch'
   step['expected_retention']=expected
  if stem=='027-baseline':
   assert c[:3]==['manual','false','none'] and c[-2:]==['true','false']
   baseline=[float(c[4]),float(c[5]),float(c[6])];assert abs(baseline[2]-.01)<1e-12 and abs(baseline[1])<87
   report['baseline']=baseline
  if stem in ['029-x-minus','031-y-minus','032-calibration-final']:
   assert baseline is not None and abs(float(c[4])-baseline[0])<=.02 and abs(float(c[5])-baseline[1])<=.02 and float(c[6])==baseline[2]
   assert c[:3]==['manual','false','none'] and c[-2:]==['true','false'] and float(c[3])==1.5e14 and abs(r-.8)<=1e-12
   step['returned_to_baseline']=True
  path.write_text(json.dumps(report,indent=2)+'\n')
  print(json.dumps(step),flush=True)
 report.update(state='passed',ended_at=datetime.datetime.now(datetime.timezone.utc).isoformat())
 path.write_text(json.dumps(report,indent=2)+'\n')
 invoke('033-young-frame','frame',[])
except BaseException as error:
 report.update(state='failed',error=repr(error),ended_at=datetime.datetime.now(datetime.timezone.utc).isoformat());path.write_text(json.dumps(report,indent=2)+'\n')
 try:invoke('setup-failure-stop','stop',[])
 except BaseException as cleanup_error:report['cleanup_caller_error']=repr(cleanup_error);path.write_text(json.dumps(report,indent=2)+'\n')
 raise
