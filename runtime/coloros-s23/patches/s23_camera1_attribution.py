"""Use the calling context's real package for legacy Camera API attribution."""
from pathlib import Path
import re
import sys

OLD = '''    sget-object v1, Landroid/hardware/Camera;->sCamExt:Landroid/hardware/ICameraExt;

    invoke-interface {v1}, Landroid/hardware/ICameraExt;->getComponentName()Ljava/lang/String;

    move-result-object v1'''
NEW = '''    # OneOS S23: keep the real package for Samsung AppOps on Camera API1 too.
    invoke-virtual {p2}, Landroid/content/Context;->getOpPackageName()Ljava/lang/String;

    move-result-object v1'''

def patch(text):
    methods = list(re.finditer(r'^\.method private blacklist cameraInit\(ILandroid/content/Context;Landroid/content/res/CameraCompatibilityInfo;\)I\n.*?^\.end method', text, re.M | re.S))
    if len(methods) != 1:
        raise ValueError('Expected pinned Camera.cameraInit descriptor')
    method = methods[0]
    body = method.group()
    if body.count(OLD) != 1 or body.count('invoke-virtual {v0, v1}, Landroid/content/AttributionSource$Builder;->setPackageName(') != 1:
        raise ValueError('Unexpected Camera API1 attribution sequence')
    return text[:method.start()] + body.replace(OLD, NEW, 1) + text[method.end():]

if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/android/hardware/Camera.smali'))
    if len(paths) != 1:
        sys.exit('Expected one Camera class')
    path = paths[0]
    result = patch(path.read_text(encoding='utf-8'))
    with open(path, 'w', encoding='utf-8', newline='\n') as output:
        output.write(result)
    print(path.relative_to(root))
