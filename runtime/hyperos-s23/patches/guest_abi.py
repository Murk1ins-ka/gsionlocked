"""Declare OneOS's single 64-bit guest zygote in the guest Build class only."""
from pathlib import Path
import sys

PRIMARY = '''    const-string/jumbo v0, "ro.product.cpu.abilist"

    const-string v2, ","

    invoke-static {v0, v2}, Landroid/os/Build;->getStringList(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v0

    sput-object v0, Landroid/os/Build;->SUPPORTED_ABIS:[Ljava/lang/String;'''
SECONDARY = '''    const-string/jumbo v0, "ro.product.cpu.abilist32"

    invoke-static {v0, v2}, Landroid/os/Build;->getStringList(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v0

    sput-object v0, Landroid/os/Build;->SUPPORTED_32_BIT_ABIS:[Ljava/lang/String;'''
EMPTY = '''    # OneOS S23: this guest session starts only its own 64-bit zygote.
    const/4 v0, 0x0

    new-array v0, v0, [Ljava/lang/String;

    sput-object v0, Landroid/os/Build;->SUPPORTED_32_BIT_ABIS:[Ljava/lang/String;'''

def patch(text):
    # HyperOS uses v3 for the separator and has its own MTK/fake-32-bit ABI arrays.
    primary=PRIMARY.replace('v2','v3');secondary=SECONDARY.replace('v2','v3')
    if text.count(primary) != 1 or text.count(secondary) != 1:
        raise ValueError("Unexpected Build ABI initializer; refusing guest framework patch")
    if '.method static constructor blacklist <clinit>()V' not in text:
        raise ValueError("Missing Build class initializer")
    result=text.replace(primary,primary.replace('"ro.product.cpu.abilist"','"ro.product.cpu.abilist64"'),1).replace(secondary,EMPTY,1)
    for field in ('MTK_HBT_SUPPORTED_ABIS','MI_FAKE_32BIT_SUPPORTED_ABIS'):
        store='    sput-object v0, Landroid/os/Build;->'+field+':[Ljava/lang/String;'
        if result.count(store)!=1:raise ValueError('Unexpected HyperOS ABI store: '+field)
        replacement='''    const-string/jumbo v0, "ro.product.cpu.abilist64"
    invoke-static {v0, v3}, Landroid/os/Build;->getStringList(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;
    move-result-object v0
'''+store
        result=result.replace(store,replacement,1)
    for field in ('MTK_HBT_SUPPORTED_32_BIT_ABIS','MI_FAKE_32BIT_SUPPORTED_32_BIT_ABIS'):
        store='    sput-object v0, Landroid/os/Build;->'+field+':[Ljava/lang/String;'
        if result.count(store)!=1:raise ValueError('Unexpected HyperOS 32-bit ABI store: '+field)
        result=result.replace(store,'    const/4 v0, 0x0\n    new-array v0, v0, [Ljava/lang/String;\n'+store,1)
    return result

if __name__ == '__main__':
    root = Path(sys.argv[1])
    paths = list(root.glob('smali*/android/os/Build.smali'))
    if len(paths) != 1:
        sys.exit('Expected exactly one android.os.Build')
    path = paths[0]
    result = patch(path.read_text(encoding='utf-8'))
    with open(path, 'w', encoding='utf-8', newline='\n') as output:
        output.write(result)
    print(path.relative_to(root).parts[0])
