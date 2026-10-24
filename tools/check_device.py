"""Read-only compatibility/runtime audit. Never installs or starts a guest."""
from pathlib import Path
import argparse, json, subprocess, shlex

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--serial',help='Optional ADB serial; never stored in reports')
    args=parser.parse_args();base=[args.adb]+(['-s',args.serial] if args.serial else [])
    def shell(command,root=False):
        if root: command='su -c '+shlex.quote(command)
        return subprocess.run(base+['shell',command],capture_output=True,text=True,timeout=15)
    profile=json.loads((ROOT/'profiles/dm1q-fzg1.json').read_text())
    facts={key:shell('getprop '+prop).stdout.strip() for key,prop in [('model','ro.product.model'),('firmware','ro.build.PDA'),('sdk','ro.build.version.sdk')]}
    compatible=facts['model']==profile['model'] and facts['firmware']==profile['firmware'] and facts['sdk']==str(profile['host_sdk'])
    print(json.dumps({'device':facts,'tested_combination':compatible},indent=2))
    if not compatible:
        print('No tested profile matches. No changes were made.');return 2
    result=shell('test -f /data/local/tmp/gsionlocked-profile.properties',root=True)
    if result.returncode:
        print('Prepared runtime is absent, or root access was not granted. See docs/SETUP.md.');return 3
    print('Prepared profile exists. This audit does not validate every runtime dependency or install a guest.')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
