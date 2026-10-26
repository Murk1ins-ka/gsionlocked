"""Syntax and host-namespace refusal check. All identity/mutation commands are fakes."""
from pathlib import Path
import os, shutil, subprocess, tempfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    if os.name=='nt':
        git=shutil.which('git')
        git_root=Path(git).resolve().parent.parent if git else None
        options=[git_root/'bin/bash.exe',git_root/'usr/bin/sh.exe'] if git_root else []
        bash=next((candidate for candidate in options if candidate.is_file()),None)
    else:bash=Path(shutil.which('sh') or '/bin/sh')
    if bash is None or not bash.is_file():raise SystemExit('Host binding checks require a POSIX shell (Git sh on Windows)')
    def path(value):
        if os.name=='nt':
            absolute=Path(value).resolve().as_posix()
            return '/'+absolute[0].lower()+absolute[2:]
        return str(value)
    script=ROOT/'runtime/samsung-common/bind-host-abi.sh'
    subprocess.run([str(bash),'-n',path(script)],check=True)
    with tempfile.TemporaryDirectory(prefix='guest-bind-refusal-') as td:
        folder=Path(td)
        mocks={'id':'echo 0\n','readlink':"echo 'mnt:[same-host-namespace]'\n"}
        for command in ['mount','umount','mkdir','rm','chown','chmod']:
            mocks[command]='echo MUTATION_WAS_REACHED >&2\nexit 99\n'
        for name,body in mocks.items():
            f=folder/name;f.write_text('#!/bin/sh\n'+body,encoding='utf-8');f.chmod(0o755)
        result=subprocess.run([str(bash),'-c','mock="$1"; target="$2"; PATH="$mock:/usr/bin:$PATH"; export PATH; set -- /data/local/tmp/oneos/rootfs; . "$target"','_',path(folder),path(script)],capture_output=True,text=True)
        if result.returncode!=3 or 'refusing a bind in the host mount namespace' not in result.stdout or 'MUTATION_WAS_REACHED' in result.stderr:
            raise AssertionError('Host-namespace refusal failed: '+result.stdout+result.stderr)
    print('PASS: binding shell syntax and host-namespace refusal before any mutation; fake root/mount commands only')

if __name__=='__main__':main()
