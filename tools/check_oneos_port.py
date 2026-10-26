"""Verify imported port sources and, optionally, an upstream overlay set. Offline only."""
from pathlib import Path
import argparse, hashlib, json, re

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / 'runtime/samsung-oneos'

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--overlays', type=Path, help='Built OneOS overlay directory, not a phone path')
    parser.add_argument('--set', choices=['x9u-in-sultra', 'x9u-sultra', 'n6-f8u', 'pixel-f8u', 'pixel-sultra'])
    args = parser.parse_args()
    if bool(args.overlays) != bool(args.set):
        parser.error('--overlays and --set must be specified together')
    manifest = json.loads((SNAPSHOT / 'provenance.json').read_text(encoding='utf-8'))
    sources = SNAPSHOT / 'upstream'
    errors = []
    actual = {path.relative_to(sources).as_posix() for path in sources.rglob('*')
              if path.is_file() and '__pycache__' not in path.parts}
    if actual != set(manifest['files']):
        errors.append('Snapshot file list differs from provenance')
    if (sources / 'switcher/assets').exists():
        errors.append('Restricted session assets must not be redistributed in this snapshot')
    for name, digest in manifest['files'].items():
        path = sources / name
        if path.is_symlink() or not path.is_file() or sha(path) != digest:
            errors.append('Source differs: ' + name)
    # Our recognition table must preserve upstream tuple identities, not model-only matches.
    identities = (ROOT / 'app/src/main/java/dev/tempgsi/controller/SamsungProfiles.java').read_text(encoding='utf-8')
    upstream_profiles = (sources / 'app-android/core/model/src/main/kotlin/com/fahadali/oneos/core/model/Profiles.kt').read_text(encoding='utf-8')
    tuples = re.findall(r'\{"[^"]+","([^"]+)","([^"]+)","([^"]+)"\}', identities)
    if len(tuples) != 13:
        errors.append('Expected 13 reference identities')
    upstream_tuples = set()
    for block in re.findall(r'val \w+ = PhoneProfile\(\s*(.*?)\n    \)', upstream_profiles, re.S):
        values = [re.search(field + r'\s*=\s*"([^"]+)"', block) for field in ('modelPrefix', 'codename', 'socModel')]
        if all(values):
            upstream_tuples.add(tuple(value.group(1) for value in values))
    if 'S_8850.copy(id = ID_S_8850_JP, label = "Galaxy S26 (docomo)", modelPrefix = "SC-51G")' in upstream_profiles:
        base = next(t for t in upstream_tuples if t[0] == 'SM-S942')
        upstream_tuples.add(('SC-51G', base[1], base[2]))
    for model, device, soc in tuples:
        if (model, device, soc) not in upstream_tuples:
            errors.append('Identity not present upstream: ' + model)
    checked = 0
    if args.overlays:
        entries = (sources / 'app-android/packages' / (args.set + '.sha256')).read_text(encoding='utf-8')
        for line in entries.splitlines():
            if not line.strip() or line.startswith('#'):
                continue
            match = re.fullmatch(r'([a-f0-9]{64})\s+([A-Za-z0-9_.-]+)', line)
            if not match:
                errors.append('Invalid overlay digest line')
                continue
            expected, name = match.groups()
            path = args.overlays / name
            if path.is_symlink() or not path.is_file() or sha(path) != expected:
                errors.append('Missing or different overlay: ' + name)
            checked += 1
    if errors:
        raise SystemExit('\n'.join(errors))
    print('PASS:', len(manifest['files']), 'pinned OneOS sources and 13 reference identities')
    if args.overlays:
        print('PASS:', checked, 'overlay hashes. This is NOT gsionlocked runtime installation or hardware validation.')

if __name__ == '__main__':
    main()
