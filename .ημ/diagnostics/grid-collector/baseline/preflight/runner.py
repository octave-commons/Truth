import pathlib,subprocess,hashlib,json,os,datetime,time
root=pathlib.Path.cwd();out=root/'.ημ/diagnostics/grid-collector/baseline/preflight';out.mkdir(parents=True,exist_ok=False)
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();assert head=='b395c4049718f7ce015ddf25fc0a192d97821373'
paths=['src/domain/spatial/index.clj','src/domain/physics/cache/neighbor.clj','test/domain/spatial/grid_query_contract_test.clj','.ημ/diagnostics/grid-collector/evidence/grid.clj','deps.edn']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths};assert hashes[paths[0]]=='280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701';assert hashes[paths[3]]=='6e1c3ce23617260ad8d7e94be00b6ff7fc15c82c3a23c547ee642abeea8b09c2'
env=os.environ.copy();env.update({'JAVA_TOOL_OPTIONS':'-Xms256m -Xmx2g','_JAVA_OPTIONS':'-Xms256m -Xmx2g'})
commands=[('format',['clojure','-J-Xms256m','-J-Xmx2g','-M:cljfmt','check',paths[2],paths[3]]),('focused',['clojure','-J-Xms256m','-J-Xmx2g','-M:test','-n','domain.spatial.grid-query-contract-test','-n','domain.spatial.index-test','-n','domain.physics.cache-test'])]
(out/'inputs.json').write_text(json.dumps({'head':head,'hashes':hashes,'environment_override':{k:env[k] for k in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS']},'scope':'Static formatting then expected-GREEN semantic characterization, no performance timing or native mutation.'},indent=2)+'\n')
for label,cmd in commands:
 start=datetime.datetime.now(datetime.timezone.utc).isoformat()
 with (out/(label+'.stdout')).open('wb') as stdout,(out/(label+'.stderr')).open('wb') as stderr:
  p=subprocess.Popen(cmd,cwd=root,env=env,stdout=stdout,stderr=stderr);print(json.dumps({'label':label,'pid':p.pid,'started_at':start,'command':cmd}),flush=True);code=p.wait()
 stable=head==subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip() and hashes=={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths}
 record={'command':cmd,'started_at':start,'finished_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'pid':p.pid,'exit_code':code,'reaped':True,'inputs_unchanged':stable}
 (out/(label+'.result.json')).write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record),flush=True)
 assert stable
 if code:raise SystemExit(code)
