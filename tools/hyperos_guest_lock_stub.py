"""Use HyperOS's base lock extension instead of its hardware/old-credential plugin.

Guest credentials are already disabled by the core guards. This additionally
prevents LockSettingsStub's static initializer loading Xiaomi's implementation,
whose SecSyntheticPasswordManager reflects into shared key/Weaver services.
Only the inspected guest class is accepted. No host files are ever patched.
"""
from pathlib import Path
import hashlib,re,sys

# SHA-256 of decoded text with LF line endings (apktool uses CRLF on Windows).
CLASS_SHA='d39059666015bc9f0f3ab89628f59726ab8b080046215c7015d8fd15b8bc53b1'
CLS='Lcom/android/server/locksettings/LockSettingsStub;'

def patch(text):
    if hashlib.sha256(text.encode('utf-8')).hexdigest()!=CLASS_SHA:raise ValueError('Guest lock extension differs from inspected HyperOS build')
    init=re.compile(r'^\.method static constructor <clinit>\(\)V\n.*?^\.end method',re.M|re.S)
    destroy=re.compile(r'^\.method public destroyLskfBasedProtector\([^\n]*\)V\n.*?^\.end method',re.M|re.S)
    if len(init.findall(text))!=1 or len(destroy.findall(text))!=1:raise ValueError('Guest lock extension methods differ')
    text=init.sub('''.method static constructor <clinit>()V
    .locals 1
    # gsionlocked: never load the Xiaomi hardware credential plugin in the guest.
    new-instance v0, '''+CLS+'''
    invoke-direct {v0}, '''+CLS+'''-><init>()V
    sput-object v0, '''+CLS+'''->sInstance:'''+CLS+'''
    return-void
.end method''',text)
    def refuse(match):return match.group().split('\n',1)[0]+'''
    .locals 0
    # gsionlocked: credential protectors are not managed by this guest extension.
    return-void
.end method'''
    return destroy.sub(refuse,text)

def main():
    root=Path(sys.argv[1]);paths=list(root.glob('smali*/com/android/server/locksettings/LockSettingsStub.smali'))
    if len(paths)!=1:raise ValueError('Expected one guest LockSettingsStub')
    path=paths[0];changed=patch(path.read_text(encoding='utf-8'))
    with path.open('w',encoding='utf-8',newline='\n') as out:out.write(changed)
    print(path.relative_to(root).parts[0])

if __name__=='__main__':main()
