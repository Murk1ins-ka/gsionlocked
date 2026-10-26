"""Check the pinned ColorOS release contract without a phone or Android SDK."""
from pathlib import Path
import hashlib, json, re

ROOT=Path(__file__).resolve().parents[1]
profile=json.loads((ROOT/'profiles/dm1q-coloros17.json').read_text())
manifest=(ROOT/'runtime/coloros-s23/fix.sha256').read_bytes()
script=(ROOT/'app/src/main/assets/coloros-session.sh').read_text()
assert b'\r' not in manifest
pin=hashlib.sha256(manifest).hexdigest()
assert pin==profile['runtime']['manifest_sha256'] and pin in script
assert profile['gsi']['sha256'] in script
for line in manifest.decode().splitlines():
    digest,name=line.split()
    assert re.fullmatch('[a-f0-9]{64}',digest) and re.fullmatch('[A-Za-z0-9_.-]+',name)
assert 'fix-s23-daily-use.sh' in manifest.decode()
main=(ROOT/'app/src/main/java/dev/tempgsi/controller/MainActivity.java').read_text()
assert main.index('ColorOsProfile.matches(selectedSha)){confirmColorOs()')<main.index('DeviceProfile.requireHost();')
assert 'COLOROS_START_DISPATCHED=' in main
assert 'ro.product.model' not in script and 'ro.product.vendor.device' not in script
assert 'ro.product.cpu.abi' in script and 'arm64-v8a' in script
assert 'getprop ro.product.manufacturer' in script and 'getprop ro.product.brand' in script
assert script.index('getprop ro.product.manufacturer') < script.index('getprop ro.build.version.sdk')
color=(ROOT/'app/src/main/java/dev/tempgsi/controller/ColorOsProfile.java').read_text()
assert 'DeviceProfile.requireHost();' in color and 'Build.MODEL' not in color
assert 'new File("/data/oneos_genv.sh").isFile()' in color
assert '--coloros' in (ROOT/'app/src/main/session_launch.c').read_text()
assert 'dev.tempgsi.controller/files/oneos_return' in (ROOT/'runtime/coloros-s23/launcher-contract.txt').read_text()
print('PASS: ColorOS digest, runtime manifest, helper and separate launch route')
