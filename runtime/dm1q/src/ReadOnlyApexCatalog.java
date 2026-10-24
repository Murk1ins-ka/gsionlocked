// Binder catalog of the actual, immutable APEX trees mounted in this container.
// Installation, rollback and activation changes are deliberately unsupported.
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
public final class ReadOnlyApexCatalog extends Binder {
    static final String DESCRIPTOR="android.apex.IApexService";
    final Map<Integer,String> methods=new HashMap<>();
    final List<Parcelable> packages=new ArrayList<>();
    final Map<String,Parcelable> names=new HashMap<>();
    final Class<?> infoClass;
    static long varint(byte[] bytes,int[] cursor)throws IOException {
        long value=0;for(int shift=0;shift<64;shift+=7){if(cursor[0]>=bytes.length)throw new EOFException();int b=bytes[cursor[0]++]&255;value|=(long)(b&127)<<shift;if((b&128)==0)return value;}throw new IOException("Invalid manifest varint");
    }
    static Map<Integer,Object> manifest(File file)throws IOException {
        byte[] bytes=Files.readAllBytes(file.toPath());if(bytes.length>1024*1024)throw new IOException("Manifest too large");
        int[] cursor={0};Map<Integer,Object> result=new HashMap<>();
        while(cursor[0]<bytes.length){long key=varint(bytes,cursor);int wire=(int)(key&7),field=(int)(key>>>3);
            if(wire==0){long value=varint(bytes,cursor);if(field==2)result.put(field,value);}
            else if(wire==2){long length=varint(bytes,cursor);if(length<0||length>bytes.length-cursor[0])throw new IOException("Manifest length");if(field==1||field==5)result.put(field,new String(bytes,cursor[0],(int)length,StandardCharsets.UTF_8));cursor[0]+=(int)length;}
            else if(wire==1)cursor[0]+=8;else if(wire==5)cursor[0]+=4;else throw new IOException("Unsupported manifest wire type");
            if(cursor[0]>bytes.length)throw new EOFException();
        }return result;
    }
    void field(Object info,String name,Object value)throws Exception {
        try{infoClass.getField(name).set(info,value);}catch(NoSuchFieldException absent){System.out.println("APEX_OPTIONAL_FIELD_ABSENT="+name);}
    }
    ReadOnlyApexCatalog()throws Exception {
        attachInterface(null,DESCRIPTOR);infoClass=Class.forName("android.apex.ApexInfo");
        for(Field field:Class.forName(DESCRIPTOR+"$Stub").getDeclaredFields())if(field.getName().startsWith("TRANSACTION_")){field.setAccessible(true);methods.put(field.getInt(null),field.getName().substring(12));}
        File[] directories=new File("/apex").listFiles();if(directories==null)throw new IOException("No mounted apex tree");Arrays.sort(directories);
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><apex-info-list>\n");
        for(File directory:directories){String name=directory.getName();if(!directory.isDirectory()||!name.matches("com\\.[A-Za-z0-9_.]+"))continue;
            if(name.equals("com.android.vndk.v33")&&new File(directory,".tempgsi-library-lists-only").isFile()){
                if(new File(directory,"apex_manifest.pb").exists()||!new File(directory,"etc/llndk.libraries.33.txt").isFile()||!new File(directory,"etc/vndksp.libraries.33.txt").isFile())throw new IOException("Invalid legacy library metadata directory");
                System.out.println("PRIVATE_VNDK33_LIBRARY_LISTS_EXCLUDED_FROM_ACTIVE_APEX_CATALOG");continue;
            }
            Map<Integer,Object> manifest=manifest(new File(directory,"apex_manifest.pb"));if(!name.equals(manifest.get(1))||!(manifest.get(2) instanceof Long))throw new IOException("Mounted APEX manifest mismatch: "+name);
            File source=new File("/system/apex/"+name+".apex");if(!source.isFile())source=new File("/system/apex/"+name+".capex");if(!source.isFile())throw new IOException("Missing source APEX: "+name);
            String versionName=(String)manifest.getOrDefault(5,"");long version=(Long)manifest.get(2);
            Object info=infoClass.getConstructor().newInstance();field(info,"moduleName",name);field(info,"modulePath",source.getPath());field(info,"preinstalledModulePath",source.getPath());field(info,"versionCode",version);field(info,"versionName",versionName);field(info,"isFactory",true);field(info,"isActive",true);
            packages.add((Parcelable)info);names.put(name,(Parcelable)info);
            xml.append("<apex-info moduleName=\"").append(name).append("\" modulePath=\"").append(source.getPath()).append("\" preinstalledModulePath=\"").append(source.getPath()).append("\" versionCode=\"").append(version).append("\" versionName=\"").append(escape(versionName)).append("\" isFactory=\"true\" isActive=\"true\" lastUpdateMillis=\"0\" provideSharedApexLibs=\"false\"/>\n");
        }
        if(packages.size()!=36)throw new IOException("Expected 36 mounted APEX, found "+packages.size());
        xml.append("</apex-info-list>\n");Files.write(new File("/apex/apex-info-list.xml").toPath(),xml.toString().getBytes(StandardCharsets.UTF_8));
        Object labeled=Class.forName("android.os.SELinux").getMethod("setFileContext",String.class,String.class).invoke(null,"/apex/apex-info-list.xml","u:object_r:apex_info_file:s0");if(!Boolean.TRUE.equals(labeled))throw new IOException("APEX metadata label failed");
    }
    static String escape(String value){return value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;");}
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
        if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
        String method=methods.get(code);if(method==null)return super.onTransact(code,data,reply,flags);
        data.enforceInterface(DESCRIPTOR);System.out.println("READ_ONLY_APEX_CALL="+method);
        switch(method){
        case "getActivePackages":case "getAllPackages":
            Parcelable[] array=(Parcelable[])Array.newInstance(infoClass,packages.size());packages.toArray(array);reply.writeNoException();reply.writeTypedArray(array,0);return true;
        case "getActivePackage":
            Parcelable info=names.get(data.readString());reply.writeNoException();reply.writeTypedObject(info,0);return true;
        case "getSessions":
            try{Parcelable[] empty=(Parcelable[])Array.newInstance(Class.forName("android.apex.ApexSessionInfo"),0);reply.writeNoException();reply.writeTypedArray(empty,0);return true;}catch(ClassNotFoundException absent){throw new IllegalStateException(absent);}
        case "markBootCompleted":reply.writeNoException();return true;
        default:reply.writeException(new UnsupportedOperationException("Immutable container APEX catalog: "+method));return true;
        }
    }
    public static void main(String[] args)throws Exception {
        Class.forName("android.os.flagging.AconfigPackage").getMethod("load",String.class).invoke(null,"android.permission.flags");
        System.out.println("PRIVATE_PERMISSION_ACONFIG_PACKAGE_LOADED");
        ReadOnlyApexCatalog catalog=new ReadOnlyApexCatalog();
        Class.forName("android.os.ServiceManager").getMethod("addService",String.class,IBinder.class).invoke(null,"apexservice",catalog);
        Files.write(new File("/data/local/tmp/apex-catalog.ready").toPath(),new byte[]{1});System.out.println("READ_ONLY_APEX_CATALOG_REGISTERED_COUNT="+catalog.packages.size());
        Binder.class.getMethod("joinThreadPool").invoke(null);
    }
}
