"""Prevent ColorOS first-boot logic resetting the shared hardware clock."""
from pathlib import Path
import sys


def patch(text):
    old = '    invoke-static {v13, v14}, Landroid/os/SystemClock;->setCurrentTimeMillis(J)Z'
    if text.count(old) != 1 or 'reset system time here, isFirstBoot' not in text:
        raise ValueError('Unexpected ColorOS first-boot time logic')
    after = text.split(old, 1)[1].lstrip()
    if after.startswith('move-result'):
        raise ValueError('Unexpected consumed clock result')
    return text.replace(old, '    # Guest first boot must not reset the shared host clock\n    nop')


if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/com/android/server/OplusNewNetworkTimeUpdateServiceFeature.smali'))
    if len(paths) != 1:
        sys.exit('Expected one network time feature')
    path = paths[0]
    result = patch(path.read_text(encoding='utf-8'))
    with open(path, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
    print(path.relative_to(root).parts[0])
