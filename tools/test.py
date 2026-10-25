from pathlib import Path
import os, subprocess, shutil, tempfile

ROOT=Path(__file__).resolve().parents[1]
subprocess.run([__import__('sys').executable,str(ROOT/'tools/check_coloros.py')],check=True)
exe='.exe' if os.name=='nt' else ''
java=Path(os.environ['JAVA_HOME'])/'bin' if os.getenv('JAVA_HOME') else None
javac=str(java/('javac'+exe)) if java else shutil.which('javac')
runtime=str(java/('java'+exe)) if java else shutil.which('java')
with tempfile.TemporaryDirectory(prefix='gsionlocked-test-') as output:
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'tests/stubs/android/os/Build.java'),str(ROOT/'app/src/main/java/dev/tempgsi/controller/DeviceProfile.java'),str(ROOT/'tests/DeviceProfileTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.DeviceProfileTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'app/src/main/java/dev/tempgsi/controller/GsiFormat.java'),str(ROOT/'tests/GsiFormatTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'GsiFormatTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'app/src/main/java/dev/tempgsi/controller/RuntimeProfile.java'),str(ROOT/'tests/RuntimeProfileTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'RuntimeProfileTest'],check=True)
