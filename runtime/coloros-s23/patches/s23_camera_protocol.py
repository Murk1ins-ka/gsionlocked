"""Adapt outgoing camera commands to the inspected Samsung camera service."""
from pathlib import Path
import re
import sys

METHOD = re.compile(r'^\.method public (?:blacklist |greylist(?:-max-[a-z])? )?(\w+\([^\n]+)\n.*?^\.end method', re.M | re.S)
SUPPORTED = {
    'android/hardware/ICameraService': {
        'createDefaultRequest', 'getSessionCharacteristics',
        'isSessionConfigurationWithParametersSupported', 'notifyDeviceStateChange',
        'reportExtensionSessionStats'},
    'android/hardware/camera2/ICameraDeviceUser': {'createInputStream', 'createStream'},
}
UNSUPPORTED = {
    'android/hardware/ICameraService': {'notifyDisplayRotationChange', 'notifySmartWindowChange', 'warmUp'},
    'android/hardware/camera2/ICameraDeviceUser': {'configureStreams', 'updateOutputConfigurations'},
}

def patch_proxy(guest, host, interface):
    host_methods = {m[1]: m[0] for m in METHOD.finditer(host)}
    replaced, blocked = set(), set()
    def replace(match):
        signature = match[1]
        name = signature.split('(', 1)[0]
        if name in SUPPORTED[interface]:
            if signature not in host_methods or '->transact(' not in host_methods[signature]:
                raise ValueError('Missing matching host camera method: ' + signature)
            replaced.add(name)
            return host_methods[signature]
        if name in UNSUPPORTED[interface]:
            blocked.add(name)
            return ('.method public blacklist ' + signature + '\n    .locals 2\n'
                '    new-instance v0, Landroid/os/RemoteException;\n'
                '    const-string v1, "OneOS: camera command unavailable on Samsung host: ' + name + '"\n'
                '    invoke-direct {v0, v1}, Landroid/os/RemoteException;-><init>(Ljava/lang/String;)V\n'
                '    throw v0\n.end method')
        return match[0]
    result = METHOD.sub(replace, guest)
    if replaced != SUPPORTED[interface] or blocked != UNSUPPORTED[interface]:
        raise ValueError('Unexpected camera command set')
    return result

def patch_flags(text):
    names = {'configureStreamsBatch', 'seamlessTransitions'}
    seen = set()
    pattern = re.compile(r'^\.method public static (?:blacklist )?(\w+)\(\)Z\n.*?^\.end method', re.M | re.S)
    def replace(match):
        name = match[1]
        if name not in names:
            return match[0]
        seen.add(name)
        return ('.method public static blacklist ' + name + '()Z\n    .locals 1\n'
            '    # Samsung 16 uses the existing configure/createStream path.\n'
            '    const/4 v0, 0x0\n    return v0\n.end method')
    result = pattern.sub(replace, text)
    if seen != names:
        raise ValueError('Expected camera transport flags missing')
    return result

def one(root, relative):
    paths = list(root.glob('smali*/' + relative))
    if len(paths) != 1:
        raise ValueError('Missing or ambiguous ' + relative)
    return paths[0]

if __name__ == '__main__':
    guest, host = map(Path, sys.argv[1:3])
    changes = []
    for interface in SUPPORTED:
        relative = interface + '$Stub$Proxy.smali'
        target = one(guest, relative)
        changes.append((target, patch_proxy(target.read_text(encoding='utf-8'), one(host, relative).read_text(encoding='utf-8'), interface)))
    flags = one(guest, 'com/android/internal/hidden_from_bootclasspath/com/android/internal/camera/flags/Flags.smali')
    changes.append((flags, patch_flags(flags.read_text(encoding='utf-8'))))
    for target, content in changes:
        with open(target, 'w', encoding='utf-8', newline='\n') as stream:
            stream.write(content)
        print(target.relative_to(guest))
