"""One authorized reverse pair; identical frozen benchmark, new evidence only."""
import datetime
import hashlib
import json
import os
import pathlib
import subprocess
import time
import traceback

ROOT = pathlib.Path('/home/err/spaces/foresight/.worktrees/truth-warp-lifecycle')
BASELINE = pathlib.Path('/home/err/spaces/foresight/.worktrees/truth-warp-cost-baseline')
REL = pathlib.Path('.ημ/diagnostics/warp-lifecycle')
OUT = ROOT / REL / 'reverse-pair'
HEADS = {ROOT: '3d912230bc8743463d0c59b75ce312e5688a0dc0',
         BASELINE: '14bd31890acdab3b9983c6780295b42f4a065c55'}
ORIGINAL = json.loads((ROOT / REL / 'before/benchmark-command.json').read_text())
FROZEN = {
    str(REL / 'fixtures.edn'): '2c275009a46b8bc813bd1541e7b3bdb73d51e1e7d2112b8aa0a1962f3b6fa096',
    str(REL / 'evidence/warp.clj'): 'deba81148f3b94a19de736beb54dab9c36eb43a10a9c2f14c3d460e8eea40ed2',
}
CANDIDATE = {
    'src/domain/intervention.clj': 'eed4697f04bf96ce8bdabc0e08b64c63bd296a0d9cc958b49574bbef643c74a8',
    'src/domain/ecs/registry.clj': 'eb5df3121fac4eb1c2e4bb1cb94b5e13984409938e3dffa3b4a6ad59a292a3dc',
    'test/domain/warp_lifecycle_test.clj': '3c15b3fae0194f0d413a347224d6e7f835af90825c16781a699859bb472393ad',
}


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_new(path, data):
    with path.open('x') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def process_stat(pid):
    try:
        raw = pathlib.Path(f'/proc/{pid}/stat').read_text()
    except FileNotFoundError:
        return None
    fields = raw[raw.rfind(')') + 2:].split()
    return {'pid': pid, 'state': fields[0], 'start_ticks': fields[19],
            'minor_faults': int(fields[7]), 'major_faults': int(fields[9]),
            'user_cpu_ticks': int(fields[11]), 'system_cpu_ticks': int(fields[12]),
            'rss_pages': int(fields[21]), 'rss_bytes': int(fields[21]) * os.sysconf('SC_PAGE_SIZE')}


def native_stopped():
    pause_path = OUT / 'native-pause-root.json'
    pause = json.loads(pause_path.read_text())
    current = process_stat(pause['pid'])
    assert current is not None, 'Root-owned native process disappeared'
    assert current['start_ticks'] == str(pause['start_ticks']), 'Native PID identity changed'
    assert current['state'] == 'T', 'Native process is not stopped'
    assert pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip() == pause['boot_id']
    assert str(pathlib.Path(f"/proc/{pause['pid']}/cwd").resolve()) == pause['cwd']
    return {'time': now(), 'pause_record_sha256': digest(pause_path), 'observed': current}


def source_guard(worktree):
    expected = dict(ORIGINAL['source_sha256'])
    expected.update(FROZEN)
    if worktree == ROOT:
        expected.update(CANDIDATE)
        lines = (worktree / 'test/domain/warp_lifecycle_test.clj').read_bytes().splitlines(keepends=True)
        for i in [38, 109, 110]:
            assert lines[i].startswith(b' ')
            lines[i] = lines[i][1:]
        assert hashlib.sha256(b''.join(lines)).hexdigest() == ORIGINAL['source_sha256']['test/domain/warp_lifecycle_test.clj']
    actual = {p: digest(worktree / p) for p in expected}
    assert actual == expected, 'Source or immutable input changed'
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=worktree, text=True).strip()
    assert head == HEADS[worktree], 'Unexpected source commit'
    assert not subprocess.check_output(['git', 'diff', 'HEAD', '--', 'src', 'test', 'bench', 'deps.edn'], cwd=worktree)
    return {'head': head, 'sha256': actual}


def memory_snapshot():
    wanted = {'MemAvailable', 'SwapTotal', 'SwapFree', 'Dirty', 'Writeback'}
    return {parts[0].rstrip(':'): ' '.join(parts[1:])
            for line in pathlib.Path('/proc/meminfo').read_text().splitlines()
            if (parts := line.split())[0].rstrip(':') in wanted}


def run(action, worktree):
    before = source_guard(worktree)
    pause_before = native_stopped()
    output = OUT / action
    output.mkdir(exist_ok=False)
    cmd = ORIGINAL['command'].copy()
    assert cmd[-3] == 'before'
    cmd[-3:] = [action, str(worktree / REL / 'fixtures.edn'), str(output)]
    command = {'command': cmd, 'cwd': str(worktree), 'started_at': now(),
               'source_before': before, 'native_before': pause_before,
               'cpu_clock_ticks_per_second': os.sysconf('SC_CLK_TCK'),
               'page_size': os.sysconf('SC_PAGE_SIZE'),
               'benchmark_adapter_unchanged': True,
               'order': 'candidate AFTER then baseline BEFORE; one pair only',
               'environment_note': 'Existing host environment; explicit same 256MiB/2GiB JVM arguments; no profiler or alternative benchmark configuration.'}
    with (output / 'benchmark.log').open('x') as log, (output / 'benchmark-load.jsonl').open('x') as load:
        proc = subprocess.Popen(cmd, cwd=worktree, stdout=log, stderr=subprocess.STDOUT)
        command['pid'] = proc.pid
        write_new(output / 'benchmark-command.json', command)
        while proc.poll() is None:
            row = {'time': now(), 'loadavg': pathlib.Path('/proc/loadavg').read_text().strip(),
                   'aggregate_cpu': pathlib.Path('/proc/stat').read_text().splitlines()[0],
                   'uptime': pathlib.Path('/proc/uptime').read_text().strip(),
                   'benchmark_process': process_stat(proc.pid), 'memory': memory_snapshot(),
                   'processes': subprocess.check_output(['ps', '-eo', 'pid,ppid,comm,stat,pcpu,rss'], text=True)}
            load.write(json.dumps(row) + '\n')
            load.flush()
            time.sleep(5)
        rc = proc.wait()
    result = {'exit_code': rc, 'completed_at': now(), 'pid': proc.pid, 'reaped': True,
              'log_sha256': digest(output / 'benchmark.log')}
    # Preserve the real process result before guards that could themselves fail.
    write_new(output / 'process-result.json', result)
    after = source_guard(worktree)
    pause_after = native_stopped()
    write_new(output / 'verification-result.json', {'source_after': after,
              'source_unchanged': before == after, 'native_after': pause_after})
    assert before == after, 'Source changed during benchmark'
    print(action, 'exit', rc, 'PID', proc.pid, 'reaped', flush=True)
    if rc:
        raise SystemExit(rc)


if __name__ == '__main__':
    OUT.mkdir(exist_ok=True)
    try:
        source_guard(ROOT)
        source_guard(BASELINE)
        native_stopped()
        run('after', ROOT)
        run('before', BASELINE)
        write_new(OUT / 'pair-result.json', {'completed_at': now(), 'exit_code': 0,
                  'both_processes_reaped': True, 'source_heads': {str(k): v for k, v in HEADS.items()}})
    except BaseException as error:
        write_new(OUT / 'runner-failure.json', {'time': now(), 'error': repr(error),
                  'traceback': traceback.format_exc(),
                  'native_not_signalled_by_runner': True})
        raise
