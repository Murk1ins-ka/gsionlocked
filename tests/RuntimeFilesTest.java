package dev.tempgsi.controller;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

public final class RuntimeFilesTest {
    static final byte[] IMAGE="synthetic image, not a real guest filesystem".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static File zip(Path dir,String system)throws Exception {
        String prefix=system.equals("evox")?"tempgsi-gsionlocked198/":"oneos/";
        Map<String,byte[]> files=new LinkedHashMap<>();for(String name:new String[]{"work198.jar","loader198","guardian198","artd-session.so","a","b","c","d","e","f"})files.put(prefix+name,name.getBytes("UTF-8"));
        Properties p=new Properties();p.setProperty("format","1");p.setProperty("system",system);p.setProperty("roots",system.equals("evox")?"tempgsi-evox154,tempgsi-gsionlocked198":"oneos");
        p.setProperty("directories",system.equals("evox")?"tempgsi-evox154/root":"oneos/cache");p.setProperty("gsi.sha256",RuntimeBundle.hash(new ByteArrayInputStream(IMAGE)));p.setProperty("host.services.sha256",String.join("",Collections.nCopies(64,"a")));
        String profile="directory=/data/local/tmp/tempgsi-gsionlocked198\nentry=EvoxWork198\njar=work198.jar\n";
        for(Map.Entry<String,byte[]> x:files.entrySet()){String hash=RuntimeBundle.hash(new ByteArrayInputStream(x.getValue()));p.setProperty("sha."+x.getKey(),hash);profile+="sha."+x.getKey().substring(prefix.length())+"="+hash+"\n";}
        if(system.equals("evox"))p.setProperty("profile.contents",profile);
        else p.setProperty("host.oneos/host.so","/system/lib64/libbase.so");
        File z=dir.resolve(system+".zip").toFile();try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(z))){out.putNextEntry(new ZipEntry("runtime.properties"));p.store(out,"fixture");out.closeEntry();for(Map.Entry<String,byte[]> x:files.entrySet()){out.putNextEntry(new ZipEntry("payload/"+x.getKey()));out.write(x.getValue());out.closeEntry();}}
        return z;
    }
    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("runtime-files-test-");File host=temp.resolve("host.so").toFile();Files.write(host.toPath(),new byte[]{7,8,9});
        RuntimeFiles.Platform platform=new RuntimeFiles.Platform(){public File hostFile(String path){return host;}public void mode(File f,int m){if(!f.exists())throw new AssertionError("chmod target absent");}public void symlink(String target,File path)throws Exception{Files.createSymbolicLink(path.toPath(),Paths.get(target));}};
        try{
            File image=temp.resolve("selected.img").toFile();Files.write(image.toPath(),IMAGE);
            for(String system:new String[]{"coloros","evox"}){
                File archive=zip(temp,system);RuntimeBundle bundle=new RuntimeBundle(archive,RuntimeBundle.hash(archive),RuntimeBundle.hash(image));
                File destination=temp.resolve(system+"-destination").toFile();destination.mkdir();File stage=temp.resolve(system+"-staging").toFile();
                RuntimeFiles.install(bundle,archive,image,stage,destination,platform,line->{});
                File output=new File(destination,system.equals("coloros")?"oneos/system.raw":"tempgsi-evox154/system.img");if(!Arrays.equals(Files.readAllBytes(output.toPath()),IMAGE))throw new AssertionError("Wrong copied image");
                if(system.equals("evox")&&!new File(destination,"gsionlocked-profile.properties").isFile())throw new AssertionError("Missing committed profile");
                if(system.equals("coloros")&&!Arrays.equals(Files.readAllBytes(new File(destination,"oneos/host.so").toPath()),new byte[]{7,8,9}))throw new AssertionError("Host component not copied");
                try{RuntimeFiles.install(bundle,archive,image,temp.resolve(system+"-again").toFile(),destination,platform,line->{});throw new AssertionError("Existing runtime overwritten");}catch(IOException expected){}
                File bad=temp.resolve(system+"-bad").toFile();bad.mkdir();File wrong=temp.resolve("wrong.img").toFile();Files.write(wrong.toPath(),new byte[]{0});
                try{RuntimeFiles.install(bundle,archive,wrong,temp.resolve(system+"-wrong-staging").toFile(),bad,platform,line->{});throw new AssertionError("Wrong GSI accepted");}catch(IOException expected){}
                if(bad.list().length!=0)throw new AssertionError("Failed installation exposed runtime");
                if(system.equals("evox")){
                    File rollback=temp.resolve("rollback-destination").toFile();rollback.mkdir();
                    try{RuntimeFiles.install(bundle,archive,image,temp.resolve("rollback-staging").toFile(),rollback,platform,line->{if(line.startsWith("RUNTIME_ROOT_READY="))throw new IllegalStateException("injected publication failure");});throw new AssertionError("Failure not injected");}catch(IllegalStateException expected){}
                    if(rollback.list().length!=0)throw new AssertionError("Partial runtime was not rolled back");
                }
                System.out.println("PASS: "+system+" filesystem install, image verification and existing-data protection");
            }
            System.out.println("PASS: publication failure rolls back; no Android services or real host files used");
        }finally{RuntimeFiles.removeOwn(temp.toFile());}
    }
}
