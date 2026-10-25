package dev.tempgsi.controller;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class RuntimeBundleTest {
    static final String IMAGE=String.join("",Collections.nCopies(64,"a"));
    static File fixture(Path dir,String bad)throws Exception {
        Properties p=new Properties();p.setProperty("format","1");p.setProperty("system","coloros");p.setProperty("roots","oneos");p.setProperty("gsi.sha256",IMAGE);p.setProperty("host.services.sha256",IMAGE);
        Map<String,byte[]> data=new LinkedHashMap<>();for(int i=0;i<10;i++)data.put("oneos/file"+i,new byte[]{(byte)i});
        if(bad.equals("escape"))data.put("oneos/../../escape",new byte[]{1});
        if(bad.equals("userdata"))data.put("oneos/data/user/0/key",new byte[]{1});
        if(bad.equals("host"))p.setProperty("host.oneos/host.so","/data/adb/ksud");
        if(bad.equals("link-parent"))p.setProperty("link.oneos","/system/lib64");
        for(Map.Entry<String,byte[]> x:data.entrySet())p.setProperty("sha."+x.getKey(),RuntimeBundle.hash(new ByteArrayInputStream(x.getValue())));
        if(bad.equals("missing"))p.setProperty("sha.oneos/missing",IMAGE);
        File zip=dir.resolve("test-"+bad+".zip").toFile();try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){out.putNextEntry(new ZipEntry("runtime.properties"));p.store(out,"test");out.closeEntry();for(Map.Entry<String,byte[]> x:data.entrySet()){out.putNextEntry(new ZipEntry("payload/"+x.getKey()));out.write(x.getValue());out.closeEntry();}}
        return zip;
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3){RuntimeBundle b=new RuntimeBundle(new File(args[0]),args[1],args[2]);System.out.println("VERIFIED: "+b.system+" "+b.files.size()+" files");return;}
        Path temp=Files.createTempDirectory("runtime-bundle-test-");
        try{
            File good=fixture(temp,"good");new RuntimeBundle(good,RuntimeBundle.hash(good),IMAGE);
            for(String bad:new String[]{"escape","userdata","host","link-parent","missing"}){File zip=fixture(temp,bad);boolean rejected=false;try{new RuntimeBundle(zip,RuntimeBundle.hash(zip),IMAGE);}catch(IOException expected){rejected=true;}if(!rejected)throw new AssertionError("Accepted "+bad);}
            try{new RuntimeBundle(good,IMAGE,IMAGE);throw new AssertionError("Accepted wrong package digest");}catch(IOException expected){}
            System.out.println("PASS: runtime packages reject traversal, private data, host-path abuse, link parents, missing files and wrong hashes");
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}}
    }
}
