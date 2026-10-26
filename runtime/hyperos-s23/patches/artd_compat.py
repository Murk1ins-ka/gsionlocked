"""Use inspected Samsung ART wire methods; reject unsupported commands."""
from pathlib import Path
import re
import sys

METHOD = re.compile(r'^\.method public (\w+)(\([^\n]+)\n.*?^\.end method', re.M | re.S)
# Do not expose whole-device cleanup, pre-reboot changes, or incompatible
# compilation structs to the host daemon. Profile operations address guest
# package profiles in /data/misc/profiles, bridged by the existing switcher.
ALLOWED = {'deleteProfile', 'commitTmpProfile', 'copyAndRewriteEmbeddedProfile',
           'copyAndRewriteProfile', 'createCancellationSignal', 'getArtifactsSize',
           'getDexFileVisibility', 'getDexoptStatus', 'getProfileSize',
           'getProfileVisibility', 'getRuntimeArtifactsSize', 'getSdmFileSize',
           'getVdexFileSize', 'isInDalvikCache', 'isProfileUsable',
           'maybeCreateSdc', 'mergeProfiles', 'validateClassLoaderContext',
           'validateDexPath', 'initProfileSaveNotification'}


def patch(guest, host):
    methods = {(m[1], m[2]): m[0] for m in METHOD.finditer(host)}
    report = {'host_wire_methods': [], 'blocked': []}

    def replace(m):
        name, signature = m[1], m[2]
        if '->transact(' not in m[0]:
            return m[0]
        if name in ALLOWED and (name, signature) in methods:
            report['host_wire_methods'].append(name)
            return methods[name, signature]
        report['blocked'].append(name)
        return (f'.method public {name}{signature}\n    .locals 2\n'
                '    new-instance v0, Landroid/os/RemoteException;\n'
                f'    const-string v1, "OneOS: incompatible host ART command: {name}"\n'
                '    invoke-direct {v0, v1}, Landroid/os/RemoteException;-><init>(Ljava/lang/String;)V\n'
                '    throw v0\n.end method')

    result = METHOD.sub(replace, guest)
    if 'deleteProfile' not in report['host_wire_methods']:
        raise ValueError('Expected compatible deleteProfile method missing')
    return result, report


if __name__ == '__main__':
    guest, host = map(Path, sys.argv[1:3])
    suffix = 'smali/com/android/server/art/IArtd$Stub$Proxy.smali'
    target = guest / suffix
    result, report = patch(target.read_text(), (host / suffix).read_text())
    with open(target, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
    print(report)
