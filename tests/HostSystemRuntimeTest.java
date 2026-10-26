package dev.tempgsi.controller;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class HostSystemRuntimeTest {
    static void rejects(File capsule)throws Exception{
        try{HostSystemRuntime.verify(capsule);}catch(IOException expected){return;}
        throw new AssertionError("Modified capsule accepted");
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("local-host-runtime-test-");
        try{
            File fixture=new File(args[0]);File library=root.resolve("own-library.fixture").toFile();Files.write(library.toPath(),"fixture bytes, not a phone library".getBytes("UTF-8"));
            Set<String> reads=new HashSet<>();HostSystemRuntime.Sources sources=new HostSystemRuntime.Sources(){
                public File file(String path)throws Exception{
                    reads.add(path);if(path.equals("/system/framework/services.jar")||path.equals("/system/framework/framework.jar"))return fixture;
                    if(path.equals("/system/lib64/libbase.so"))return library;
                    if(path.startsWith("/system/lib64/"))return root.resolve("missing-library").toFile();
                    throw new IOException("Unexpected source access");
                }
                public void mode(File file,int mode){if(!file.exists())throw new AssertionError("Mode before file publication");}
            };
            Properties facts=new Properties();facts.setProperty("manufacturer","samsung");facts.setProperty("brand","samsung");facts.setProperty("sdk","37");facts.setProperty("model","Fixture Samsung");
            File capsule=root.resolve("host").toFile();Properties result=HostSystemRuntime.capture(capsule,sources,facts);
            if(!"3".equals(result.getProperty("installd.methods")))throw new AssertionError("Host table not captured");
            if(!"framework.jar".equals(result.getProperty("installd.source")))throw new AssertionError("Boot-class-path identity must take precedence");
            Properties map=new Properties();try(InputStream in=new FileInputStream(new File(capsule,"installd.properties"))){map.load(in);}
            if(!"301".equals(map.getProperty("method.createUserData|(Ljava/lang/String;III)V")))throw new AssertionError("Phone-specific ID not extracted");
            if(!Arrays.equals(Files.readAllBytes(library.toPath()),Files.readAllBytes(new File(capsule,"lib64/libbase.so").toPath())))throw new AssertionError("Host library bytes not copied");
            for(String path:reads)if(!path.startsWith("/system/"))throw new AssertionError("Read outside system inputs");
            try{HostSystemRuntime.capture(capsule,sources,facts);throw new AssertionError("Existing capsule overwritten");}catch(IOException expected){}
            Files.write(new File(capsule,"installd.properties").toPath(),"changed".getBytes("UTF-8"));rejects(capsule);
            facts.setProperty("accounts","not allowed");try{HostSystemRuntime.capture(root.resolve("private-rejected").toFile(),sources,facts);throw new AssertionError("Private fact accepted");}catch(IOException expected){}
            facts.remove("accounts");facts.setProperty("manufacturer","other");try{HostSystemRuntime.capture(root.resolve("vendor-rejected").toFile(),sources,facts);throw new AssertionError("Non-Samsung accepted");}catch(IOException expected){}
            System.out.println("PASS: local host runtime extraction from fixture system files; per-host IDs/libraries, hash verification, existing-data and privacy boundaries");
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(root)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
}
