"""Regression for APKTool's DEX041/112-byte header mismatch; needs external APKTool."""
from pathlib import Path
import argparse,hashlib,os,struct,subprocess,tempfile,zlib

ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apktool',type=Path,required=True);p.add_argument('--java-home',type=Path,default=os.getenv('JAVA_HOME'));a=p.parse_args()
    if not a.java_home:p.error('JDK required')
    exe='.exe' if os.name=='nt' else '';java=a.java_home/'bin'/('java'+exe);javac=java.with_name('javac'+exe)
    with tempfile.TemporaryDirectory(prefix='smali-header-check-') as name:
        folder=Path(name);smali=folder/'smali';smali.mkdir();classes=folder/'classes';classes.mkdir()
        (smali/'Smoke.smali').write_text('''.class public Ldev/gsionlocked/Smoke;
.super Ljava/lang/Object;
.method public static add(II)I
    .locals 1
    add-int v0, p0, p1
    return v0
.end method
''')
        subprocess.run([str(javac),'-encoding','UTF-8','-cp',str(a.apktool),'-d',str(classes),str(ROOT/'tools/AssembleSmali.java')],check=True)
        target=folder/'classes.dex'
        command=[str(java),'-cp',str(classes)+os.pathsep+str(a.apktool),'AssembleSmali',str(smali),str(target)]
        subprocess.run(command,check=True);data=target.read_bytes()
        assert data[:8] in [b'dex\n039\0',b'dex\n040\0']
        assert struct.unpack_from('<II',data,32)==(len(data),112)
        assert data[12:32]==hashlib.sha1(data[32:]).digest()
        assert struct.unpack_from('<I',data,8)[0]==zlib.adler32(data[12:])&0xffffffff
        old=data;result=subprocess.run(command,capture_output=True)
        assert result.returncode and target.read_bytes()==old
    print('PASS: assembler emits valid legacy DEX size/header/checksums; existing outputs preserved')

if __name__=='__main__':main()
