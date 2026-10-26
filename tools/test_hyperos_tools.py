"""Boundary checks for offline HyperOS preparation; no device, mounts or firmware files."""
from pathlib import Path
import importlib.util,io,tempfile,unittest,zipfile

ROOT=Path(__file__).resolve().parents[1]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

apex=load('apex','tools/prepare_gsi_apex.py')
native=load('native','tools/hyperos_native_vold.py')
lock=load('lock','tools/hyperos_guest_lock_stub.py')
xspace=load('xspace','tools/hyperos_no_xspace_repair.py')
abi=load('abi','runtime/hyperos-s23/patches/guest_abi.py')
art=load('art','tools/build_hyperos_art.py')

def archive(entries):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in entries:z.writestr(name,data)
    return stream.getvalue()

class PreparationTests(unittest.TestCase):
    def test_art_status_field_order_and_constructor_default(self):
        text='''.method public final writeToParcel(Landroid/os/Parcel;I)V
    .locals 1
    iget-object v0, p0, Lcom/android/server/art/GetDexoptStatusResult;->compilerFilter:Ljava/lang/String;
    invoke-virtual {p1, v0}, Landroid/os/Parcel;->writeString(Ljava/lang/String;)V
    iget p0, p0, Lcom/android/server/art/GetDexoptStatusResult;->artifactsLocation:I
    invoke-virtual {p1, p0}, Landroid/os/Parcel;->writeInt(I)V
    return-void
.end method'''
        self.assertEqual(art.status_fields(text),[('compilerFilter','Ljava/lang/String;','writeString'),('artifactsLocation','I','writeInt')])
        constructor='.method public constructor <init>()V\n    .locals 1\n    return-void\n.end method'
        self.assertIn('->dex2oatCmdline:Ljava/lang/String;',art.default_status_command(constructor))
        with self.assertRaises(ValueError):art.default_status_command(constructor.replace('.locals 1','.locals 0'))
    def test_direct_and_nested_stream_integrity(self):
        data=bytes(range(256))*32;direct=archive([('apex_payload.img',data)])
        for content in [direct,archive([('original_apex',direct)])]:
            with zipfile.ZipFile(io.BytesIO(content)) as z:
                stream,child=apex.payload(z)
                try:
                    with stream:self.assertEqual(stream.read(),data)
                finally:
                    if child:child.close()
    def test_ambiguous_and_deep_archives_rejected(self):
        direct=archive([('apex_payload.img',b'x'*4096)])
        for content in [archive([('../apex_payload.img',b'x'*4096)]),archive([('apex_payload.img',b'x'*4096),('original_apex',direct)]),archive([('original_apex',archive([('original_apex',direct)]))])]:
            with zipfile.ZipFile(io.BytesIO(content)) as z:
                with self.assertRaises(ValueError):apex.payload(z)
    def test_unknown_firmware_never_patched_by_offsets(self):
        for name in native.PINS:
            with self.assertRaises(ValueError):native.patch(name,b'\0vold\0'+b'\0'*4096)
        with self.assertRaises(ValueError):lock.patch('.class public LLockSettingsStub;\n')
        with self.assertRaises(ValueError):xspace.patch('.method private repairProfileGroupIdIfNecessaryLP()V\n    return-void\n.end method')
        with self.assertRaises(ValueError):abi.patch('changed ABI initializer')
    def test_manifest_duplicate_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'components.properties';p.write_text('sha.test=first\nsha.test=second\n')
            with self.assertRaises(ValueError):apex.properties(p)

if __name__=='__main__':unittest.main()
