"""Import a pinned, source-only OneOS porting snapshot. Never accesses a phone."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess

REVISION = '73c3be36c35462742503380e1f025fead3cea48c'
ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, required=True, help='Local clone of fahadalijaved23/OneOS')
    args = p.parse_args()
    repo = args.repo.resolve()
    revision = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != REVISION:
        p.error('Expected pinned upstream commit ' + REVISION)
    paths = ['LICENSE', 'NOTICE', 'CREDITS.md', 'README.md', 'switcher',
             'scripts/make-overlays.sh', 'scripts/check-dex.py', 'scripts/check-shell.py',
             'scripts/host-installd-table.sh', 'app-android/packages',
             'app-android/core/model/src/main', 'app-android/core/root/src/main',
             'app-android/core/safety/src/main', 'app-android/app/src/full/assets/root']
    # Use committed blobs, never untracked local output or a modified working copy.
    tracked = subprocess.check_output(['git', '-C', str(repo), 'ls-tree', '-r', '--name-only', REVISION], text=True).splitlines()
    selected = [name for name in tracked if any(name == x or name.startswith(x + '/') for x in paths)
                and not name.startswith('switcher/assets/')]
    # Upstream NOTICE excludes OneLineage-derived session scripts from the general
    # reuse permission. Keep those out of this independently redistributed snapshot.
    destination = ROOT / 'runtime/samsung-oneos/upstream'
    entries = {}
    for name in selected:
        if Path(name).suffix.lower() in {'.apk', '.jar', '.so', '.img', '.zip', '.gz', '.xz'}:
            raise RuntimeError('Unexpected binary in source snapshot: ' + name)
        data = subprocess.check_output(['git', '-C', str(repo), 'show', REVISION + ':' + name])
        data.decode('utf-8')
        target = destination / name
        if target.exists() and target.read_bytes() != data:
            p.error('Existing source differs and is preserved: ' + name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        entries[name] = hashlib.sha256(data).hexdigest()
    (destination.parent / 'provenance.json').write_text(json.dumps({
        'repository': 'https://github.com/fahadalijaved23/OneOS', 'commit': REVISION,
        'purpose': 'Porting sources only; not loaded or executed by the Android app',
        'files': entries}, indent=2) + '\n', encoding='utf-8')
    print('Imported', len(entries), 'pinned source files; no phone or runtime binaries')

if __name__ == '__main__':
    main()
