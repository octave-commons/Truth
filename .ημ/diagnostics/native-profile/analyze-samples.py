"""Offline aggregation of the audited six-event export and safe /proc counters."""
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import statistics

OUT = Path(__file__).resolve().parent
source = OUT / 'selected-events.json'
raw = source.read_bytes() if source.exists() else gzip.decompress((OUT / 'selected-events.json.gz').read_bytes())
events = json.loads(raw)['recording']['events']


def rows(counter, n=40):
    return [{'name': name, 'count': count} for name, count in counter.most_common(n)]


def method(frame):
    method = frame['method']
    return method['type']['name'] + '.' + method['name']


def seconds(duration):
    assert duration.startswith('PT') and duration.endswith('S')
    return float(duration[2:-1])


samples = {}
for kind in ['jdk.ExecutionSample', 'jdk.NativeMethodSample']:
    matching = [e['values'] for e in events if e['type'] == kind]
    threads, inclusive, project_leaf, leaf = Counter(), Counter(), Counter(), Counter()
    per_thread = defaultdict(Counter)
    truncated = 0
    for event in matching:
        thread = event['sampledThread']['javaName'] or event['sampledThread']['osName']
        threads[thread] += 1
        stack = event.get('stackTrace') or {}
        truncated += bool(stack.get('truncated'))
        frames = [method(frame) for frame in stack.get('frames', [])]
        if frames:
            leaf[frames[0]] += 1
        projects = [frame for frame in frames if frame.startswith(('domain/', 'infra/', 'shape/', 'law/'))]
        if projects:
            project_leaf[projects[0]] += 1
        for frame in set(projects):
            inclusive[frame] += 1
            per_thread[thread][frame] += 1
    samples[kind] = {'total': len(matching), 'truncated': truncated,
                     'threads': rows(threads), 'inclusive_project': rows(inclusive, 100),
                     'first_project_frame': rows(project_leaf, 70), 'leaf_frames': rows(leaf),
                     'per_thread_project': {name: rows(counts, 35) for name, counts in per_thread.items()}}

before = json.loads((OUT / 'cpu-before.json').read_text())
after = json.loads((OUT / 'cpu-after.json').read_text())
elapsed = after['monotonic'] - before['monotonic']
hz = before['clock_ticks']


def cpu_delta(left, right):
    assert left['starttime'] == right['starttime']
    return ((right['utime'] + right['stime']) - (left['utime'] + left['stime'])) / hz


def cpu_rows(key):
    result = []
    for identity, right in after[key].items():
        left = before[key].get(identity)
        if left and left['starttime'] == right['starttime']:
            delta = cpu_delta(left, right)
            if delta > 0:
                result.append({'id': identity, 'comm': right['comm'], 'cpu_seconds': delta,
                               'average_cores': delta / elapsed})
    return sorted(result, key=lambda row: row['cpu_seconds'], reverse=True)


pauses = [seconds(e['values']['duration']) for e in events if e['type'] == 'jdk.GCPhasePause']
loads = [e['values'] for e in events if e['type'] == 'jdk.CPULoad']
result = {'export_sha256': hashlib.sha256(raw).hexdigest(),
          'event_counts': dict(Counter(e['type'] for e in events)),
          'sample_summary': samples,
          'cpu_window_seconds': elapsed, 'cpu_count': before['cpu_count'],
          'target_cpu_seconds': cpu_delta(before['process'], after['process']),
          'thread_cpu': cpu_rows('threads'), 'process_cpu': cpu_rows('processes'),
          'gc': {'count': len(pauses), 'total_pause_seconds': sum(pauses),
                 'median_pause_seconds': statistics.median(pauses), 'max_pause_seconds': max(pauses)},
          'cpu_load': {key: {'mean': statistics.mean(e[key] for e in loads),
                             'min': min(e[key] for e in loads), 'max': max(e[key] for e in loads)}
                       for key in ['jvmUser', 'jvmSystem', 'machineTotal']},
          'limits': ['Sampling counts are not per-system timings or independently additive costs.',
                     'Native method samples show Java/JNI boundaries, not Mesa native-worker stacks.',
                     'OS counters include all target native threads; terminated threads are absent from deltas.',
                     'The CPU counter interval surrounds the 45s recording and is longer than it.']}
(OUT / 'sample-summary.json').write_text(json.dumps(result, indent=2) + '\n')
execution = [e['values'] for e in events if e['type'] == 'jdk.ExecutionSample']
prefixes = {'Kepler': 'domain/orbital/kepler$',
            'Barnes-Hut force': 'domain/gravity/barnes_hut/force$',
            'Spatial index': 'domain/spatial/index$',
            'Integrator kinematics': 'domain/integrator/kinematics$',
            'Hydro+EM force': 'domain/mhd/force$',
            'Rendering': 'infra/render/', 'Physics SoA': 'domain/physics/cache/soa$'}
groups = {}
for group, prefix in prefixes.items():
    count = sum(any(frame['method']['type']['name'].startswith(prefix)
                    for frame in (event.get('stackTrace') or {}).get('frames', []))
                for event in execution)
    groups[group] = {'samples': count, 'share_of_java_samples': count / len(execution)}
render_native = [e['values'] for e in events if e['type'] == 'jdk.NativeMethodSample'
                 and e['values']['sampledThread']['javaName'] == 'gates-of-truth-dev-window']
swaps = sum(any(frame['method']['type']['name'] == 'org/lwjgl/glfw/GLFW'
                and frame['method']['name'] == 'glfwSwapBuffers'
                for frame in event['stackTrace']['frames']) for event in render_native)
llvmpipe = [row for row in result['thread_cpu'] if row['comm'].startswith('llvmpipe')]
pipe_cpu = sum(row['cpu_seconds'] for row in llvmpipe)
aggregate = {'inclusive_namespace_union': groups, 'render_native_samples': len(render_native),
             'swap_buffer_samples': swaps, 'llvmpipe_threads': len(llvmpipe),
             'llvmpipe_cpu_seconds': pipe_cpu,
             'llvmpipe_share_of_target_cpu': pipe_cpu / result['target_cpu_seconds'],
             'target_average_cores': result['target_cpu_seconds'] / elapsed,
             'gc_pause_fraction_of_recording': sum(pauses) / 45}
(OUT / 'hotspot-aggregates.json').write_text(json.dumps(aggregate, indent=2) + '\n')
for kind, report in samples.items():
    print(kind, 'samples', report['total'], 'truncated', report['truncated'])
    print('threads', report['threads'][:8])
    print('project leaves', report['first_project_frame'][:18])
    print('project inclusive', report['inclusive_project'][:15])
print('cpu_seconds', result['target_cpu_seconds'], 'elapsed', elapsed)
print('threads', result['thread_cpu'][:18])
print('processes', result['process_cpu'][:12])
print('gc', result['gc'], 'load', result['cpu_load'])

# Preserve the full selected export losslessly without checking in 67MB of repetition.
compressed = gzip.compress(raw, compresslevel=9, mtime=0)
assert gzip.decompress(compressed) == raw
(OUT / 'selected-events.json.gz').write_bytes(compressed)
if source.exists():
    source.unlink()
