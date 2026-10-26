"""Check guarded-proxy transformations, including apktool's comment-free output."""
from dynamic_installd_dispatch import patch, ADAPTER

def method(name,blocked=False):
    return '''.method public '''+name+'''()V
    .locals 5
'''+('    const-string v1, "OneOS: installd.'+name+' blocked in the container"\n    throw v0\n' if blocked else '')+'''
    const/16 v1, 0x14
    invoke-interface {v0, v1, v2, v3, v4}, Landroid/os/IBinder;->transact(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z
    return-void
.end method'''

def main():
    blocks=[method(name) for name in ['createAppData','createAppDataBatched','restoreconAppData','invalidateMounts']]
    blocks+=[method('getAppSize',True),method('destroyUserData',True)]
    source='.class public final Lcom/android/server/pm/OneOSInstalldProxy;\n'+'\n'.join(blocks)
    result,names=patch(source)
    assert len(names)==4 and result.count(ADAPTER)==4
    assert blocks[-1] in result and blocks[-2] in result
    try:patch(result)
    except ValueError:pass
    else:raise AssertionError('Repeated patch accepted')
    try:patch(source.replace('OneOS: installd.destroyUserData blocked in the container','unguarded'))
    except ValueError:pass
    else:raise AssertionError('Missing destructive guard accepted')
    print('PASS: dynamic proxy transform preserves comment-free denied methods and rejects missing guards/repeated patch')

if __name__=='__main__':main()
