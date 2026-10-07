import pathlib,subprocess,hashlib,json,os,datetime
root=pathlib.Path.cwd();out=root/'.ημ/diagnostics/grid-collector/baseline/capture-run';out.mkdir(parents=True,exist_ok=False)
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();assert head=='b395c4049718f7ce015ddf25fc0a192d97821373'
paths=['src/domain/spatial/index.clj','src/domain/physics/cache/neighbor.clj','test/domain/spatial/grid_query_contract_test.clj','.ημ/diagnostics/grid-collector/evidence/grid.clj','deps.edn']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths};assert hashes[paths[0]]=='280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701';assert hashes[paths[3]]=='0baaa761a1d343802b8a320870d9860ec53a24fda99956b0c43bbb1a8554be74'
alias='{:aliases {:grid-evidence {:extra-paths [".ημ/diagnostics/grid-collector"] :main-opts ["-m" "evidence.grid"]}}}'
cmd=['clojure','-J-Xms256m','-J-Xmx2g','-Sdeps',alias,'-M:bench:grid-evidence','capture','.ημ/diagnostics/grid-collector/fixtures.edn']
env=os.environ.copy();env.update({'JAVA_TOOL_OPTIONS':'-Xms256m -Xmx2g','_JAVA_OPTIONS':'-Xms256m -Xmx2g'})
start=datetime.datetime.now(datetime.timezone.utc).isoformat();(out/'command.json').write_text(json.dumps({'command':cmd,'started_at':start,'head':head,'inputs':hashes,'environment_override':{k:env[k] for k in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS']},'native_state':'Untouched; no timing measurement in this capture'},indent=2)+'\n')
with (out/'stdout.log').open('wb') as stdout,(out/'stderr.log').open('wb') as stderr:
 p=subprocess.Popen(cmd,cwd=root,env=env,stdout=stdout,stderr=stderr);print(json.dumps({'pid':p.pid,'started_at':start}),flush=True);code=p.wait()
stable=head==subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip() and hashes=={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths}
record={'pid':p.pid,'exit_code':code,'reaped':True,'completed_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'inputs_unchanged':stable};(out/'result.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record),flush=True);assert stable;raise SystemExit(code)
