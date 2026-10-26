"""Replace permitted guarded proxy transaction constants with per-phone dispatch.

Input is an ALREADY GUARDED services.jar smali tree, never unguarded framework.
The adapter is built separately from runtime/samsung-common/src. No phone access.
"""
from pathlib import Path
import argparse, re

ALLOWED = {
    'createUserData', 'setFirstBoot', 'createAppData', 'createAppDataBatched',
    'restoreconAppData', 'fixupAppData', 'getAppSize', 'getUserSize', 'getExternalSize',
    'getAppCrates', 'getUserCrates', 'controlDexOptBlocking', 'mergeProfiles', 'dumpProfiles',
    'copySystemProfile', 'createProfileSnapshot', 'linkNativeLibraryDirectory', 'createOatDirs',
    'linkFile', 'hashSecondaryDexFile', 'invalidateMounts', 'isQuotaSupported', 'prepareAppProfile',
    'tryMountDataMirror', 'getOdexVisibility', 'createFsveritySetupAuthToken', 'enableFsverity',
}
ADAPTER = 'Lcom/android/server/pm/GsionlockedInstalldMap;->code(Ljava/lang/String;)I'

def patch(text):
    original_guards = re.findall(r'OneOS: installd\.[A-Za-z0-9_$]+ blocked in the container', text)
    if '.class public final Lcom/android/server/pm/OneOSInstalldProxy;' not in text:
        raise ValueError('Expected the existing guarded OneOSInstalldProxy')
    if ADAPTER in text:
        raise ValueError('Proxy already uses dynamic dispatch')
    methods = re.findall(r'^\.method[^\n]*\n.*?^\.end method', text, re.M | re.S)
    if not methods:
        raise ValueError('Proxy methods absent')
    changed = []
    for method in methods:
        header = method.splitlines()[0]
        match = re.search(r' ([A-Za-z0-9_$]+)(\([^\n]+)$', header)
        if not match:
            continue
        name, signature = match.groups()
        if name not in ALLOWED:
            continue
        # Existing denied methods remain denied, including any permitted name
        # whose signature did not fit the original source generation.
        marker = 'not sent to the host installd'
        if marker in method or 'OneOS: installd.'+name+' blocked in the container' in method:
            continue
        calls = list(re.finditer(r'^\s*invoke-interface(?:/range)? \{([^}]+)\}, Landroid/os/IBinder;->transact\(ILandroid/os/Parcel;Landroid/os/Parcel;I\)Z\s*$', method, re.M))
        if len(calls) != 1 or '/range' in calls[0].group(0):
            raise ValueError('Unexpected Binder invocation in ' + name)
        registers = [r.strip() for r in calls[0].group(1).split(',')]
        if len(registers) != 5:
            raise ValueError('Invalid Binder registers in ' + name)
        register = registers[1]
        if not re.fullmatch(r'[vp]\d+', register):
            raise ValueError('Invalid transaction register')
        before = method[:calls[0].start()]
        constants = list(re.finditer(r'^\s*const(?:/4|/16)? '+register+r', (?:-?0x[0-9a-f]+|[0-9]+)\s*$', before, re.M))
        if not constants:
            raise ValueError('Missing transaction constant in ' + name)
        constant = constants[-1]
        # Between the transaction load and transact, no reassignment is expected.
        tail = before[constant.end():]
        if re.search(r'^\s*(?:move\S*|const\S*|[ias]get\S*) '+register+r'[,\s]', tail, re.M):
            raise ValueError('Transaction register is overwritten in ' + name)
        replacement = ('\n    const-string/jumbo '+register+', "'+name+'|'+signature+'"\n\n'
                       '    invoke-static/range {'+register+' .. '+register+'}, '+ADAPTER+'\n\n'
                       '    move-result '+register+'\n')
        result = method[:constant.start()] + replacement + method[constant.end():]
        text = text.replace(method, result, 1)
        changed.append(name)
    if not {'createAppData', 'createAppDataBatched', 'restoreconAppData', 'invalidateMounts'}.issubset(changed):
        raise ValueError('Required guarded proxy methods were not adapted')
    if 'OneOS: installd.destroyUserData blocked in the container' not in text:
        raise ValueError('Destructive-operation guard absent')
    if sorted(original_guards) != sorted(re.findall(r'OneOS: installd\.[A-Za-z0-9_$]+ blocked in the container', text)):
        raise ValueError('Existing destructive-operation guards changed')
    return text, changed

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('smali_root', type=Path)
    args=parser.parse_args()
    paths=list(args.smali_root.glob('smali*/com/android/server/pm/OneOSInstalldProxy.smali'))
    if len(paths)!=1:
        parser.error('Expected exactly one existing guarded proxy')
    path=paths[0]
    result, changed=patch(path.read_text(encoding='utf-8'))
    with path.open('w', encoding='utf-8', newline='\n') as out:
        out.write(result)
    print('Dynamic host installd dispatch:', ', '.join(changed))
    print(path.relative_to(args.smali_root).parts[0])

if __name__=='__main__':
    main()
