import pathlib,subprocess,hashlib,json,os,datetime,time,signal
root=pathlib.Path.cwd();out=root/'.ημ/diagnostics/grid-collector/candidate/bench-run';out.mkdir(parents=True,exist_ok=False)
validation=pathlib.Path('/home/err/spaces/foresight/.worktrees/truth-validation');health=json.loads((validation/'.ημ/diagnostics/native-health/2026-10-07T034022Z/command.json').read_text())
pid=131581;expected_start='125503';expected_boot='b04f7fb6-ea65-4b47-8866-73e24a6887f6'
now=lambda:datetime.datetime.now(datetime.timezone.utc).isoformat()
def write(name,obj): (out/name).write_text(json.dumps(obj,indent=2)+'\n')
def native():
 fields=pathlib.Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split();boot=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip();cwd=os.readlink(f'/proc/{pid}/cwd');assert fields[19]==expected_start and boot==expected_boot and cwd==str(validation),'Native identity mismatch'
 return {'at':now(),'pid':pid,'startticks':fields[19],'boot_id':boot,'cwd':cwd,'state':fields[0],'cpu_ticks':int(fields[11])+int(fields[12])}
def native_health(label):
 state=native();env=os.environ.copy();env.update(health['environment_override']);cmd=health['command'];start=now()
 with (out/(label+'.stdout')).open('wb') as stdout,(out/(label+'.stderr')).open('wb') as stderr:
  p=subprocess.Popen(cmd,cwd=validation,env=env,stdout=stdout,stderr=stderr)
  try:rc=p.wait(timeout=45)
  except subprocess.TimeoutExpired:p.terminate();rc=p.wait(timeout=15)
 result={'pid':p.pid,'exit_code':rc,'reaped':True,'started_at':start,'completed_at':now(),'server_before':state,'server_after':native(),'command':cmd,'form_sha256':health['form_sha256']};write(label+'.json',result);assert rc==0,'Guarded native health failed'
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();assert head=='8f953d2bcfab2fa2b3fef8a390157a4629eab9e0'
paths=['src/domain/spatial/index.clj','src/domain/physics/cache/neighbor.clj','test/domain/spatial/grid_query_contract_test.clj','.ημ/diagnostics/grid-collector/evidence/grid.clj','.ημ/diagnostics/grid-collector/fixtures.edn','deps.edn'];hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths};assert hashes[paths[0]]=='3685ddefe8ccedd3af3716678684a041ec50e1a08b1cbc89d8025ebfa3b8437a';assert hashes[paths[3]]=='51a4fc1ac47a53d4b488df72ca7713b1cec010752f1070b971ce508a1db299de'
assert hashes[paths[4]]=='c9eb7f817e658f956a0418fb7ee325dd04bbdf1ccd9806e84ab795c154ed9a74'
rows=json.loads(subprocess.check_output(['pm2','jlist'],text=True));item=next(x for x in rows if x['name']=='truth-native-7896');pe=item['pm2_env'];assert item['pid']==pid and item['pm_id']==13 and pe['autorestart'] is False and pe['restart_time']==0 and pe['status']=='online';write('pm2-guard.json',{'name':item['name'],'pid':item['pid'],'pm_id':item['pm_id'],'status':pe['status'],'autorestart':pe['autorestart'],'restart_count':pe['restart_time']})
alias='{:aliases {:grid-evidence {:extra-paths [".ημ/diagnostics/grid-collector"] :main-opts ["-m" "evidence.grid"]}}}';cmd=['clojure','-J-Xms256m','-J-Xmx2g','-Sdeps',alias,'-M:bench:grid-evidence','candidate','.ημ/diagnostics/grid-collector/fixtures.edn','.ημ/diagnostics/grid-collector/after'];env=os.environ.copy();env.update({'JAVA_TOOL_OPTIONS':'-Xms256m -Xmx2g','_JAVA_OPTIONS':'-Xms256m -Xmx2g'})
write('command.json',{'command':cmd,'head':head,'inputs':hashes,'environment_override':{k:env[k] for k in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS']},'deadline_seconds':600,'scope':'Six isolated cost cases, same native world/context temporarily suspended; other services unmodified.'})
paused=False;p=None;rc=None;primary_error=None;sample=[]
try:
 native_health('native-before');s=native();assert s['state'] not in ['T','t'],'Already paused by another owner';os.kill(pid,signal.SIGSTOP);paused=True
 deadline=time.monotonic()+3
 while native()['state']!='T':
  assert time.monotonic()<deadline,'Native did not pause';time.sleep(.02)
 paused_state=native();write('native-paused.json',paused_state);print(json.dumps({'native_paused':paused_state}),flush=True)
 with (out/'stdout.log').open('wb') as stdout,(out/'stderr.log').open('wb') as stderr:
  started=now();p=subprocess.Popen(cmd,cwd=root,env=env,stdout=stdout,stderr=stderr,start_new_session=True);print(json.dumps({'benchmark_pid':p.pid,'started_at':started}),flush=True);deadline=time.monotonic()+600
  while True:
   n=native();assert n['state']=='T' and n['cpu_ticks']==paused_state['cpu_ticks'],'Owned native resumed or used CPU during measurement';sample.append({'at':now(),'load_average':os.getloadavg(),'native_cpu_ticks':n['cpu_ticks'],'native_state':n['state']})
   try:rc=p.wait(timeout=3);break
   except subprocess.TimeoutExpired:assert time.monotonic()<deadline,'Benchmark deadline exceeded'
  stable=head==subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip() and hashes=={f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in paths}
  write('result.json',{'pid':p.pid,'exit_code':rc,'reaped':True,'started_at':started,'completed_at':now(),'inputs_unchanged':stable,'native_zero_cpu_while_sampled':True});assert stable
except BaseException as ex:
 primary_error=repr(ex);write('wrapper-error.json',{'at':now(),'error':primary_error});raise
finally:
 if p is not None and p.poll() is None:
  os.killpg(p.pid,signal.SIGTERM)
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
  write('terminated-child.json',{'pid':p.pid,'exit_code':p.returncode,'reaped':True,'at':now()})
 write('load-samples.json',sample)
 if paused:
  before=native();os.kill(pid,signal.SIGCONT);write('native-resumed.json',{'at':now(),'before':before,'after':native(),'operation':'SIGCONT same verified owned process; no world/context replacement'})
  print(json.dumps({'native_resumed':pid,'at':now()}),flush=True)
 if primary_error is None:native_health('native-after')
print(json.dumps({'exit_code':rc,'reaped':True,'completed_at':now()}),flush=True);raise SystemExit(rc)
