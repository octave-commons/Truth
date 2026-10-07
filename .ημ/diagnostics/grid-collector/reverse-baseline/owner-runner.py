from pathlib import Path
import subprocess,hashlib,json,datetime
root=Path.cwd();p=root/'src/domain/spatial/index.clj';candidate=p.read_bytes();old=subprocess.check_output(['git','show','8f953d2bcfab2fa2b3fef8a390157a4629eab9e0:src/domain/spatial/index.clj']);digest=lambda x:hashlib.sha256(x).hexdigest()
assert digest(candidate)=='3685ddefe8ccedd3af3716678684a041ec50e1a08b1cbc89d8025ebfa3b8437a' and digest(old)=='280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701'
rc=None
try:
 p.write_bytes(old)
 rc=subprocess.run(['python3','/tmp/truth-grid-measure-reverse-baseline.py'],cwd=root).returncode
finally:
 assert digest(p.read_bytes())==digest(old),'Source changed by another owner; do not overwrite'
 p.write_bytes(candidate)
 proof={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'restored_candidate_sha256':digest(p.read_bytes()),'return_baseline_exit_code':rc,'original_candidate_equal':p.read_bytes()==candidate}
 (root/'.ημ/diagnostics/grid-collector/reverse-baseline/source-restored.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof),flush=True)
raise SystemExit(rc)
