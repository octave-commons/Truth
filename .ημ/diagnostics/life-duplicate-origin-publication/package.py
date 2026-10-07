#!/usr/bin/env python3
"""Build once (--build), or read-only verify, the duplicate-origin publication.

This producer is authored before its initial generation. It executes no project
code and uses Git only for immutable object/diff reads. Existing outputs are
never overwritten; later repairs require separately recorded successor evidence.
"""
import hashlib
import json
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
RAW = ROOT / '.ημ/diagnostics/life-duplicate-origin-review-fix'
BASE = '98b847ce75491bdccacf2ec3a7476d451270bf49'
REMOTE = '42566341ceb6ede9b5f7c1c7f8013ab59d4e1192'
RED = 'c57a4799f032bf962b91dc3d76bc8cd86dc2ab54'
DIRECT = RAW / 'canonical-gate-root-closure.json'
HISTORICAL = RAW / 'RED-OBSERVED.json'
OUTPUTS = ['package.py', 'evidence.jsonl', 'MAP.json', 'RESULT.md', 'STAGING.txt']
COMPANIONS = ['src/law/life_account.clj', 'kanban/tasks/life-account-origin-kernel.md',
              'kanban/tasks/.events/ledger.edn', '.ημ/receipts.edn',
              '.ημ/session-mycology/ledger.md']


def sha(data):
    return hashlib.sha256(data).hexdigest()


def relative(path):
    return path.relative_to(ROOT).as_posix()


def info(path):
    data = path.read_bytes()
    return {'path': relative(path), 'bytes': len(data), 'sha256': sha(data)}


def load(path):
    return json.loads(path.read_bytes())


def git(*args):
    return subprocess.check_output(['git', '-c', 'core.quotePath=false', *args], cwd=ROOT)


def raw_paths():
    return sorted([p for directory in [RAW, HERE / 'inputs'] for p in directory.rglob('*') if p.is_file()], key=relative)


def verify_outcomes():
    root = load(DIRECT)
    assert sha(DIRECT.read_bytes()) == '9de0fb944473fe96d50987e18db0ef6d3926c453cda67f17a81c103b3e490cc4'
    assert root['tests'] == {'tests': 944, 'assertions': 16247, 'failures': 0, 'errors': 0}
    assert root['primary_reaped'] and not root['cleanup_errors']
    assert len(root['processes_absent']) == 16
    readback = load(RAW / 'canonical-gate-readback.json')
    assert sha((RAW / 'canonical-gate-readback.json').read_bytes()) == root['readback_sha256']
    # This is a saved canonical Rheos response, not a Markdown board parser.
    frontmatter = json.loads(readback['stdout'])['frontmatter']
    assert frontmatter['status'] == 'review' and frontmatter['points'] == '3'
    checks = []
    definitions = [
        ('red', RAW / 'red-runner/root-closure.json', RAW / 'red-runner/runs/red-01', 'files', 'observed', 4),
        ('focused-green', RAW / 'green-runner/root-closure.json', RAW / 'green-runner/runs/green-01', 'files', 'actual', 0),
        ('canonical-gate', DIRECT, RAW / 'canonical-gate-runner/runs/gate-01', 'raw_files', 'tests', 0),
    ]
    for label, closure_path, run, files_key, outcome_key, failures in definitions:
        closure = load(closure_path)
        for name, expected in closure[files_key].items():
            actual = info(run / name)
            assert actual['bytes'] == expected['bytes'] and actual['sha256'] == expected['sha256'], name
        outcome = closure[outcome_key]
        assert outcome['failures'] == failures and outcome['errors'] == 0
        text = (run / 'stdout.log').read_text()
        assert re.findall(r'^Ran (\d+) tests containing (\d+) assertions\.$', text, re.M) == [(str(outcome['tests']), str(outcome['assertions']))]
        assert re.findall(r'^(\d+) failures, (\d+) errors\.$', text, re.M) == [(str(failures), '0')]
        checks.append({'name': label, 'closure': info(closure_path), 'outcome': outcome,
                       'raw_files_verified': len(closure[files_key]), 'elapsed_s': closure['elapsed_s']})
    execution = load(RAW / 'canonical-gate-runner/runs/gate-01/execution.json')
    assert execution['status'] == 'complete' and execution['exit_code'] == 0
    for key in ['remaining_group', 'remaining_observed', 'cleanup_errors', 'membership_unknown', 'source_changed', 'preparation_changed']:
        assert not execution[key], key
    assert execution['primary_reaped'] and not execution['total_deadline_exceeded']
    stdout = (RAW / 'canonical-gate-runner/runs/gate-01/stdout.log').read_text()
    assert 'moved life-account-origin-kernel: in_progress -> review' in stdout.splitlines()
    for name in ['clj-kondo', 'structural smells', 'Splint', 'clojure-lsp', 'jscpd', 'cljfmt']:
        assert name in stdout, name
    pins = load(RAW / 'canonical-gate-runner/source-pins.json')
    assert len(pins['candidate']) == 319
    for name, digest in pins['candidate'].items():
        assert sha((ROOT / name).read_bytes()) == digest, name
    prep = load(RAW / 'canonical-gate-runner/preparation-hashes.json')
    for name, digest in prep.items():
        assert sha((RAW / 'canonical-gate-runner' / name).read_bytes()) == digest, name
    assert HISTORICAL.read_bytes() == git('show', RED + ':' + relative(HISTORICAL))
    return {'observed_runs': checks, 'canonical_status': 'review', 'canonical_points': '3',
            'candidate_pins_verified': 319, 'preparation_pins_verified': len(prep),
            'recorded_absent_identities': 16, 'process_limit': 'Absence/reap audited from immutable root closure; no fresh process probe.',
            'law_sha256': pins['candidate']['src/law/life_account.clj']}


def new_file(name, data):
    with (HERE / name).open('xb') as stream:
        stream.write(data)


def build():
    assert all(not (HERE / name).exists() for name in OUTPUTS if name != 'package.py')
    verified = verify_outcomes()
    originals = raw_paths()
    packed = [p for p in originals if p not in [DIRECT, HISTORICAL]]
    encoded = bytearray()
    entries = []
    for number, path in enumerate(packed, 1):
        data = path.read_bytes()
        text = data.decode('utf-8')
        assert text.encode('utf-8') == data
        row = {**info(path), 'text': text}
        line = (json.dumps(row, ensure_ascii=False, separators=(',', ':')) + '\n').encode()
        entries.append({**info(path), 'line': number, 'archive_byte_offset': len(encoded), 'archive_line_bytes': len(line)})
        encoded.extend(line)
    new_file('evidence.jsonl', bytes(encoded))
    old_paths = git('diff', '--no-renames', '--name-only', '-z', BASE, RED).decode().split('\0')[:-1]
    remote_paths = git('diff', '--no-renames', '--name-only', '-z', BASE, REMOTE).decode().split('\0')[:-1]
    assert len(remote_paths) == 100 and len(old_paths) == 101
    stage = sorted(set(COMPANIONS + [relative(DIRECT)] + [relative(HERE / name) for name in OUTPUTS]))
    cumulative = sorted(set(old_paths) | set(stage))
    new_file('STAGING.txt', ('\n'.join(stage) + '\n').encode())
    review = load(HERE / 'inputs/native-review.json')
    native = load(HERE / 'inputs/native-pr.json')
    assert review['id'] == 5446881815 and review['commit_id'] == REMOTE
    assert native['head']['sha'] == REMOTE and native['base']['sha'] == BASE and native['changed_files'] == 100
    ignored_section = review['body'].split('<summary>⛔ Files ignored due to path filters (8)</summary>')[1].split('</details>')[0]
    ignored = re.findall(r'^\* `([^`]+)` is excluded by `([^`]+)`$', ignored_section, re.M)
    assert len(ignored) == 8 and all(pattern == '!**/*.log' for _, pattern in ignored)
    assert '<summary>📒 Files selected for processing (92)</summary>' in review['body']
    conditional = len(cumulative) - len(ignored)
    source = info(ROOT / 'src/law/life_account.clj')
    empty = sum(p.stat().st_size == 0 for p in packed)
    original_bytes = sum(p.stat().st_size for p in packed)
    result = f'''# Duplicate accepted-origin repair: observed GREEN

The pure operation-context law now rejects more than one supplied accepted origin for the requested/current account, before retry lookup. Empty, partial, rejected and unrelated-account histories retain their existing behavior; this remains validation of supplied facts, not proof of global completeness. No ECS, lifecycle, biological producer, clock, rendering, input or Gate behavior is introduced.

The review finding is CodeRabbit review `5446881815`, inline `4210730351`, thread `PRRT_kwDOTDahac6qDIeZ`, on published head `{REMOTE}`. The meaningful RED checkpoint `{RED}` added tests first. Source and tests remain direct normal files; this package only represents diagnostic evidence.

| Actual execution | Tests | Assertions | Failures | Errors | Elapsed seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| Focused RED | 38 | 508 | 4 | 0 | 4.095704 |
| Focused GREEN | 38 | 508 | 0 | 0 | 4.234642 |
| Canonical full gate | 944 | 16,247 | 0 | 0 | 82.308369 |

The full gate ran once from 2026-10-07T20:10:23.069694Z to 20:11:45.378057Z, using the configured canonical Rheos move. Full tests and all six strict tools passed; actual output records the move to Review. Saved canonical readback confirms `status: review`, `points: 3`. Root closure records the primary reaped and all 16 observed owned identities absent. The package independently checks the raw files, 319 pinned inputs and preparation hashes; it does not run a workload or probe live processes.

The raw stderr includes the existing `infra.dev.window-test` boom fixture stack trace and JVM-option notices. The zero-error test result and strict success are recorded alongside that stderr, not inferred from silence. Two root orchestration mistakes are retained: an exclusive-lane preflight initially included exempt retained PID131581 and refused before launch; the first post-run readback checker used `estimate` rather than the actual `points` key. Neither caused a workload retry. Retained native process health is not asserted.

The earlier unexecuted 900/910-second preparation is preserved alongside the later root-selected 280-second work / 300-second total wrapper and exact refinement. Historical RED, preparation and scope claims are unchanged. The current law SHA-256 is `{source['sha256']}`; no production change followed qualification.

The [archive](evidence.jsonl) carries **{len(packed)} exact UTF-8 originals / {original_bytes:,} decoded bytes / {len(encoded):,} encoded bytes**, including **{empty} empty files**. [MAP.json](MAP.json) records every original path, byte count, hash and byte offset, plus direct and already-committed classifications. The full root [gate closure](../life-duplicate-origin-review-fix/canonical-gate-root-closure.json) remains direct. All local originals remain unchanged. Native capacity responses are also preserved as archive inputs with exact fetch commands.

Publication size is **{len(cumulative)} cumulative no-rename path names**, compared with 100 on remote and 101 at the local RED checkpoint. Native [review5446881815](https://github.com/octave-commons/Truth/pull/38#pullrequestreview-5446881815) selected92 and excluded8 `.log` paths on the remote head. If the same eight exclusions apply, this proposed package supplies **{conditional} included paths**; one later independent-review artifact would make **{conditional + 1}**. These are conditional calculations, not new-head provider admission. No local CodeRabbit configuration is present, and organization settings were not fetched. Current [official plans](https://docs.coderabbit.ai/management/plans) give OSS100–300 after filtering; the exact repository maximum is unverified. No paid usage or review request was made.

Generate-once provenance is explicit: `python3 .ημ/diagnostics/life-duplicate-origin-publication/package.py --build` creates this package once and refuses existing outputs. The same command without `--build` performs read-only verification. There was no post-generation repair used to retroactively represent how the initial package was produced. Root owns subsequent staging, commit, publication and finding settlement. This is not hosted approval or gameplay advancement.
'''
    new_file('RESULT.md', result.encode())
    rows = [json.loads(line) for line in bytes(encoded).splitlines()]
    assert len(rows) == len(entries) and len({x['path'] for x in rows}) == len(rows)
    for row, entry in zip(rows, entries):
        raw = row['text'].encode()
        assert raw == (ROOT / row['path']).read_bytes()
        assert len(raw) == entry['bytes'] and sha(raw) == entry['sha256']
    baseline_inventory = []
    for name in old_paths:
        data = git('show', RED + ':' + name)
        baseline_inventory.append({'path': name, 'bytes': len(data), 'sha256': sha(data)})
    mapping = {'format': 'Exact UTF-8 text per compact JSONL record; no omission, normalization, truncation or compression.',
               'base': BASE, 'remote_observed': REMOTE, 'local_RED': RED,
               'generation': {'initial_argv': ['python3', relative(HERE / 'package.py'), '--build'],
                              'verification_argv': ['python3', relative(HERE / 'package.py')],
                              'cwd': str(ROOT), 'producer_sha256': sha((HERE / 'package.py').read_bytes()),
                              'phase': 'Initial generation from immutable originals; not post-repair reconstruction.',
                              'prior_package_outputs': 'Absent; create-exclusive writes only.'},
               'archive': info(HERE / 'evidence.jsonl'), 'packed': entries,
               'direct_originals': [info(DIRECT)], 'already_committed_originals': [info(HISTORICAL)],
               'all_original_count': len(originals), 'all_original_bytes': sum(p.stat().st_size for p in originals),
               'packed_original_bytes': original_bytes, 'empty_originals': empty,
               'roundtrip': {'all_exact_bytes': True, 'all_paths_unique': True, 'all_originals_classified': True},
               'observed_verification': verified,
               'capacity': {'native_review': review['id'], 'head': REMOTE, 'remote_changed': 100,
                            'native_selected': 92, 'native_exclusions': [{'path': p, 'filter': f} for p, f in ignored],
                            'proposed_no_rename_paths': len(cumulative), 'conditional_included_same_exclusions': conditional,
                            'repository_exact_included_limit': 'UNKNOWN; official OSS range100–300, no universal100 claim.',
                            'local_config': 'No .coderabbit.yml/.yaml; organization config not fetched.'},
               'RED_baseline_inventory': baseline_inventory, 'prospective_staging_paths': stage,
               'cumulative_no_rename_paths': cumulative,
               'companion_boundary': 'Source/card/ledger/receipts remain direct, not packed. Root may append provenance; immutable raw selection does not include those live companions.',
               'package_files': [info(HERE / name) for name in OUTPUTS if name != 'MAP.json']}
    new_file('MAP.json', (json.dumps(mapping, indent=2, ensure_ascii=False) + '\n').encode())
    verify()


def verify():
    verified = verify_outcomes()
    mapping = load(HERE / 'MAP.json')
    archive = (HERE / 'evidence.jsonl').read_bytes()
    assert sha(archive) == mapping['archive']['sha256']
    lines = archive.splitlines(keepends=True)
    assert len(lines) == len(mapping['packed'])
    paths = []
    offset = 0
    for line, entry in zip(lines, mapping['packed']):
        assert len(line) == entry['archive_line_bytes'] and offset == entry['archive_byte_offset']
        offset += len(line)
        row = json.loads(line)
        raw = row['text'].encode('utf-8')
        assert raw == (ROOT / row['path']).read_bytes()
        assert row['path'] == entry['path'] and len(raw) == row['bytes'] == entry['bytes']
        assert sha(raw) == row['sha256'] == entry['sha256']
        paths.append(row['path'])
    for entry in mapping['direct_originals'] + mapping['already_committed_originals'] + mapping['package_files']:
        assert info(ROOT / entry['path']) == entry, entry['path']
    paths += [x['path'] for key in ['direct_originals', 'already_committed_originals'] for x in mapping[key]]
    assert len(paths) == len(set(paths)) and set(paths) == {relative(p) for p in raw_paths()}
    print(json.dumps({'verified': True, 'originals': len(paths), 'packed': len(lines),
                      'decoded_bytes': mapping['packed_original_bytes'], 'encoded_bytes': len(archive),
                      'candidate_pins': verified['candidate_pins_verified'],
                      'cumulative_paths': len(mapping['cumulative_no_rename_paths']),
                      'conditional_included_paths': mapping['capacity']['conditional_included_same_exclusions'],
                      'MAP_sha256': sha((HERE / 'MAP.json').read_bytes())}, indent=2))


if __name__ == '__main__':
    assert sys.argv[1:] in [[], ['--build']], 'Only optional --build is accepted'
    build() if sys.argv[1:] else verify()
