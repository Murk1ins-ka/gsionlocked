"""Keep ColorOS' real connection-result handler independent of region flags."""
from pathlib import Path
import re
import sys

HEADER = '.method public final getOplusWifiConnectionAlert()Lcom/oplus/server/wifi/OplusWifiConnectionAlert;'
OLD = '''    invoke-virtual {v0, v1}, Lcom/oplus/content/OplusFeatureConfigManager;->hasFeature(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_1'''


def patch(text):
    match = re.search(re.escape(HEADER) + r'\n.*?^\.end method', text, re.M | re.S)
    if not match or match.group().count(OLD) != 1:
        raise ValueError('Unexpected ColorOS primary Wi-Fi alert initializer')
    body = match.group().replace(OLD, '    # OneOS: Settings requires real connection-result broadcasts in every region.\n    const/4 v0, 0x1', 1)
    return text[:match.start()] + body + text[match.end():]


if __name__ == '__main__':
    file = Path(sys.argv[1]) / 'smali/com/oplus/server/wifi/common/OplusWifiInjector.smali'
    result = patch(file.read_text(encoding='utf-8'))
    with open(file, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(result)
