"""One approved observational recording; no application or global JVM changes."""
import datetime
import json
import os
from pathlib import Path
import subprocess
import time

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
PID = 3083624


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def run(command, name, env=None):
    started = utc()
    result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True)
    (OUT / (name + '.log')).write_text(result.stdout + result.stderr)
    (OUT / (name + '-command.json')).write_text(json.dumps({
        'command': command, 'start_utc': started, 'end_utc': utc(),
        'exit': result.returncode}, indent=2) + '\n')
    if result.returncode:
        raise RuntimeError(name + ' failed; inspect its bounded command log')
    return result.stdout


def stat(path):
    # Linux stat fields after the parenthesized comm; never read cmdline/environ.
    raw = path.read_text()
    end = raw.rindex(')')
    fields = raw[end + 2:].split()
    return {'comm': raw[raw.index('(') + 1:end], 'state': fields[0],
            'utime': int(fields[11]), 'stime': int(fields[12]),
            'starttime': int(fields[19])}


def snapshot(name):
    target = Path('/proc') / str(PID)
    data = {'utc': utc(), 'monotonic': time.monotonic(),
            'clock_ticks': os.sysconf('SC_CLK_TCK'), 'cpu_count': os.cpu_count(),
            'executable': str((target / 'exe').resolve()),
            'cwd': str((target / 'cwd').resolve()),
            'process': stat(target / 'stat'), 'threads': {}, 'processes': {},
            'host_cpu': Path('/proc/stat').read_text().splitlines()[0],
            'loadavg': Path('/proc/loadavg').read_text().strip()}
    assert data['executable'] == '/usr/lib/jvm/java-21-openjdk-amd64/bin/java'
    assert data['cwd'] == '/home/err/spaces/foresight/.worktrees/truth-focus-input'
    for directory in (target / 'task').iterdir():
        try:
            data['threads'][directory.name] = stat(directory / 'stat')
        except (FileNotFoundError, ProcessLookupError):
            pass
    for directory in Path('/proc').iterdir():
        if directory.name.isdigit():
            try:
                data['processes'][directory.name] = stat(directory / 'stat')
            except (FileNotFoundError, PermissionError, ProcessLookupError):
                pass
    (OUT / (name + '.json')).write_text(json.dumps(data, indent=2) + '\n')
    return data


def world(name):
    env = dict(os.environ, JAVA_OPTS='-Xms128m -Xmx512m', TRUTH_DEMO_PORT='7895')
    form = '(load-file "' + str(OUT / 'world-observation.clj') + '")'
    run(['clojure', '-M:demo-client', form], name, env=env)


if __name__ == '__main__':
    assert not (OUT / 'native-45s.jfr').exists(), 'Never overwrite/repeat a recording'
    world('world-before')
    check = run(['jcmd', str(PID), 'JFR.check'], 'recordings-before')
    assert 'No available recordings' in check, 'Unexpected recording must be coordinated'
    before = snapshot('cpu-before')
    run(['jcmd', str(PID), 'JFR.start', 'name=truth-native-45s',
         'settings=' + str(OUT / 'minimal-safe.jfc'), 'duration=45s',
         'filename=' + str(OUT / 'native-45s.jfr'),
         'dumponexit=false', 'path-to-gc-roots=false'], 'recording-start')
    time.sleep(47)
    after = snapshot('cpu-after')
    assert before['process']['starttime'] == after['process']['starttime']
    run(['jcmd', str(PID), 'JFR.check'], 'recordings-after')
    assert (OUT / 'native-45s.jfr').stat().st_size > 0
    # Event inventory only. No payload export until the allowlist audit passes.
    run(['jfr', 'summary', str(OUT / 'native-45s.jfr')], 'recording-summary')
    world('world-after')
    print(json.dumps({'completed_utc': utc(), 'recording_bytes':
                      (OUT / 'native-45s.jfr').stat().st_size,
                      'cpu_window_seconds': after['monotonic'] - before['monotonic']}))
