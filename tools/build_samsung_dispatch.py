"""Build a candidate ColorOS installd adapter from the pinned guarded base JAR.

Offline build only: never deploys, mounts, changes a phone, or bypasses a launch guard.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, zipfile
from dynamic_installd_dispatch import patch

ROOT=Path(__file__).resolve().parents[1]

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base-services',type=Path,required=True)
    p.add_argument('--apktool',type=Path,required=True,help='APKTool 3.0.3 JAR; external tool')
    p.add_argument('--sdk',type=Path,required=True)
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True,help='New output directory, never overwritten')
    args=p.parse_args()
    expected=dict((name,digest) for digest,name in (line.split() for line in (ROOT/'runtime/coloros-s23/fix.sha256').read_text().splitlines()))['services_patched.jar']
    if sha(args.base_services)!=expected:p.error('Input is not the pinned guarded base services JAR')
    output=args.output.resolve()
    if output.exists():p.error('Existing output is preserved; choose a new directory')
    output.mkdir(parents=True)
    exe='.exe' if os.name=='nt' else ''
    javac=args.java_home.resolve()/'bin'/('javac'+exe);java=args.java_home.resolve()/'bin'/('java'+exe)
    apktool=args.apktool.resolve();sdk=args.sdk.resolve()
    decoded=output/'decoded'
    subprocess.run([str(java),'-jar',str(apktool),'d','-f','-r','-o',str(decoded),str(args.base_services.resolve())],check=True)
    proxies=list(decoded.glob('smali*/com/android/server/pm/OneOSInstalldProxy.smali'))
    if len(proxies)!=1:raise RuntimeError('Expected one guarded proxy in the actual input JAR')
    proxy=proxies[0];updated,methods=patch(proxy.read_text(encoding='utf-8'))
    with proxy.open('w',encoding='utf-8',newline='\n') as f:f.write(updated)
    directory=proxy.relative_to(decoded).parts[0]
    dex_name='classes.dex' if directory=='smali' else 'classes'+directory.removeprefix('smali_classes')+'.dex'
    assembler=output/'assembler';assembler.mkdir()
    subprocess.run([str(javac),'-encoding','UTF-8','-cp',str(apktool),'-d',str(assembler),str(ROOT/'tools/AssembleSmali.java')],check=True)
    rebuilt=output/dex_name
    subprocess.run([str(java),'-cp',str(assembler)+os.pathsep+str(apktool),'AssembleSmali',str(decoded/directory),str(rebuilt)],check=True)
    helpers=output/'helper-classes';helpers.mkdir()
    subprocess.run([str(javac),'-encoding','UTF-8','-source','8','-target','8','-cp',str(sdk/'platforms/android-35/android.jar'),'-d',str(helpers),str(ROOT/'runtime/samsung-common/src/GsionlockedInstalldMap.java')],check=True)
    dex=output/'helper-dex';dex.mkdir()
    subprocess.run([str(java),'-cp',str(sdk/'build-tools/35.0.0/lib/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(sdk/'platforms/android-35/android.jar'),'--output',str(dex),*[str(f) for f in helpers.rglob('*.class')]],check=True)
    raw=output/'services-host-dispatch-unaligned.jar';candidate=output/'services-host-dispatch.jar'
    with zipfile.ZipFile(args.base_services) as base,zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as out:
        if dex_name not in base.namelist():raise RuntimeError('Rebuilt DEX does not match base JAR entry')
        index=2
        while 'classes'+str(index)+'.dex' in base.namelist():index+=1
        helper_name='classes'+str(index)+'.dex'
        for item in base.infolist():
            # This is a framework JAR, not an APK. Rebuild only one DEX; retain all
            # other byte contents and metadata. Existing entry names are unchanged.
            out.writestr(item,rebuilt.read_bytes() if item.filename==dex_name else base.read(item.filename))
        out.write(dex/'classes.dex',helper_name)
    subprocess.run([str(sdk/'build-tools/35.0.0'/('zipalign'+exe)),'-f','4',str(raw),str(candidate)],check=True)
    subprocess.run([str(sdk/'build-tools/35.0.0'/('zipalign'+exe)),'-c','4',str(candidate)],check=True)
    result={'artifact':candidate.name,'sha256':sha(candidate),'base_sha256':expected,
            'changed_methods':methods,'rebuilt_dex':dex_name,'helper_dex':helper_name,
            'status':'candidate-not-installed-or-hardware-validated',
            'requires':'read-only guest binding of the current host-generated installd.properties and updated signed runtime guards'}
    (output/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,indent=2))

if __name__=='__main__':main()
