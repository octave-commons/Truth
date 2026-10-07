"""Run matched AFTER only after the parent releases the exclusive cost window."""
import datetime
import hashlib
import json
import pathlib
import subprocess
import time

ROOT = pathlib.Path.cwd()
BASE = ROOT / '.ημ/diagnostics/warp-lifecycle'
OUT = BASE / 'after'
EXPECTED = {
    'src/domain/intervention.clj': 'eed4697f04bf96ce8bdabc0e08b64c63bd296a0d9cc958b49574bbef643c74a8',
    'src/domain/ecs/registry.clj': 'eb5df3121fac4eb1c2e4bb1cb94b5e13984409938e3dffa3b4a6ad59a292a3dc',
    'test/domain/warp_lifecycle_test.clj': '3c15b3fae0194f0d413a347224d6e7f835af90825c16781a699859bb472393ad',
    '.ημ/diagnostics/warp-lifecycle/fixtures.edn': '2c275009a46b8bc813bd1541e7b3bdb73d51e1e7d2112b8aa0a1962f3b6fa096',
    '.ημ/diagnostics/warp-lifecycle/evidence/warp.clj': 'deba81148f3b94a19de736beb54dab9c36eb43a10a9c2f14c3d460e8eea40ed2',
}
SOURCES = list(json.loads((BASE / 'before/benchmark-command.json').read_text())['source_sha256'])
SOURCES.append('.ημ/diagnostics/warp-lifecycle/fixtures.edn')

def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def hashes():
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in SOURCES}

def write_new(path, data):
    with path.open('x') as f:
        json.dump(data, f, indent=2)
        f.write('\n')

before = hashes()
assert all(before[p] == h for p, h in EXPECTED.items()), 'Source or frozen input mismatch'
original = json.loads((BASE / 'before/benchmark-command.json').read_text())
# The only test change is the parent-approved three-line indentation correction.
# Reconstruct and hash the exact baseline bytes before allowing that exception.
reverse_test = (ROOT / 'test/domain/warp_lifecycle_test.clj').read_bytes().splitlines(keepends=True)
for i in [38, 109, 110]:
    assert reverse_test[i].startswith(b' ')
    reverse_test[i] = reverse_test[i][1:]
assert hashlib.sha256(b''.join(reverse_test)).hexdigest() == original['source_sha256']['test/domain/warp_lifecycle_test.clj']
for p, h in original['source_sha256'].items():
    if p not in ['src/domain/intervention.clj', 'src/domain/ecs/registry.clj', 'test/domain/warp_lifecycle_test.clj']:
        assert before[p] == h, 'Unrelated source changed: ' + p
cmd = original['command'].copy()
assert cmd[-3] == 'before'
cmd[-3] = 'after'
cmd[-1] = str(OUT)
OUT.mkdir(exist_ok=True)
command = {'command': cmd, 'cwd': str(ROOT), 'started_at': now(),
           'source_sha256': before,
           'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
           'source_diff': subprocess.check_output(['git', 'diff', '--', 'src'], text=True),
           'boot_id': pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
           'baseline_command_sha256': hashlib.sha256((BASE / 'before/benchmark-command.json').read_bytes()).hexdigest()}
with (OUT / 'benchmark.log').open('x') as log, (OUT / 'benchmark-load.jsonl').open('x') as load:
    proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT)
    command['pid'] = proc.pid
    write_new(OUT / 'benchmark-command.json', command)
    while proc.poll() is None:
        row = {'time': now(), 'loadavg': pathlib.Path('/proc/loadavg').read_text().strip(),
               'uptime': pathlib.Path('/proc/uptime').read_text().strip(),
               'aggregate_cpu': pathlib.Path('/proc/stat').read_text().splitlines()[0],
               'processes': subprocess.check_output(['ps', '-eo', 'pid,ppid,comm,stat,pcpu,rss'], text=True)}
        load.write(json.dumps(row) + '\n')
        load.flush()
        time.sleep(5)
    rc = proc.wait()
after = hashes()
write_new(OUT / 'benchmark-result.json', {'exit_code': rc, 'completed_at': now(),
          'pid': proc.pid, 'reaped': True, 'source_sha256': after,
          'source_unchanged': before == after,
          'log_sha256': hashlib.sha256((OUT / 'benchmark.log').read_bytes()).hexdigest()})
print('AFTER exit', rc, 'PID', proc.pid, 'reaped', flush=True)
assert before == after, 'Source changed while executing'
raise SystemExit(rc)
