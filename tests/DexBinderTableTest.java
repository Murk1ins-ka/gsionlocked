package dev.tempgsi.controller;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class DexBinderTableTest {
    static final String DESCRIPTOR="Landroid/os/IInstalld;";
    static void rejects(File jar)throws Exception {
        try{DexBinderTable.read(jar,DESCRIPTOR);}catch(IOException expected){return;}
        throw new AssertionError("Malformed or ambiguous Binder table accepted");
    }
    static File jar(Path folder,String name,byte[] dex,boolean duplicate)throws Exception {
        File file=folder.resolve(name).toFile();try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
            if(dex!=null){z.putNextEntry(new ZipEntry("classes.dex"));z.write(dex);z.closeEntry();}
            if(duplicate){z.putNextEntry(new ZipEntry("classes2.dex"));z.write(dex);z.closeEntry();}
        }return file;
    }
    static void u4(byte[] bytes,int at,long value){for(int i=0;i<4;i++)bytes[at+i]=(byte)(value>>>(i*8));}
    static void checksums(byte[] bytes)throws Exception {
        byte[] sha=java.security.MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(bytes,32,bytes.length));
        System.arraycopy(sha,0,bytes,12,20);Adler32 a=new Adler32();a.update(bytes,12,bytes.length-12);u4(bytes,8,a.getValue());
    }
    public static void main(String[] args)throws Exception {
        SortedMap<String,DexBinderTable.Entry> a=DexBinderTable.read(new File(args[0]),DESCRIPTOR),b=DexBinderTable.read(new File(args[1]),DESCRIPTOR);
        if(a.size()!=3||b.size()!=3)throw new AssertionError("Incomplete method table");
        if(a.get("createUserData").code!=1||b.get("createUserData").code!=301)throw new AssertionError("Per-phone transaction IDs lost");
        if(!"(Ljava/lang/String;III)V".equals(a.get("createUserData").signature)||!a.get("createUserData").signature.equals(b.get("createUserData").signature))throw new AssertionError("Method prototype lost");
        if(!"(Ljava/lang/String;)Z".equals(a.get("isQuotaSupported").signature)||a.get("isQuotaSupported").code!=40)throw new AssertionError();
        rejects(new File(args[2])); // duplicate numeric transactions
        rejects(new File(args[3])); // overloaded method name
        Path temp=Files.createTempDirectory("dex-binder-test-");
        try{
            byte[] original;try(ZipFile z=new ZipFile(args[0]);InputStream in=z.getInputStream(z.getEntry("classes.dex"))){original=in.readAllBytes();}
            rejects(jar(temp,"duplicates.jar",original,true));rejects(jar(temp,"empty.jar",null,false));
            byte[] bad=original.clone();bad[bad.length-1]^=1;rejects(jar(temp,"checksum.jar",bad,false));
            bad=original.clone();u4(bad,60,0x7fffffff);checksums(bad);rejects(jar(temp,"offset.jar",bad,false));
            bad=original.clone();u4(bad,56,0x7fffffff);checksums(bad);rejects(jar(temp,"count.jar",bad,false));
            bad=Arrays.copyOf(original,70);rejects(jar(temp,"truncated.jar",bad,false));
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(temp)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
        System.out.println("PASS: real D8 Binder fixtures; per-device IDs/prototypes; corrupt, oversized, missing, duplicate and ambiguous tables rejected");
    }
}
