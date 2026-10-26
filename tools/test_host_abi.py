"""Compile real DEX fixtures and verify the host-derived Binder dispatch. No phone."""
from pathlib import Path
import os, subprocess, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    sdk=Path(os.getenv('ANDROID_SDK_ROOT') or os.getenv('ANDROID_HOME') or ROOT/'build/sdk')
    java=Path(os.environ['JAVA_HOME'])/'bin'
    exe='.exe' if os.name=='nt' else ''
    javac=java/('javac'+exe); jvm=java/('java'+exe)
    d8=sdk/'build-tools/35.0.0/lib/d8.jar'
    if not d8.is_file():
        raise SystemExit('Host ABI tests need Android SDK build-tools 35.0.0 (D8)')
    with tempfile.TemporaryDirectory(prefix='host-abi-fixtures-') as td:
        temp=Path(td); jars=[]
        for number, bad in [(1, ''),(301, ''),(1, 'duplicate'),(1, 'overload')]:
            folder=temp/f'fixture-{len(jars)}'; folder.mkdir()
            source=folder/'IInstalld.java'
            source.write_text('''package android.os;
public interface IInstalld {
  void createUserData(String uuid,int user,int serial,int flags);
  boolean isQuotaSupported(String uuid);
  void setFirstBoot();
  '''+('void createUserData(int user);' if bad=='overload' else '')+'''
  abstract class Stub implements IInstalld {
    static final String DESCRIPTOR="android.os.IInstalld";
    static final int TRANSACTION_createUserData='''+str(number)+''';
    static final int TRANSACTION_isQuotaSupported='''+('1' if bad=='duplicate' else '40')+''';
    static final int TRANSACTION_setFirstBoot=3;
  }
}''', encoding='utf-8')
            classes=folder/'classes'; classes.mkdir()
            subprocess.run([str(javac),'-encoding','UTF-8','-source','8','-target','8','-d',str(classes),str(source)],check=True,capture_output=True)
            dex=folder/'dex'; dex.mkdir()
            subprocess.run([str(jvm),'-cp',str(d8),'com.android.tools.r8.D8','--min-api','31','--output',str(dex),*[str(f) for f in classes.rglob('*.class')]],check=True,capture_output=True)
            jar=folder/'fixture.jar'
            with zipfile.ZipFile(jar,'w') as z:z.write(dex/'classes.dex','classes.dex')
            jars.append(jar)
        out=temp/'checks';out.mkdir()
        files=[ROOT/'app/src/main/java/dev/tempgsi/controller/DexBinderTable.java',ROOT/'tests/DexBinderTableTest.java',
               ROOT/'app/src/main/java/dev/tempgsi/controller/RuntimeBundle.java',ROOT/'app/src/main/java/dev/tempgsi/controller/HostSystemRuntime.java',ROOT/'tests/HostSystemRuntimeTest.java',
               ROOT/'runtime/samsung-common/src/GsionlockedInstalldMap.java',ROOT/'tests/InstalldMapTest.java',ROOT/'tests/stubs/android/os/RemoteException.java']
        subprocess.run([str(javac),'-encoding','UTF-8','-d',str(out),*[str(f) for f in files]],check=True)
        subprocess.run([str(jvm),'-cp',str(out),'dev.tempgsi.controller.DexBinderTableTest',*[str(f) for f in jars]],check=True)
        subprocess.run([str(jvm),'-cp',str(out),'com.android.server.pm.InstalldMapTest'],check=True)
        subprocess.run([str(jvm),'-cp',str(out),'dev.tempgsi.controller.HostSystemRuntimeTest',str(jars[1])],check=True)

if __name__=='__main__':main()
