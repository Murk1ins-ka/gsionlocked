"""Native changes pinned to the inspected owner-supplied X9 Ultra IN image."""
import hashlib

PINS = {
    'vold': '7fe53478f0e482e64a680bd12f6ead83f0501d62cab5d6de085bfeec8acecc5f',
    'service.incremental.so': '25979ef93ee4b1a781c16adc783febdba2462563e5a7e69f4ba24d011d4b8427',
    'surfaceflinger': '4333d54834648e56c369c1457b12045fd40278f0f4bbe82cf5a142415b37e48a',
}


def patch(name, source):
    if name not in PINS or hashlib.sha256(source).hexdigest() != PINS[name]:
        raise ValueError('Not the inspected X9 Ultra IN binary: ' + name)
    result = bytearray(source)
    if name == 'surfaceflinger':
        # AIBinder_getExtension 0x471170; cbz x8,0x4711c4 at 0x471178;
        # IQtiComposer3Client::fromBinder 0x471184. Existing null fallback.
        changes = [(0x471178, bytes.fromhex('680200b4'), bytes.fromhex('13000014'))]
    else:
        offset = 0x104e6 if name == 'vold' else 0x6536b
        changes = [(offset, b'\0vold\0', b'\0gvld\0')]
        if name == 'vold':
            # VendorVoldNativeService try_start moved +0x2000 in this build.
            # tbz w19,#0,0x7ac38 -> existing not-declared success path.
            changes.append((0x7ab64, bytes.fromhex('b3060036'), bytes.fromhex('35000014')))
    for offset, old, new in changes:
        if result[offset:offset + len(old)] != old:
            raise ValueError(f'Unexpected native sequence at {offset:#x}')
        result[offset:offset + len(old)] = new
    return bytes(result)
