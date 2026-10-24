"""Audit the publishable source tree, excluding ignored build/private directories."""
from pathlib import Path
import re, sys

ROOT=Path(__file__).resolve().parents[1]
IGNORED={'.git','build','dist','local-runtime','private','__pycache__'}
FORBIDDEN={'.keystore','.jks','.p12','.pem','.key','.img','.xz','.zip','.apk','.db','.log','.so','.jar'}

def source_files():
    for path in ROOT.rglob('*'):
        relative=path.relative_to(ROOT)
        if path.is_file() and not any(part in IGNORED for part in relative.parts):yield path

def main():
    errors=[];count=0
    for path in source_files():
        count+=1;rel=path.relative_to(ROOT)
        if path.suffix.lower() in FORBIDDEN:errors.append(f'Excluded artifact: {rel}')
        if path.stat().st_size>25*1024*1024:errors.append(f'Unexpected large file: {rel}')
        if path.suffix.lower() in {'.png'}:continue
        try:text=path.read_text(encoding='utf-8-sig')
        except UnicodeDecodeError:errors.append(f'Unexpected binary: {rel}');continue
        if re.search(r'C:[\\/]+Users[\\/]+[^\\/\s]+',text):errors.append(f'Personal Windows path: {rel}')
        if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',text):errors.append(f'Private key: {rel}')
        if re.search(r'\b(?:ghp|github_pat)_[A-Za-z0-9_]{25,}\b',text):errors.append(f'Access token: {rel}')
    if errors:
        print('\n'.join(errors));return 1
    print(f'PASS: {count} publishable files; no prohibited artifacts or detected credentials')
    return 0

if __name__=='__main__':raise SystemExit(main())
