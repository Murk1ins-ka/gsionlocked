"""Use the existing legacy network-stats query path with host SDK 36 tethering."""
from pathlib import Path
import re
import sys


def patch(text):
    pattern = re.compile(r'(?m)^(    )invoke-static \{\}, Lcom/android/internal/hidden_from_bootclasspath/android/net/platform/flags/Flags;->useNetstatsPerQueryFlags\(\)Z\n\s*\n    move-result (v\d+)')
    matches = list(pattern.finditer(text))
    if len(matches) != 2 or '->querySummary(Landroid/net/NetworkTemplate;JJI)' not in text or \
            '->querySummary(Landroid/net/NetworkTemplate;JJ)' not in text or \
            '->queryTaggedSummary(Landroid/net/NetworkTemplate;JJI)' not in text or \
            '->queryTaggedSummary(Landroid/net/NetworkTemplate;JJ)' not in text:
        raise ValueError("Unexpected StatsPullAtomService; refusing compatibility patch")
    return pattern.sub(lambda m: m[1] + '# S23: host SDK 36 tethering has the existing legacy query API\n' +
                       m[1] + 'const/4 ' + m[2] + ', 0x0', text)


if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/com/android/server/stats/pull/StatsPullAtomService.smali'))
    if len(paths) != 1:
        sys.exit('Expected exactly one StatsPullAtomService')
    source = paths[0]
    result = patch(source.read_text(encoding='utf-8'))
    with open(source, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
    print(source.relative_to(root).parts[0])
