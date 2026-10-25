"""Preserve the real application package in Camera2's Samsung-bound attribution."""
from pathlib import Path
import re
import sys

OLD = '    invoke-virtual {v2, v7}, Landroid/content/AttributionSource$Builder;->setPackageName(Ljava/lang/String;)Landroid/content/AttributionSource$Builder;'
NEW = '    # OneOS S23: Samsung AppOps requires the actual package, not package/Activity.\n' + OLD.replace('{v2, v7}', '{v2, v0}')

def patch(text):
    methods = list(re.finditer(r'^\.method [^\n]*openCameraDeviceUserAsync\([^\n]*\n.*?^\.end method', text, re.M | re.S))
    if len(methods) != 1:
        raise ValueError('Expected one Camera2 open method')
    method = methods[0]
    body = method.group()
    if body.count(OLD) != 1 or NEW in body:
        raise ValueError('Unexpected Camera2 component attribution sequence')
    anchor = 'invoke-virtual {v0}, Landroid/content/Context;->getOpPackageName()Ljava/lang/String;\n\n    move-result-object v0'
    if body.count(anchor) != 1:
        raise ValueError('Canonical package register is not verified')
    start = body.index(anchor) + len(anchor)
    end = body.index(OLD)
    if re.search(r'^\s*(?:move-result-object|move-object(?:/from16|/16)?|const-string(?:/jumbo)?|new-instance|iget-object) v0(?:,|\s*$)', body[start:end], re.M):
        raise ValueError('Canonical package register overwritten')
    return text[:method.start()] + body.replace(OLD, NEW, 1) + text[method.end():]

if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/android/hardware/camera2/CameraManager.smali'))
    if len(paths) != 1:
        sys.exit('Expected one CameraManager')
    path = paths[0]
    result = patch(path.read_text(encoding='utf-8'))
    with open(path, 'w', encoding='utf-8', newline='\n') as output:
        output.write(result)
    print(path.relative_to(root))
