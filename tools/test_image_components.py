"""Exercise raw ext4 and nested APEX extraction without mounting or a phone."""
from pathlib import Path
import hashlib, io, os, shutil, struct, subprocess, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[1]
PACKAGE='dev.tempgsi.controller.'

def archive(entries):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in entries:z.writestr(name,data)
    return out.getvalue()

def filesystem(apex):
    # Small ext4 fixture: one group, extent-backed directories and one APEX.
    b=bytearray(128*4096)
    def u16(at,v):struct.pack_into('<H',b,at,v)
    def u32(at,v):struct.pack_into('<I',b,at,v)
    for off,v in [(0,32),(4,128),(20,0),(24,2),(32,128),(40,32),(76,1),(96,0x42)]:u32(1024+off,v)
    u16(1080,0xef53);u16(1112,256);u32(4096+8,2)
    def inode(n,mode,size,physical,count):
        at=8192+(n-1)*256;u16(at,mode);u32(at+4,size);u32(at+32,0x80000)
        u16(at+40,0xf30a);u16(at+42,1);u16(at+44,4)
        u32(at+52,0);u16(at+56,count);u32(at+60,physical)
    def directory(n,block,entries):
        inode(n,0x41ed,4096,block,1);at=block*4096
        for i,(name,ino,kind) in enumerate(entries):
            raw=name.encode();length=((8+len(raw)+3)//4)*4 if i<len(entries)-1 else (block+1)*4096-at
            u32(at,ino);u16(at+4,length);b[at+6]=len(raw);b[at+7]=kind;b[at+8:at+8+len(raw)]=raw;at+=length
    directory(2,4,[('system',3,2)]);directory(3,5,[('apex',4,2)])
    directory(4,6,[('test.apex',5,1)])
    inode(5,0x81a4,len(apex),7,(len(apex)+4095)//4096)
    b[7*4096:7*4096+len(apex)]=apex
    return b

def main():
    suffix='.exe' if os.name=='nt' else ''
    java=Path(os.environ['JAVA_HOME'])/'bin' if os.getenv('JAVA_HOME') else None
    javac=str(java/('javac'+suffix)) if java else shutil.which('javac')
    runtime=str(java/('java'+suffix)) if java else shutil.which('java')
    with tempfile.TemporaryDirectory(prefix='gsi-components-') as name:
        tmp=Path(name);classes=tmp/'classes';classes.mkdir()
        sources=ROOT/'app/src/main/java/dev/tempgsi/controller'
        subprocess.run([javac,'-encoding','UTF-8','-d',str(classes),*[str(sources/(n+'.java')) for n in ('GsiFormat','ReadOnlyExt4','GuestImageComponents','RuntimeBundle','RuntimeFiles','RuntimeProfile')],str(ROOT/'tests/ImageComponentInstallTest.java'),str(ROOT/'tools/DeriveRuntimePackage.java')],check=True)
        data=bytes(range(256))*32;digest=hashlib.sha256(data).hexdigest()
        props=tmp/'recipe.properties'
        props.write_text(f'format=1\noutputs=test_payload.img\nsource.test_payload.img=/system/apex/test.apex\nsha.test_payload.img={digest}\nbytes.test_payload.img={len(data)}\n')
        def run(label,content,ok=True,recipe=props):
            image=tmp/(label+'.img');image.write_bytes(content);output=tmp/(label+'-out')
            result=subprocess.run([runtime,'-cp',str(classes),PACKAGE+'GuestImageComponents',str(image),str(recipe),str(output)],capture_output=True,text=True)
            assert (result.returncode==0)==ok,(label,result.stdout,result.stderr)
            if ok:assert (output/'test_payload.img').read_bytes()==data
            else:assert not output.exists(),label+' left partial extraction'
            return image,output
        direct=archive([('apex_payload.img',data)])
        run('direct',filesystem(direct))
        run('compressed',filesystem(archive([('original_apex',direct)])))
        run('wrong-hash',filesystem(archive([('apex_payload.img',b'x'*len(data))])),False)
        run('wrong-size',filesystem(archive([('apex_payload.img',data[:-1])])),False)
        run('missing',filesystem(archive([('../../outside',data)])),False)
        run('repeated-payload',filesystem(archive([('apex_payload.img',data),('original_apex',direct)])),False)
        run('too-deep',filesystem(archive([('original_apex',archive([('original_apex',direct)]))])),False)
        bad=filesystem(direct);struct.pack_into('<H',bad,6*4096+4,0)
        run('bad-directory',bad,False)
        bad=filesystem(direct);struct.pack_into('<I',bad,1024+96,0x46)
        run('journal-recovery',bad,False)
        bad=filesystem(direct);struct.pack_into('<I',bad,1024+96,0xc2);struct.pack_into('<H',bad,1024+254,64);struct.pack_into('<I',bad,1024+336,0x80000000)
        run('overflow-geometry',bad,False)
        image,output=run('preserve',filesystem(direct))
        sentinel=output/'keep';sentinel.write_text('existing')
        result=subprocess.run([runtime,'-cp',str(classes),PACKAGE+'GuestImageComponents',str(image),str(props),str(output)],capture_output=True)
        assert result.returncode and sentinel.read_text()=='existing'
        # Format 2 omits the payload from ZIP; installer derives it only after GSI verification.
        for valid in (True,False):
            values={'format':'2','system':'coloros','roots':'oneos','gsi.sha256':hashlib.sha256(image.read_bytes()).hexdigest(),'host.services.sha256':'a'*64,
                    'sha.oneos/test_payload.img':digest if valid else 'b'*64,'image.oneos/test_payload.img':'/system/apex/test.apex','bytes.oneos/test_payload.img':str(len(data))}
            entries=[]
            for i in range(10):
                content=bytes([i]);path='oneos/file'+str(i)
                values['sha.'+path]=hashlib.sha256(content).hexdigest();entries.append(('payload/'+path,content))
            entries.insert(0,('runtime.properties',''.join(k+'='+v+'\n' for k,v in values.items()).encode()))
            package=tmp/('runtime-'+str(valid)+'.zip');package.write_bytes(archive(entries))
            subprocess.run([runtime,'-cp',str(classes),PACKAGE+'ImageComponentInstallTest',str(image),str(package),str(tmp/('installed-'+str(valid))),'accept' if valid else 'reject'],check=True)
            if valid:
                original=dict(values);original['format']='1';del original['image.oneos/test_payload.img'];del original['bytes.oneos/test_payload.img']
                base=tmp/'original-runtime.zip';base.write_bytes(archive([('runtime.properties',''.join(k+'='+v+'\n' for k,v in original.items()).encode()),*entries[1:],('payload/oneos/test_payload.img',data)]))
                converted=tmp/'converted-runtime.zip';base_hash=hashlib.sha256(base.read_bytes()).hexdigest()
                command=[runtime,'-cp',str(classes),'DeriveRuntimePackage',str(base),base_hash,str(props),str(converted)]
                subprocess.run(command,check=True,capture_output=True)
                with zipfile.ZipFile(converted) as z:assert 'payload/oneos/test_payload.img' not in z.namelist()
                subprocess.run([runtime,'-cp',str(classes),PACKAGE+'ImageComponentInstallTest',str(image),str(converted),str(tmp/'installed-converted'),'accept'],check=True)
                saved=converted.read_bytes();assert subprocess.run(command,capture_output=True).returncode and saved==converted.read_bytes()
    print('Image components: direct/CAPEX integrity, malformed ext4, nesting and existing-data checks passed')

if __name__=='__main__':main()
