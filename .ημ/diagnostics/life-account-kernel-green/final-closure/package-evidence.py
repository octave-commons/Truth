#!/usr/bin/env python3
"""Losslessly publish new life-kernel diagnostics; no runtime, board or Git writes."""
from pathlib import Path
import datetime
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
GREEN = '.ημ/diagnostics/life-account-kernel-green/'
BASE = '98b847ce75491bdccacf2ec3a7476d451270bf49'
HEAD = 'd80c83783fd2fc974b0698e60dfc1b2a41361979'
sha = lambda raw: hashlib.sha256(raw).hexdigest()
rel = lambda path: str(path.relative_to(ROOT))

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def write(path, text):
    with path.open('x', encoding='utf-8', newline='') as stream:
        stream.write(text)

def write_json(path, value):
    write(path, json.dumps(value, indent=2, ensure_ascii=False) + '\n')

assert git('rev-parse', 'HEAD').decode().strip() == HEAD
outputs = ['package-evidence.py', 'auxiliary.jsonl', 'AUXILIARY-MAP.json',
           'packaging-proof.json', 'PACKAGING.md', 'STAGING.txt', 'SHA256SUMS']
output_paths = {rel(HERE / name) for name in outputs}
untracked = set(git('ls-files', '--others', '--exclude-standard', '-z').decode().split('\0')) - {''}
raw_paths = sorted(untracked - output_paths)
assert all(p.startswith(GREEN) or p == '.ημ/diagnostics/life-account-kernel-plan/next-boundary-assessment.md' for p in raw_paths)
assert not any((HERE / name).exists() for name in outputs if name != 'package-evidence.py')
direct_suffixes = [
    'RESULT.md', 'focused-closure.json', 'SHA256SUMS',
    'local-independent-source-review.json',
    'canonical-gate-attempt-01/RESULT.md', 'canonical-gate-attempt-01/SHA256SUMS',
    'canonical-gate-attempt-01/stdout.log', 'canonical-gate-attempt-01/stderr.log',
    'canonical-gate-attempt-02/RESULT.md', 'canonical-gate-attempt-02/SHA256SUMS',
    'canonical-gate-attempt-02/result.json', 'canonical-gate-attempt-02/stdout.log',
    'canonical-gate-attempt-02/stderr.log', 'canonical-gate-attempt-02/canonical-readback-proof.json',
    'style-repair/RESULT.md', 'style-repair/exact-delta-proof.json', 'style-repair/SHA256SUMS',
    'final-closure/RESULT.md', 'final-closure/PR-BODY.md',
    'final-closure/append-only-proof.json', 'final-closure/gate-and-style-closure.json']
direct = sorted([GREEN + x for x in direct_suffixes] +
                ['.ημ/diagnostics/life-account-kernel-plan/next-boundary-assessment.md'])
assert set(direct) <= set(raw_paths)
packed = sorted(set(raw_paths) - set(direct))
metadata = {p: {'bytes': (ROOT / p).stat().st_size,
                'sha256': sha((ROOT / p).read_bytes())} for p in raw_paths}
rows = []
archive_path = HERE / 'auxiliary.jsonl'
with archive_path.open('xb') as stream:
    for line, path in enumerate(packed, 1):
        raw = (ROOT / path).read_bytes()
        content = raw.decode('utf-8', errors='strict')
        assert content.encode('utf-8') == raw
        record = {'path': path, **metadata[path], 'content': content}
        encoded = (json.dumps(record, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')
        start = stream.tell()
        stream.write(encoded)
        rows.append({'path': path, **metadata[path], 'line': line,
                     'byte_start': start, 'byte_end': stream.tell()})
archive = archive_path.read_bytes()
recovered = {}
for row in rows:
    record = json.loads(archive[row['byte_start']:row['byte_end']])
    raw = record['content'].encode('utf-8')
    assert record['path'] == row['path'] and len(raw) == row['bytes'] and sha(raw) == row['sha256']
    assert raw == (ROOT / row['path']).read_bytes()
    assert row['path'] not in recovered
    recovered[row['path']] = raw
assert len(archive.splitlines()) == len(rows) == len(packed)
assert set(direct).isdisjoint(recovered) and set(direct) | set(recovered) == set(raw_paths)
assert all(metadata[p] == {'bytes': (ROOT / p).stat().st_size,
                           'sha256': sha((ROOT / p).read_bytes())} for p in raw_paths)
committed = set(git('diff', '--name-only', BASE, HEAD, '-z').decode().split('\0')) - {''}
modified = sorted(set(git('diff', '--name-only', '-z').decode().split('\0')) - {''})
allowed_modified = {'.ημ/receipts.edn', '.ημ/session-mycology/ledger.md',
                    'kanban/tasks/.events/ledger.edn', 'kanban/tasks/life-account-origin-kernel.md',
                    'src/law/life_account.clj', 'src/domain/life_account.clj',
                    'test/law/life_account_test.clj', 'test/domain/life_account_test.clj'}
assert set(modified) == allowed_modified
staging = sorted(set(modified) | set(direct) | output_paths)
assert set(modified) <= committed
prospective = sorted(committed | set(staging))
assert len(prospective) <= 100
mapping = {'version': 1, 'root': 'repository root', 'base': BASE, 'head_before_green': HEAD,
           'encoding': 'UTF-8 JSONL: encode decoded content as UTF-8 to reconstruct original bytes',
           'archive': rel(archive_path), 'archive_bytes': len(archive), 'archive_sha256': sha(archive),
           'original_count': len(raw_paths), 'original_bytes': sum(v['bytes'] for v in metadata.values()),
           'direct': {p: metadata[p] for p in direct}, 'packed': rows,
           'already_committed_paths': sorted(committed), 'modified_tracked_paths': modified,
           'new_direct_publication_paths': sorted(set(direct) | output_paths),
           'cumulative_pr_paths': prospective,
           'no_omitted_originals': True, 'all_local_originals_retained_unchanged': True}
write_json(HERE / 'AUXILIARY-MAP.json', mapping)
write(HERE / 'STAGING.txt', '\n'.join(staging) + '\n')
proof = {'at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
         'base': BASE, 'head_before_green': HEAD,
         'original_files': len(raw_paths), 'original_bytes': mapping['original_bytes'],
         'direct_original_files': len(direct), 'packed_original_files': len(packed),
         'packed_original_bytes': sum(metadata[p]['bytes'] for p in packed),
         'empty_packed_files': sum(metadata[p]['bytes'] == 0 for p in packed),
         'archive_bytes': len(archive), 'archive_sha256': sha(archive),
         'archive_map_sha256': sha((HERE / 'AUXILIARY-MAP.json').read_bytes()),
         'script_sha256': sha(Path(__file__).read_bytes()),
         'all_packed_byte_roundtrips_equal': True, 'all_raw_files_unchanged': True,
         'disjoint_complete_partition': True, 'already_committed_paths_preserved': len(committed),
         'stage_paths': len(staging), 'new_publication_paths': len(set(direct) | output_paths),
         'cumulative_pr_path_count': len(prospective),
         'no_runtime_board_source_or_git_mutation': True,
         'workflow': {'path': '.github/workflows/eta-mu-review.yml',
                      'sha256': sha((ROOT / '.github/workflows/eta-mu-review.yml').read_bytes()),
                      'upstream_pin': '09a4454480baa67f6fdc40f6f73f5e48ef0457d1',
                      'review_timeout_minutes': 45, 'explicit_120_input': False,
                      'source': 'inspected exact upstream Git blob, review job line 1004; unchanged'}}
write_json(HERE / 'packaging-proof.json', proof)
write(HERE / 'PACKAGING.md', f'''# Lossless life-kernel evidence publication

This package represents **{len(raw_paths)} new diagnostic originals / {mapping['original_bytes']:,} bytes**:
{len(direct)} direct originals and {len(packed)} packed originals. Every original remains
byte-for-byte unchanged locally. The {len(committed)} paths already changed against the
actual PR38 base are preserved as normal Git history; no old path is packed,
deleted or rewritten to reduce this count. Authorized source/card/ledger changes
remain direct. Historical manifests are kept unchanged and resolve their packed
original paths through this map.

[AUXILIARY-MAP.json](AUXILIARY-MAP.json) is a complete disjoint partition.
Each packed entry identifies its original repository-relative path, byte count,
SHA256, one-based JSONL line and UTF-8 record byte span in
[auxiliary.jsonl](auxiliary.jsonl). Decode the `content` field and encode it as
UTF-8: newlines, Unicode, trailing whitespace and {proof['empty_packed_files']} empty files survive
exactly. Nothing is summarized, truncated or omitted from the archived evidence.
[packaging-proof.json](packaging-proof.json) records the verified byte equality
for every original and the [one-shot script](package-evidence.py) records how it
was produced. The script does not run tests, call Rheos, mutate Git, or touch
source/runtime state.

All new raw diagnostics are either direct or packed, including complete failed
and successful logs, commands, process observations, source snapshots, canonical
readbacks, and receipt append evidence. Human-readable results, final gate logs,
key proofs and original manifests are direct for convenient review. A historical
path appearing only in the archive is retrievable evidence, not a broken/omitted
input. An extractor must use a new empty destination and validate each byte count
and hash; never overwrite an existing original.

[STAGING.txt](STAGING.txt) contains exactly {len(staging)} proposed GREEN-commit paths:
{len(modified)} already tracked modifications plus {len(set(direct) | output_paths)} new paths.
The cumulative diff against `{BASE}` contains **{len(prospective)} paths**. This is a
representation decision only: it does not claim a particular hosted reviewer
actually consumed every record or confer review approval. The existing upstream
MiMo caller still has a 45-minute review job; no 120-minute option was added.
[SHA256SUMS](SHA256SUMS) hashes every staged path except itself. The actual Git
diff byte count is reported separately after staging to avoid a self-referential
manifest. A fresh hosted review must still establish complete input coverage.
''')
write(HERE / 'SHA256SUMS', ''.join(f'{sha((ROOT / p).read_bytes())}  {p}\n'
                                for p in staging if p != rel(HERE / 'SHA256SUMS')))
print(json.dumps(proof, indent=2))
