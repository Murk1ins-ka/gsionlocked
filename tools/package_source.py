"""Create a source archive using the same allowlist as the public audit."""
from pathlib import Path
import hashlib, json, zipfile
from audit_public import ROOT,main,source_files

if main():raise SystemExit(1)
dist=ROOT/'dist';dist.mkdir(exist_ok=True)
target=dist/'gsionlocked-source.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(source_files()):archive.write(path,'gsionlocked/'+path.relative_to(ROOT).as_posix())
digest=hashlib.sha256(target.read_bytes()).hexdigest()
(dist/'source-sha256.txt').write_text(digest+'  '+target.name+'\n')
print(target)
