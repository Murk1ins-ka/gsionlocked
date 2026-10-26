import android.os.Parcel;
import dalvik.system.DexClassLoader;
import java.lang.reflect.*;

/** Pure parcel test as shell UID. No Binder calls, system writes or guest boot. */
public final class HyperosArtParcelProbe {
    static final String TYPE="com.android.server.art.GetDexoptStatusResult";
    static final String[] FIELDS={"compilerFilter","compilationReason","locationDebugString","isBackedByVdexOnly","artifactsLocation"};
    static final Object[] VALUES={"verify","install","guest-only-test",Boolean.TRUE,Integer.valueOf(3)};
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("host.jar guest.jar original|patched");
        ClassLoader parent=HyperosArtParcelProbe.class.getClassLoader();
        System.out.println("PROBE_LOAD_HOST");
        Class<?> host=new DexClassLoader(args[0],null,null,parent).loadClass(TYPE);
        System.out.println("PROBE_LOAD_GUEST");
        Class<?> guest=new DexClassLoader(args[1],null,null,parent).loadClass(TYPE);
        System.out.println("PROBE_CONSTRUCT_HOST");
        Object before=host.getConstructor().newInstance();for(int i=0;i<FIELDS.length;i++)host.getField(FIELDS[i]).set(before,VALUES[i]);
        System.out.println("PROBE_CONSTRUCT_GUEST");
        Object after=guest.getConstructor().newInstance();Parcel p=Parcel.obtain();
        try{
            host.getMethod("writeToParcel",Parcel.class,int.class).invoke(before,p,0);p.setDataPosition(0);
            System.out.println("PROBE_READ_HOST_PARCEL");
            guest.getMethod("readFromParcel",Parcel.class).invoke(after,p);
            for(int i=0;i<FIELDS.length;i++)if(!VALUES[i].equals(guest.getField(FIELDS[i]).get(after)))throw new AssertionError("Wrong field: "+FIELDS[i]);
            if(p.dataPosition()!=p.dataSize())throw new AssertionError("Guest did not consume host parcel");
            Object command=guest.getField("dex2oatCmdline").get(after);
            if(args[2].equals("original")){
                if(command!=null)throw new AssertionError("Expected missing newer field in original guest");
                System.out.println("ART_ORIGINAL_OLD_HOST_COMMAND_FIELD=null");
            }else if(args[2].equals("patched")){
                if(!"".equals(command))throw new AssertionError("New field lacks non-null default");
            }else throw new IllegalArgumentException("Unknown test mode");
        }finally{p.recycle();}
        Parcel reverse=Parcel.obtain();
        try{
            guest.getMethod("writeToParcel",Parcel.class,int.class).invoke(after,reverse,0);reverse.setDataPosition(0);
            Object decoded=host.getConstructor().newInstance();host.getMethod("readFromParcel",Parcel.class).invoke(decoded,reverse);
            for(int i=0;i<FIELDS.length;i++)if(!VALUES[i].equals(host.getField(FIELDS[i]).get(decoded)))throw new AssertionError("Reverse field: "+FIELDS[i]);
            if(reverse.dataPosition()!=reverse.dataSize())throw new AssertionError("Host failed to skip new trailing field");
        }finally{reverse.recycle();}
        System.out.println("ART_PARCEL_BACKCOMPAT_OK="+args[2]);
    }
}
