"""Create signed-package input overrides connecting local capture to guest dispatch.

Offline only. Does not deploy, launch, replace an existing stage or publish a runtime.
"""
from pathlib import Path
import argparse, hashlib, json, re

ROOT=Path(__file__).resolve().parents[1]

def sha(data):return hashlib.sha256(data).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage',type=Path,required=True)
    p.add_argument('--dispatch-build',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    fix=(args.stage/'fix.sha256').read_bytes()
    if sha(fix)!='9bfcb5a5d87d01d0afc81c200ee71a8a4bca7dd412253716a759531e80369284':p.error('Expected the pinned guarded base stage')
    base={name:digest for digest,name in (line.split() for line in fix.decode().splitlines())}
    result=json.loads((args.dispatch_build/'result.json').read_text(encoding='utf-8'))
    services=(args.dispatch_build/result['artifact']).read_bytes()
    if sha(services)!=result['sha256'] or result['base_sha256']!=base['services_patched.jar']:p.error('Candidate dispatch artifact differs')
    required={'createAppData','createAppDataBatched','restoreconAppData','invalidateMounts'}
    if not required.issubset(result['changed_methods']):p.error('Candidate does not include required dispatch hooks')
    overrides={'services_patched.jar':services,'bind-host-abi.sh':(ROOT/'runtime/samsung-common/bind-host-abi.sh').read_bytes()}
    for name in ['guest_start.sh','start-coloros-logged.sh','guards.txt']:
        data=(args.stage/name).read_bytes()
        if sha(data)!=base[name]:p.error('Base stage file changed: '+name)
        overrides[name]=data
    script=overrides['guest_start.sh'].decode('utf-8')
    anchor='$BB mount --make-rprivate /\n'
    if script.count(anchor)!=1:p.error('Unexpected private-namespace boundary')
    script=script.replace(anchor,anchor+'''# The host capsule is bound only after mount propagation is private and before pivot.
export GSIONLOCKED_BIND_BUSYBOX="$BB"
/system/bin/sh /data/local/tmp/oneos/bind-host-abi.sh /data/local/tmp/oneos/rootfs || exit 3
''',1)
    composer='setprop ctl.restart vendor.qti.hardware.display.composer\n'
    if script.count(composer)!=1:p.error('Unexpected composer restart boundary')
    script=script.replace(composer,'''# The service was discovered from this host's init declarations before takeover.
case "${GSIONLOCKED_HOST_COMPOSER_SERVICE:-}" in
    ''|*[!A-Za-z0-9_.-]*) echo 'Invalid captured display composer service'; exit 3 ;;
esac
setprop ctl.restart "$GSIONLOCKED_HOST_COMPOSER_SERVICE" || exit 3
''',1)
    ready='''        service check android.hardware.graphics.composer3.IComposer/default 2>/dev/null | grep -q ': found' && return 0
        [ "${ONEOS_PROFILE:-}" = s23-8550-ossi37 ] || return 1
        $BB timeout 2 /system/bin/lshal -i 2>/dev/null |
            $BB grep -qF 'android.hardware.graphics.composer@2.4::IComposer/default'
'''
    if script.count(ready)!=1:p.error('Unexpected composer readiness boundary')
    script=script.replace(ready,'''        case "${GSIONLOCKED_HOST_COMPOSER_INTERFACE:-}" in
            android.hardware.graphics.composer3.IComposer/default)
                service check "$GSIONLOCKED_HOST_COMPOSER_INTERFACE" 2>/dev/null | grep -q ': found' ;;
            android.hardware.graphics.composer@2.[1-4]::IComposer/default)
                $BB timeout 2 /system/bin/lshal -i 2>/dev/null |
                    $BB grep -qF "$GSIONLOCKED_HOST_COMPOSER_INTERFACE" ;;
            *) return 1 ;;
        esac
''',1)
    script=script.replace('Display composer registered (AIDL or S23 HIDL 2.4)','Captured host display composer registered')
    overrides['guest_start.sh']=script.encode('utf-8')
    wrapper=overrides['start-coloros-logged.sh'].decode('utf-8')
    anchor='BOOT=$(cat /proc/sys/kernel/random/boot_id)\n'
    if wrapper.count(anchor)!=1:p.error('Unexpected host launcher boundary')
    wrapper=wrapper.replace(anchor,'''# Prepare this Samsung's own system inputs while One UI is still running.
HOST_CAPTURE=$(CLASSPATH=/data/local/tmp/tempgsi-control/gsionlocked.apk /system/bin/app_process /system/bin dev.tempgsi.controller.HostPrepareMain) || exit 3
GSIONLOCKED_HOST_RUNTIME=$(printf '%s\\n' "$HOST_CAPTURE" | sed -n 's/^HOST_PREPARED=//p')
GSIONLOCKED_HOST_MAP_SHA256=$(printf '%s\\n' "$HOST_CAPTURE" | sed -n 's/^HOST_MAP_SHA256=//p')
GSIONLOCKED_HOST_COMPOSER_SERVICE=$(printf '%s\\n' "$HOST_CAPTURE" | sed -n 's/^HOST_COMPOSER_SERVICE=//p')
GSIONLOCKED_HOST_COMPOSER_INTERFACE=$(printf '%s\\n' "$HOST_CAPTURE" | sed -n 's/^HOST_COMPOSER_INTERFACE=//p')
[ -n "$GSIONLOCKED_HOST_RUNTIME" ] && [ -n "$GSIONLOCKED_HOST_MAP_SHA256" ] || exit 3
case "$GSIONLOCKED_HOST_COMPOSER_SERVICE" in
    ''|*[!A-Za-z0-9_.-]*) echo 'Display composer not resolved; host left running'; exit 3 ;;
esac
case "$GSIONLOCKED_HOST_COMPOSER_INTERFACE" in
    android.hardware.graphics.composer3.IComposer/default|android.hardware.graphics.composer@2.[1-4]::IComposer/default) ;;
    *) echo 'Display composer interface not resolved; host left running'; exit 3 ;;
esac
export GSIONLOCKED_HOST_RUNTIME GSIONLOCKED_HOST_MAP_SHA256 GSIONLOCKED_HOST_COMPOSER_SERVICE GSIONLOCKED_HOST_COMPOSER_INTERFACE
'''+anchor,1)
    overrides['start-coloros-logged.sh']=wrapper.encode('utf-8')
    guards=overrides['guards.txt'].decode('utf-8')
    old=base['services_patched.jar']
    pattern=r'^'+old+r'(\s+services_patched\.jar)$'
    guards,count=re.subn(pattern,sha(services)+r'\1',guards,flags=re.M)
    if count!=1:p.error('Expected exactly one services guard')
    overrides['guards.txt']=guards.encode('utf-8')
    new=dict(base);new.update({name:sha(data) for name,data in overrides.items()})
    overrides['fix.sha256']=''.join(digest+'  '+name+'\n' for name,digest in sorted(new.items())).encode('ascii')
    if args.output.exists():p.error('Existing output is preserved')
    args.output.mkdir(parents=True)
    for name,data in overrides.items():(args.output/name).write_bytes(data)
    (args.output/'integration.json').write_text(json.dumps({
        'status':'candidate-only-not-deployed','base_manifest_sha256':sha(fix),
        'manifest_sha256':sha(overrides['fix.sha256']),
        'overrides':{name:sha(data) for name,data in overrides.items()},
        'host_binding':'private guest namespace, read-only single mapping file',
        'remaining':'common platform/backend integration, signed runtime package, on-device validation'
    },indent=2)+'\n',encoding='utf-8')
    print('CONNECTED_CANDIDATE_OVERRIDES='+str(len(overrides)))
    print('Local host capture -> protected guest bind -> dynamic installd dispatch; not installed')

if __name__=='__main__':main()
