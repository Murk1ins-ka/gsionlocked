"""Verify and unpack a downloaded GSI using Python 3 standard libraries.
Run in a folder containing GSI-MANIFEST.json and the downloaded archives:
    python restore-gsi.py coloros
    python restore-gsi.py evolution
Existing output images are never overwritten.
"""
from pathlib import Path
import argparse,hashlib,json,lzma,shutil,zipfile

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(8*1024*1024),b''):h.update(b)
    return h.hexdigest()

def local(folder,name):
    if Path(name).name!=name or '/' in name or '\\' in name:raise ValueError('Invalid manifest filename')
    p=folder/name
    if p.is_symlink():raise ValueError('Symlink refused: '+name)
    return p

def verify(path,info):
    if not path.is_file() or path.stat().st_size!=info['bytes'] or sha(path)!=info['sha256']:
        raise ValueError('Missing or corrupt file: '+path.name)
    print('Verified:',path.name,flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('system',choices=['coloros','evolution']);args=parser.parse_args()
    folder=Path(__file__).resolve().parent
    m=json.loads((folder/'GSI-MANIFEST.json').read_text(encoding='utf-8'))[args.system]
    image=m['image'];out=local(folder,image['file'])
    if out.exists():raise ValueError('Output already exists: '+out.name)
    if shutil.disk_usage(folder).free < image['bytes']+(m.get('archive',{}).get('bytes',0)):
        raise ValueError('Not enough free disk space for the archive and raw image')
    if args.system=='evolution':
        archive=local(folder,m['file']);verify(archive,m);source=lzma.open(archive,'rb');zip_archive=None
    else:
        for item in m['assets']:verify(local(folder,item['file']),item)
        archive=local(folder,m['archive']['file'])
        if len(m['assets'])>1:
            if archive.exists():verify(archive,m['archive'])
            else:
                with archive.open('xb') as joined:
                    for item in m['assets']:
                        with local(folder,item['file']).open('rb') as part:shutil.copyfileobj(part,joined,8*1024*1024)
        verify(archive,m['archive']);zip_archive=zipfile.ZipFile(archive);source=zip_archive.open(image['file'])
    h=hashlib.sha256();n=0
    try:
        with source,out.open('xb') as target:
            for b in iter(lambda:source.read(8*1024*1024),b''):target.write(b);h.update(b);n+=len(b)
    finally:
        if zip_archive:zip_archive.close()
    if n!=image['bytes'] or h.hexdigest()!=image['sha256']:
        raise ValueError('Extracted image mismatch. Do not use the output file.')
    print('Verified raw image:',out.name,flush=True)

if __name__=='__main__':main()
