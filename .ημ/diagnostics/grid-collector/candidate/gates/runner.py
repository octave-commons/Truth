from pathlib import Path
import subprocess,hashlib,json,os,datetime,signal
root=Path.cwd();out=root/'.ημ/diagnostics/grid-collector/candidate/gates';out.mkdir(parents=True,exist_ok=False)
now=lambda:datetime.datetime.now(datetime.timezone.utc).isoformat()
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();assert head=='8f953d2bcfab2fa2b3fef8a390157a4629eab9e0'
paths=subprocess.check_output(['git','ls-files','-z','src','test','test-native','bench','dev','bin','deps.edn','.clj-kondo','.lsp','.splint.edn','.jscpd.json']).decode().split('\0')[:-1]
paths += ['.ημ/diagnostics/grid-collector/evidence/grid.clj','.ημ/diagnostics/grid-collector/fixtures.edn']
def hashes():return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths}
expected=hashes();assert expected['src/domain/spatial/index.clj']=='3685ddefe8ccedd3af3716678684a041ec50e1a08b1cbc89d8025ebfa3b8437a'
env=os.environ.copy();env.update({'JAVA_TOOL_OPTIONS':'-Xms256m -Xmx2g','_JAVA_OPTIONS':'-Xms256m -Xmx2g'})
(out/'inputs.json').write_text(json.dumps({'head':head,'hashes':expected,'environment_override':{k:env[k] for k in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS']},'scope':'Qualification only; durations with resumed native are not performance evidence.'},indent=2)+'\n')
for label,cmd in [('full-suite',['clojure','-J-Xms256m','-J-Xmx2g','-M:test']),('strict',['bin/analyze','--strict'])]:
 start=now();p=None
 try:
  with (out/(label+'.stdout')).open('wb') as stdout,(out/(label+'.stderr')).open('wb') as stderr:
   p=subprocess.Popen(cmd,cwd=root,env=env,stdout=stdout,stderr=stderr,start_new_session=True);print(json.dumps({'label':label,'pid':p.pid,'started_at':start,'command':cmd}),flush=True);rc=p.wait(timeout=1200)
 finally:
  if p and p.poll() is None:
   os.killpg(p.pid,signal.SIGTERM)
   try:p.wait(timeout=15)
   except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
  stable=head==subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip() and expected==hashes()
  result={'command':cmd,'started_at':start,'finished_at':now(),'pid':p.pid if p else None,'exit_code':p.returncode if p else None,'reaped':p is not None and p.poll() is not None,'inputs_unchanged':stable}
  (out/(label+'.result.json')).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
 assert stable
 if rc:raise SystemExit(rc)
