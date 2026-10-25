"""Keep an unavailable OPPO vendor property from killing the guest framework.

Samsung retains ownership of its CPU/thermal controls. A denied optional
OPPO property write must propagate neither success nor an uncaught exception.
Only updateCpuSetEnableStatus's two writes to its cpulimit property are wrapped.
"""
from pathlib import Path
import re
import sys

CALL = 'Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V'
OWNER = 'Lcom/android/server/am/OplusCpuLimitManager;'
HELPER = 'oneosSetCpuLimitProperty'


def patch(text):
    if f'.method private static {HELPER}' in text:
        return text
    methods = list(re.finditer(r'^\.method[^\n]*\n.*?^\.end method', text, re.M | re.S))
    edits = []
    for match in methods:
        body = match.group()
        if not body.startswith('.method private updateCpuSetEnableStatus()V\n'):
            continue
        count = body.count(CALL)
        if count != 2 or body.count('"persist.vendor.enable.cpulimit"') != 1:
            raise ValueError('Unexpected CPU limit property method')
        # These methods set only this OPPO property; don't wrap unrelated writes.
        edits.append((match.start(), match.end(), body.replace(CALL,
            OWNER + '->' + HELPER + '(Ljava/lang/String;Ljava/lang/String;)V')))
    if not edits:
        raise ValueError('No pinned CPU limit property writes found')
    for start, end, body in reversed(edits):
        text = text[:start] + body + text[end:]
    return text + f'''
.method private static {HELPER}(Ljava/lang/String;Ljava/lang/String;)V
    .locals 2
    :oneos_try_start
    invoke-static {{p0, p1}}, {CALL}
    :oneos_try_end
    .catch Ljava/lang/RuntimeException; {{:oneos_try_start .. :oneos_try_end}} :oneos_denied
    return-void
    :oneos_denied
    move-exception v0
    const-string v1, "OneOSCpuLimit"
    const-string p0, "OPPO CPU limit property unavailable on host; keeping host CPU controls"
    invoke-static {{v1, p0, v0}}, Landroid/util/Slog;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method
'''


if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/com/android/server/am/OplusCpuLimitManager.smali'))
    if len(paths) != 1:
        sys.exit('Expected exactly one OplusCpuLimitManager')
    path = paths[0]
    with open(path, encoding='utf-8') as stream:
        result = patch(stream.read())
    with open(path, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
    print(path.relative_to(root).parts[0])
