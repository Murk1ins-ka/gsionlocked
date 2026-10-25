"""Select existing legacy netstats APIs throughout the guest framework."""
from pathlib import Path
import re
import sys

HEADER = '.method public static blacklist useNetstatsPerQueryFlags()Z'

def patch(text):
    pattern = re.compile(re.escape(HEADER) + r'\n(.*?)\n\.end method', re.S)
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise ValueError('Expected one netstats feature flag method')
    match = matches[0]
    body = match.group(1)
    instructions = [line.strip() for line in body.splitlines()
                    if line.strip() and not line.strip().startswith(('.', '#'))]
    if instructions != ['const/4 v0, 0x1', 'return v0'] or '.locals 1' not in body:
        raise ValueError('Unexpected netstats flag implementation')
    changed = body.replace('const/4 v0, 0x1',
        '# S23: host tethering exposes legacy queries, without per-query flags.\n    const/4 v0, 0x0', 1)
    return text[:match.start(1)] + changed + text[match.end(1):]

if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/com/android/internal/hidden_from_bootclasspath/android/net/platform/flags/Flags.smali'))
    if len(paths) != 1:
        sys.exit('Expected one guest networking flags class')
    path = paths[0]
    result = patch(path.read_text(encoding='utf-8'))
    with open(path, 'w', encoding='utf-8', newline='\n') as output:
        output.write(result)
    print(path.relative_to(root).parts[0])
