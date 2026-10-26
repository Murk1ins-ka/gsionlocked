from pathlib import Path
import os, subprocess, shutil, tempfile

ROOT=Path(__file__).resolve().parents[1]
subprocess.run([__import__('sys').executable,str(ROOT/'tools/test_hyperos_tools.py')],check=True)
subprocess.run([__import__('sys').executable,str(ROOT/'tools/test_image_components.py')],check=True)
subprocess.run([__import__('sys').executable,str(ROOT/'tools/check_oneos_port.py')],check=True)
subprocess.run([__import__('sys').executable,str(ROOT/'tools/test_host_abi.py')],check=True)
subprocess.run([__import__('sys').executable,str(ROOT/'tools/test_dynamic_dispatch.py')],check=True)
subprocess.run([__import__('sys').executable,str(ROOT/'tools/test_host_binding.py')],check=True)
subprocess.run([__import__('sys').executable,str(ROOT/'tools/check_coloros.py')],check=True)
exe='.exe' if os.name=='nt' else ''
java=Path(os.environ['JAVA_HOME'])/'bin' if os.getenv('JAVA_HOME') else None
javac=str(java/('javac'+exe)) if java else shutil.which('javac')
runtime=str(java/('java'+exe)) if java else shutil.which('java')
with tempfile.TemporaryDirectory(prefix='gsionlocked-test-') as output:
    subprocess.run([javac,'-encoding','UTF-8','-d',output,*[str(ROOT/('app/src/main/java/dev/tempgsi/controller/'+name+'.java')) for name in ('RuntimeBundle','HostComposer')],str(ROOT/'tests/HostComposerTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.HostComposerTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'app/src/main/java/dev/tempgsi/controller/RuntimeBundle.java'),str(ROOT/'tests/RuntimeBundleTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.RuntimeBundleTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,*[str(ROOT/('app/src/main/java/dev/tempgsi/controller/'+name+'.java')) for name in ('RuntimeBundle','RuntimeProfile','RuntimeFiles','ReadOnlyExt4','GsiFormat','GuestImageComponents')],str(ROOT/'tests/RuntimeFilesTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.RuntimeFilesTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'tests/stubs/android/os/Build.java'),str(ROOT/'app/src/main/java/dev/tempgsi/controller/SamsungProfiles.java'),str(ROOT/'app/src/main/java/dev/tempgsi/controller/DeviceProfile.java'),str(ROOT/'tests/DeviceProfileTest.java'),str(ROOT/'tests/SamsungProfilesTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.DeviceProfileTest'],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.SamsungProfilesTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-cp',output,'-d',output,str(ROOT/'tests/stubs/android/content/Context.java'),str(ROOT/'app/src/main/java/dev/tempgsi/controller/AutomaticSetup.java'),str(ROOT/'tests/AutomaticSetupTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'dev.tempgsi.controller.AutomaticSetupTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'app/src/main/java/dev/tempgsi/controller/GsiFormat.java'),str(ROOT/'tests/GsiFormatTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'GsiFormatTest'],check=True)
    subprocess.run([javac,'-encoding','UTF-8','-d',output,str(ROOT/'app/src/main/java/dev/tempgsi/controller/RuntimeProfile.java'),str(ROOT/'tests/RuntimeProfileTest.java')],check=True)
    subprocess.run([runtime,'-cp',output,'RuntimeProfileTest'],check=True)
