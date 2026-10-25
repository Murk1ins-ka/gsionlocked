"""Reject guest credential verification against the shared host Gatekeeper.

Verification isn't read-only: failed attempts can change TEE lockout counters.
Until an isolated backend exists, return a real ERROR response, never a token
or fabricated successful authentication. Existing enrollment guards remain.
"""
from pathlib import Path
import re
import sys

CALL = re.compile(r'^\s*invoke-interface(?:/range)? \{[^}]+\}, Landroid/service/gatekeeper/IGateKeeperService;->verifyChallenge\(IJ\[B\[B\)Landroid/service/gatekeeper/GateKeeperResponse;\s*$')
ERROR = 'Landroid/service/gatekeeper/GateKeeperResponse;->ERROR:Landroid/service/gatekeeper/GateKeeperResponse;'

def patch(text, expected_count=3):
    lines = text.splitlines()
    count = 0
    for index, line in enumerate(lines):
        if not CALL.match(line):
            continue
        result_index = index + 1
        while result_index < len(lines) and (not lines[result_index].strip() or lines[result_index].lstrip().startswith(('.', '#'))):
            result_index += 1
        match = re.fullmatch(r'\s*move-result-object ([vp]\d+)\s*', lines[result_index]) if result_index < len(lines) else None
        if not match:
            raise ValueError('Unexpected Gatekeeper result register')
        register = int(match[1][1:])
        if match[1].startswith('p'):
            method_start = next((i for i in range(index, -1, -1) if lines[i].startswith('.method ')), None)
            if method_start is None:
                raise ValueError('Missing enclosing method')
            locals_match = next((re.fullmatch(r'\s*\.locals (\d+)\s*', item) for item in lines[method_start:index] if item.strip().startswith('.locals ')), None)
            if locals_match is None:
                raise ValueError('Parameter result requires verified .locals count')
            register += int(locals_match[1])
        if register > 255:
            raise ValueError('Gatekeeper result exceeds sget-object register encoding')
        lines[index] = '    # OneOS S23: host verification can change shared TEE lockout state.\n    nop'
        lines[result_index] = '    sget-object ' + match[1] + ', ' + ERROR
        count += 1
    if count != expected_count:
        raise ValueError('Expected ' + str(expected_count) + ' Gatekeeper verify calls, found ' + str(count))
    return '\n'.join(lines) + '\n'

if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/com/android/server/locksettings/SyntheticPasswordManager.smali'))
    if len(paths) != 1:
        sys.exit('Expected one SyntheticPasswordManager')
    target = paths[0]
    result = patch(target.read_text(encoding='utf-8'))
    with open(target, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
    print(target.relative_to(root))
