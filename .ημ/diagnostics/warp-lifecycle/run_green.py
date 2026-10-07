"""Run authorized focused/full/static qualification, never benchmark or native code."""
import datetime
import hashlib
import json
import os
import pathlib
import subprocess

ROOT = pathlib.Path.cwd()
OUT = ROOT / '.ημ/diagnostics/warp-lifecycle/green'
PATHS = ['src/domain/intervention.clj', 'src/domain/ecs/registry.clj',
         'src/domain/ecs/tick.clj', 'src/domain/integrator.clj',
         'src/domain/integrator/kinematics.clj', 'src/domain/physics/cache/soa.clj',
         'test/domain/warp_lifecycle_test.clj',
         '.ημ/diagnostics/warp-lifecycle/evidence/warp.clj',
         '.ημ/diagnostics/warp-lifecycle/fixtures.edn']

def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def hashes():
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in PATHS}

def write_new(path, data):
    with path.open('x') as f:
        json.dump(data, f, indent=2)
        f.write('\n')

def run(label, cmd, extra_env=None):
    before = hashes()
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    assert head == '14bd31890acdab3b9983c6780295b42f4a065c55'
    command = {'command': cmd, 'cwd': str(ROOT), 'started_at': now(),
               'head': head, 'source_sha256': before,
               'source_diff': subprocess.check_output(['git', 'diff', '--', 'src'], text=True),
               'environment_override': extra_env or {},
               'resource_scope': 'Sequential correctness qualification; parent-owned native may overlap; not isolated performance.'}
    env = os.environ.copy()
    env.update(extra_env or {})
    with (OUT / (label + '.log')).open('x') as log:
        proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, env=env)
        command['pid'] = proc.pid
        write_new(OUT / (label + '-command.json'), command)
        rc = proc.wait()
    after = hashes()
    write_new(OUT / (label + '-result.json'),
              {'exit_code': rc, 'completed_at': now(), 'pid': proc.pid,
               'reaped': True, 'source_sha256': after, 'source_unchanged': before == after,
               'log_sha256': hashlib.sha256((OUT / (label + '.log')).read_bytes()).hexdigest()})
    print(label, 'exit', rc, 'PID', proc.pid, 'reaped', flush=True)
    assert before == after, 'Source changed during run'
    if rc:
        raise SystemExit(rc)

run('focused-attempt3', ['clojure', '-J-Xms256m', '-J-Xmx2g', '-M:test',
                '-n', 'domain.warp-lifecycle-test', '-n', 'domain.intervention-test',
                '-n', 'architecture-test', '-n', 'law.registry-test'])
run('strict-attempt2', ['bin/analyze', '--strict'], {'JAVA_TOOL_OPTIONS': '-Xms256m -Xmx2g'})
