"""Disable the pinned HyperOS guest's XSpace profile repair before its single-user guard.

The inspected method copies mUsers entries into a temporary SparseArray, calls
XSpace repair, then writes modified guest user metadata. It triggers upstream's
conservative writer scan. This guest has no secondary profiles; omit the whole
repair path rather than weakening the writer/call-graph validation.
"""
from pathlib import Path
import hashlib,re,sys

METHOD_SHA='d2c9705f72898da47837f4437f19a85e8a39e71da19025cecc75c6f134684833'
PATTERN=re.compile(r'^\.method private repairProfileGroupIdIfNecessaryLP\(\)V\n.*?^\.end method',re.M|re.S)

def patch(text):
    matches=list(PATTERN.finditer(text))
    if len(matches)!=1:raise ValueError('Expected one inspected XSpace repair method')
    method=matches[0]
    if hashlib.sha256(method.group().encode('utf-8')).hexdigest()!=METHOD_SHA:raise ValueError('XSpace method differs from inspected HyperOS build')
    replacement='''.method private repairProfileGroupIdIfNecessaryLP()V
    .locals 0
    # gsionlocked: single-user guest has no XSpace profiles to repair.
    return-void
.end method'''
    return text[:method.start()]+replacement+text[method.end():]

def main():
    root=Path(sys.argv[1]);paths=list(root.glob('smali*/com/android/server/pm/UserManagerService.smali'))
    if len(paths)!=1:raise ValueError('Expected one UserManagerService')
    path=paths[0];changed=patch(path.read_text(encoding='utf-8'))
    with path.open('w',encoding='utf-8',newline='\n') as f:f.write(changed)
    print(path.relative_to(root).parts[0])

if __name__=='__main__':main()
