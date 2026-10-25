"""Keep guest lock lifecycle notifications away from the shared host Keystore.

This closes a remaining shared-user operation; it does not implement guest
credentials or make biometric enrollment safe. Auth tokens and key operations
are intentionally not replaced with fabricated success responses.
"""
from pathlib import Path
import re
import sys

AUTH = 'Landroid/security/KeyStoreAuthorization;'
CALL = re.compile(r'^\s*invoke-virtual(?:/range)? \{[^}]+\}, ' + re.escape(AUTH)
                  + r'->(onDeviceLocked\(I\[JZ\)I|onDeviceUnlocked\(I\[B\)I|onUserStorageLocked\(I\)I)\s*$')
EXPECTED = {
    'com/android/server/locksettings/LockSettingsService.smali': {
        'onDeviceUnlocked(I[B)I': 1, 'onUserStorageLocked(I)I': 1},
    'com/android/server/trust/TrustManagerService.smali': {
        'onDeviceLocked(I[JZ)I': 1, 'onDeviceUnlocked(I[B)I': 1},
}

def patch(text, expected):
    lines = text.splitlines()
    counts = {}
    for index, line in enumerate(lines):
        match = CALL.match(line)
        if not match:
            continue
        signature = match[1]
        if signature not in expected:
            raise ValueError('Unexpected shared Keystore lifecycle call: ' + signature)
        # The pinned callers ignore these integer statuses. Never leave a
        # move-result orphaned or invent an apparent successful key unlock.
        for next_line in lines[index + 1:]:
            stripped = next_line.strip()
            if not stripped or stripped.startswith(('.', ':', '#')):
                continue
            if stripped.startswith('move-result'):
                raise ValueError('Keystore lifecycle result now consumed; review required')
            break
        counts[signature] = counts.get(signature, 0) + 1
        lines[index] = ('    # OneOS S23: guest ' + signature
            + ' must not change host user0 Keystore state.\n    nop')
    if counts != expected:
        raise ValueError('Unexpected Keystore lifecycle call set: ' + repr(counts))
    return '\n'.join(lines) + '\n'

if __name__ == '__main__':
    root = Path(sys.argv[1])
    changes = []
    for relative, expected in EXPECTED.items():
        paths = list(root.glob('smali*/' + relative))
        if len(paths) != 1:
            raise ValueError('Expected one ' + relative)
        target = paths[0]
        changes.append((target, patch(target.read_text(encoding='utf-8'), expected)))
    for target, content in changes:
        with open(target, 'w', encoding='utf-8', newline='\n') as stream:
            stream.write(content)
        print(target.relative_to(root))
