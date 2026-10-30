"""Package the continuum experiment, source overlay and retained runtime."""
from pathlib import Path
import hashlib, json, shutil, subprocess, sys, zipfile

repo = Path(sys.argv[1]).resolve()
runtime = Path(sys.argv[2]).resolve()
destination = Path(sys.argv[3]).resolve()
bundle = destination / 'truth-continuum-impact-2026-10-24'
bundle.mkdir(parents=True, exist_ok=True)
study = repo / 'docs/demo/continuum-impact-2026-10-24'
base = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
tracked = subprocess.check_output(['git', '-C', str(repo), 'diff', '--name-only'], text=True).splitlines()
(bundle / 'overlay-files.txt').write_text('\n'.join(tracked) + '\n')
(bundle / 'source-overlay.patch').write_bytes(subprocess.check_output(
    ['git', '-C', str(repo), 'diff', '--binary', base, '--', *tracked]))
new = subprocess.check_output(['git', '-C', str(repo), 'ls-files', '--others', '--exclude-standard'], text=True).splitlines()
for name in new:
    if name.startswith(('src/', 'test/', 'dev/', 'docs/specs/', 'docs/research/', 'kanban/tasks/')) or name == 'docs/demo/continuum-impact-2026-10-24/README.md':
        target = bundle / 'new-source' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repo / name, target)
for branch in ['pilot', 'run', 'run-extended']:
    for path in (study / branch).rglob('*'):
        if path.is_file() and path.suffix in ['.json', '.edn']:
            target = bundle / 'data' / branch / path.relative_to(study / branch)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
for path in study.glob('*'):
    if path.is_file() and path.name != 'package-receipt.json' and path.suffix in ['.json', '.log', '.gif', '.png']:
        target = bundle / 'evidence' / path.name
        target.parent.mkdir(exist_ok=True)
        shutil.copy2(path, target)
shutil.copytree(runtime, bundle / 'runtime', dirs_exist_ok=True)
launcher = '''#!/usr/bin/env bash
set -euo pipefail
bundle_dir=$(cd "$(dirname "$0")" && pwd)
repo_dir=${1:?Usage: ./run-continuum.sh /absolute/Truth --check OR /absolute/NEW_OUTPUT}
shift
repo_dir=$(cd "$repo_dir" && pwd)
action=${1:---check}
overlay_dir=$(mktemp -d /tmp/truth-continuum-overlay.XXXXXX)
source_commit=BASE
while IFS= read -r file; do
 mkdir -p "$overlay_dir/$(dirname "$file")"
 git -C "$repo_dir" show "$source_commit:$file" > "$overlay_dir/$file"
done < "$bundle_dir/overlay-files.txt"
patch --silent -p1 -d "$overlay_dir" < "$bundle_dir/source-overlay.patch"
cp -R "$bundle_dir/new-source/." "$overlay_dir/"
export TRUTH_CONTINUUM_CP="$overlay_dir/src:$overlay_dir/dev:$overlay_dir/test:$repo_dir/src:$repo_dir/dev:$repo_dir/test:$repo_dir/resources:$bundle_dir/runtime/jars/*:$bundle_dir/runtime/malli/src:$bundle_dir/runtime/malli/resources:$bundle_dir/runtime/dynaload/src"
export TRUTH_ADAPTIVE_CP="$TRUTH_CONTINUUM_CP"
cd "$repo_dir"
if [[ "$action" == --check ]]; then
 java -Xmx2g -XX:ActiveProcessorCount=4 -cp "$TRUTH_CONTINUUM_CP" clojure.main -e '(require (quote domain.continuum-test) (quote architecture-test) (quote domain.material-resolution-test) (quote shape.rigid-test) (quote domain.material-debris-test) (quote infra.render.material-test) (quote infra.dev.window-test) (quote law.render-test)) (let [r (clojure.test/run-tests (quote domain.continuum-test) (quote architecture-test) (quote domain.material-resolution-test) (quote shape.rigid-test) (quote domain.material-debris-test) (quote infra.render.material-test) (quote infra.dev.window-test) (quote law.render-test))] (shutdown-agents) (System/exit (+ (:fail r) (:error r))))'
else
 if [[ "$action" != /* ]]; then echo 'Use an absolute output path.' >&2; exit 2; fi
 if [[ -e "$action" ]]; then echo 'Choose a new output directory to retain prior runs.' >&2; exit 2; fi
 mkdir -p "$action"
 java -Xmx3g -XX:ActiveProcessorCount=6 -cp "$TRUTH_CONTINUUM_CP" clojure.main -m continuum-experiment "$action/run" 900
 cp -R "$action/run" "$action/run-extended"
 java -Xmx3g -XX:ActiveProcessorCount=6 -cp "$TRUTH_CONTINUUM_CP" clojure.main -m continuum-continue "$action/run-extended" 1800
 python3 "$overlay_dir/dev/analyze_continuum.py" "$action"
 echo "Raw continuum states and measurements: $action"
fi
'''.replace('BASE', base)
(bundle / 'run-continuum.sh').write_text(launcher)
(bundle / 'run-continuum.sh').chmod(0o755)
(bundle / 'README.md').write_text('''# Truth: compatible continuum impact

Published comparison: https://truth-planetary-lab.enso-err.chatgpt.site/continuum-impact/

This is a standalone experiment overlay for a Truth checkout, with raw physical
states, measurements and native visual evidence. It requires no repository merge.
Actual recovered base commit: `''' + base + '''`.

```bash
./run-continuum.sh /absolute/path/to/Truth --check
./run-continuum.sh /absolute/path/to/Truth /absolute/path/to/NEW_OUTPUT
```

Use a checkout at the named base for faithful reproduction; the launcher overlays
the changed files without modifying that checkout. Java 21+, Bash, patch and Git
are needed; analysis needs Python 3 with NumPy. Clojure, Malli, dynaload and LWJGL
are supplied in runtime/. Native replay also needs Pillow, Matplotlib, Xvfb,
xdotool and a working OpenGL 3.3 implementation. continuum_visuals.py regenerates
meshes from exact snapshots; continuum_capture.py uses the existing native
renderer. build_continuum_site.py creates the presentation and charts from them.

Read new-source/docs/research/conservative-continuum-impact.md for the model,
observed results, retained failed pilot, source recovery and limits. This is
plane-strain material softening with illustrative parameters, no gravity or
topological fracture. Refinement covers a whole local patch. The previous
granular sphere model's 4.30 GJ subdivision failure is not claimed fixed.

data/run/ contains the original 900 s runs; data/run-extended/ contains those
records plus continuations to 1800 s. evidence/summary.json is the numerical
readout. All extrema there are over retained samples. The source and runtime
carry existing project and upstream terms; this adds no new license grant.
The required full repository analysis and benchmark gates could not run in
this environment; see their logs and the blocking validation-environment card.
''')
manifest = {str(p.relative_to(bundle)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(bundle.rglob('*')) if p.is_file() and p.name != 'SHA256SUMS.json'}
(bundle / 'SHA256SUMS.json').write_text(json.dumps(manifest, indent=2))
archive = destination / (bundle.name + '.zip')
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for path in sorted(bundle.rglob('*')):
        if path.is_file():
            z.write(path, path.relative_to(destination))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
print(json.dumps({'archive': str(archive), 'bytes': archive.stat().st_size,
                  'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
                  'base': base, 'files': len(manifest)}, indent=2))
