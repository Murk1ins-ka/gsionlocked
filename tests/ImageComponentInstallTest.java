package dev.tempgsi.controller;
import java.io.*;
import java.nio.file.*;

/** Full file transaction using a synthetic GSI and a package without its APEX payload. */
public final class ImageComponentInstallTest {
    public static void main(String[] args)throws Exception {
        File image=new File(args[0]),archive=new File(args[1]),destination=new File(args[2]);
        if(!destination.mkdir())throw new IOException("Cannot create test destination");
        RuntimeBundle bundle=new RuntimeBundle(archive,RuntimeBundle.hash(archive),RuntimeBundle.hash(image));
        RuntimeFiles.Platform platform=new RuntimeFiles.Platform(){
            public File hostFile(String path){throw new AssertionError("Unexpected host access");}
            public void mode(File file,int mode){if(!file.exists())throw new AssertionError("Missing file");}
            public void symlink(String target,File path){throw new AssertionError("Unexpected symlink");}
        };
        try{
            RuntimeFiles.install(bundle,archive,image,new File(destination.getParentFile(),destination.getName()+"-stage"),destination,platform,line->{});
            if(args[3].equals("reject"))throw new AssertionError("Invalid component was published");
            File component=new File(destination,"oneos/test_payload.img");
            if(!RuntimeBundle.hash(component).equals(bundle.files.get("oneos/test_payload.img")))throw new AssertionError("Wrong derived payload");
        }catch(IOException failure){
            if(!args[3].equals("reject"))throw failure;
            if(destination.list().length!=0)throw new AssertionError("Failed derivation exposed runtime");
        }
    }
}
