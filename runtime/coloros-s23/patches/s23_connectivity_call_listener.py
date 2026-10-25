"""Carry the guest InCallService class while retaining the host network module.

Only the new OTT premium-network slicing integration is marked unsupported:
the host module does not provide this guest service. Normal Telecom lifecycle
and all other guest/host networking remain intact.
"""
from pathlib import Path
import hashlib
import sys

SOURCE_SHA = 'd614136b4a6d98aae4b638e87762f3cb8c0ec94ea28b2124f7b8eb47489ab517'
OLD = '''    invoke-virtual {v1, v2, v3}, Landroid/net/ConnectivityManager;->isFeatureEnabled(J)Z

    move-result v1'''
NEW = '''    # OneOS S23: keep host16 networking; OTT slicing integration is unavailable.
    const/4 v1, 0x0'''

def patch(text):
    if text.count(OLD) != 1:
        raise ValueError('Unexpected connectivity listener feature query')
    if '.super Landroid/telecom/InCallService;' not in text:
        raise ValueError('Expected real InCallService implementation')
    for name in ('onCallAdded', 'onCallRemoved'):
        body = text.split('.method public whitelist ' + name + '(Landroid/telecom/Call;)V\n', 1)[1].split('.end method', 1)[0]
        if '->mSupportOttNetworkSlicing:Z' not in body or 'return-void' not in body:
            raise ValueError('Missing unsupported-feature short circuit: ' + name)
    return text.replace(OLD, NEW, 1)

if __name__ == '__main__':
    root, source = map(Path, sys.argv[1:3])
    if hashlib.sha256(source.read_bytes()).hexdigest() != SOURCE_SHA:
        raise ValueError('Connectivity listener source differs from inspected X9U class')
    relative = 'android/net/ConnectivityCallListenerService.smali'
    if list(root.glob('smali*/' + relative)):
        raise ValueError('Connectivity listener class already exists')
    result = patch(source.read_text(encoding='utf-8'))
    target = root / 'smali_classes2' / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    with open(target, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
    print(target.relative_to(root))
