"""Prepare guest APEX payloads/recipes from InspectGsi's system-only exports. No mounts."""
from pathlib import Path
import argparse,hashlib,io,json,re,zipfile

def properties(path):
    values={}
    for line in path.read_text(encoding='ascii').splitlines():
        if not line or line.startswith(('#','!')):continue
        key,value=line.split('=',1)
        if key in values:raise ValueError('Duplicate property')
        values[key]=value
    return values

def payload(z,depth=0):
    if depth>1 or len(z.infolist())>256:raise ValueError('Unsupported nested APEX')
    names=z.namelist();direct=names.count('apex_payload.img');nested=names.count('original_apex')
    if direct+nested!=1:raise ValueError('APEX must contain exactly one payload or nested original')
    if direct:
        info=z.getinfo('apex_payload.img')
        if not 4096<=info.file_size<=2*1024**3:raise ValueError('APEX payload outside size limits')
        return z.open(info),None
    info=z.getinfo('original_apex')
    if info.file_size>256*1024**2:raise ValueError('Nested APEX too large')
    child=zipfile.ZipFile(io.BytesIO(z.read(info)))
    try:
        stream,unused=payload(child,depth+1)
        return stream,child
    except BaseException:child.close();raise

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--image-sha256',required=True);a=p.parse_args()
    if not re.fullmatch('[a-f0-9]{64}',a.image_sha256):p.error('Expected image SHA-256')
    if a.output.exists():p.error('Existing output is preserved')
    source=properties(a.inputs/'components.properties');a.output.mkdir(parents=True)
    values={'format':'1','gsi.sha256':a.image_sha256};report={}
    for key,path in sorted(source.items()):
        if not key.startswith('source.') or not path.startswith('/system/apex/'):continue
        name=key[7:];file=a.inputs/name
        if not re.fullmatch('[A-Za-z0-9_.+-]+',name):raise ValueError('Invalid exported APEX filename')
        if file.is_symlink() or not file.is_file() or file.parent.resolve()!=a.inputs.resolve():raise ValueError('Invalid exported APEX path')
        if file.stat().st_size>256*1024**2 or file.stat().st_size!=int(source['bytes.'+name]):raise ValueError('Invalid exported APEX size')
        digest=hashlib.sha256(file.read_bytes()).hexdigest()
        if digest!=source['sha.'+name]:raise ValueError('Exported APEX changed')
        module=path.rsplit('/',1)[1].rsplit('.',1)[0]
        module=module.removeprefix('com.google.android.').removeprefix('com.android.').removesuffix('_compressed').removesuffix('_tplus')
        if module=='tzdata6':module='tzdata'
        if not re.fullmatch('[A-Za-z0-9_.-]+',module):raise ValueError('Invalid module name')
        target=module+'_payload.img';h=hashlib.sha256();size=0
        with zipfile.ZipFile(file) as z:
            stream,child=payload(z)
            try:
                with stream,(a.output/target).open('xb') as out:
                    for block in iter(lambda:stream.read(1024*1024),b''):
                        size+=len(block)
                        if size>2*1024**3:raise ValueError('Oversized payload')
                        h.update(block);out.write(block)
            finally:
                if child:child.close()
        values['source.'+target]=path;values['sha.'+target]=h.hexdigest();values['bytes.'+target]=str(size)
        report[target]={'sha256':h.hexdigest(),'bytes':size,'source':path}
        print('Prepared '+target+' ('+str(size)+' bytes)',flush=True)
    if not report:raise ValueError('No APEX modules exported')
    values['outputs']=','.join(sorted(report))
    (a.output/'recipes.properties').write_text(''.join(k+'='+v+'\n' for k,v in sorted(values.items())),encoding='ascii')
    (a.output/'result.json').write_text(json.dumps({'image_sha256':a.image_sha256,'state':'payloads-prepared-not-runtime-or-boot-validation','components':report},indent=2)+'\n')
    print('PREPARED_GUEST_APEX_PAYLOADS='+str(len(report)))

if __name__=='__main__':main()
