"""Generate pinned local-extraction recipes from an image listing and existing payloads."""
from pathlib import Path
import argparse, hashlib

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage',type=Path,required=True)
    p.add_argument('--apex-list',type=Path,required=True,help='ReadOnlyExt4 listing of /system/apex')
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    sources={}
    for name in args.apex_list.read_text(encoding='utf-8-sig').splitlines():
        name=name.strip()
        if not name.endswith(('.apex','.capex')):continue
        key=name.rsplit('.',1)[0]
        if key.startswith('com.google.android.'):key=key[len('com.google.android.'):]
        elif key.startswith('com.android.'):key=key[len('com.android.'):]
        else:continue
        key=key.removesuffix('_compressed').removesuffix('_tplus')
        if key=='tzdata6':key='tzdata'
        sources.setdefault(key,[]).append('/system/apex/'+name)
    manifest={name:digest for digest,name in (line.split() for line in (args.stage/'fix.sha256').read_text().splitlines())}
    outputs=sorted(name for name in manifest if name.endswith('_payload.img'))
    values={'format':'1','outputs':','.join(outputs)}
    for name in outputs:
        key=name.removesuffix('_payload.img');candidates=sources.get(key,[])
        if len(candidates)!=1:p.error('Ambiguous or missing APEX for '+name+': '+str(candidates))
        payload=args.stage/name;h=hashlib.sha256()
        with payload.open('rb') as f:
            for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
        if h.hexdigest()!=manifest[name]:p.error('Prepared payload differs: '+name)
        values['source.'+name]=candidates[0];values['sha.'+name]=manifest[name];values['bytes.'+name]=str(payload.stat().st_size)
    if args.output.exists():p.error('Existing recipe output is preserved')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(''.join(key+'='+value+'\n' for key,value in sorted(values.items())),encoding='ascii')
    print('PINNED_IMAGE_COMPONENTS='+str(len(outputs)))

if __name__=='__main__':main()
