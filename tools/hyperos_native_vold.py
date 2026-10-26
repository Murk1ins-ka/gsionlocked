"""Build a pinned HyperOS-only gvld candidate. Offline; never installs or executes it.

Inspected AArch64 ELF functions: VoldNativeService::start at 0x6d3dc loads
String16("vold") from 0xf45d, then calls addService. VendorVoldNativeService's
try_start at 0x6cd98 branches to its not-declared path at 0x6ced4. Incremental
looks up the name at 0x64786 from 0xc005c and 0xc2710/14. PT_LOAD vaddrs equal
file offsets for these pinned binaries. Java callers must also use gvld.
"""
from pathlib import Path
import argparse,hashlib,json

PINS={'system-bin-vold':'f93d8d75894dacf39842fd349c7c271f18794dec0a3a450d56b39464b89273e4',
      'system-lib64-service.incremental.so':'2a8a3ff60d47b438acef3338d2f28ea13cc54cce1cad5d8bd55aa68f4adb7caa'}
CHANGES={'system-bin-vold':[(0xf45c,b'\0vold\0',b'\0gvld\0'),(0x6ce00,bytes.fromhex('b3060036'),bytes.fromhex('35000014'))],
         'system-lib64-service.incremental.so':[(0x64785,b'\0vold\0',b'\0gvld\0')]}
TARGETS={'system-bin-vold':'vold_patched','system-lib64-service.incremental.so':'service.incremental_patched.so'}

def patch(name,data):
    if hashlib.sha256(data).hexdigest()!=PINS[name]:raise ValueError('Native input differs from inspected HyperOS build')
    result=bytearray(data)
    for offset,old,new in CHANGES[name]:
        if result[offset:offset+len(old)]!=old or len(old)!=len(new):raise ValueError('Native instruction/string anchor differs')
        result[offset:offset+len(old)]=new
    return bytes(result)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    if a.output.exists():p.error('Existing output is preserved')
    prepared={name:patch(name,(a.inputs/name).read_bytes()) for name in PINS}
    a.output.mkdir(parents=True);report={}
    for name,data in prepared.items():
        path=a.output/TARGETS[name];path.write_bytes(data)
        report[path.name]={'source_sha256':PINS[name],'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'changed_offsets':[hex(offset) for offset,old,new in CHANGES[name]]}
    (a.output/'result.json').write_text(json.dumps({'state':'candidate-requires-guest-java-gvld-and-device-validation','artifacts':report},indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
