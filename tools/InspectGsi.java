import dev.tempgsi.controller.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read system-only GSI metadata and optional framework inputs. Never mounts an image. */
public final class InspectGsi {
    public static void main(String[] args)throws Exception {
        if(args.length<2||args.length>5)throw new IOException("Usage: InspectGsi image.img new-output-directory [--framework] [--apex] [--native]");
        Set<String> options=new HashSet<>(Arrays.asList(args).subList(2,args.length));
        if(!new HashSet<>(Arrays.asList("--framework","--apex","--native")).containsAll(options))throw new IOException("Unknown inspection option");
        File source=new File(args[0]),out=new File(args[1]);
        if(out.exists()||Files.isSymbolicLink(out.toPath()))throw new IOException("Existing inspection directory is preserved");
        byte[] header=new byte[4096];try(InputStream in=new FileInputStream(source)){if(in.read(header)!=header.length)throw new EOFException("Image header truncated");}
        GsiFormat.validate(header,source.length());if(!out.mkdir())throw new IOException("Cannot create inspection directory");
        Properties facts=new Properties(),components=new Properties();facts.setProperty("image.bytes",Long.toString(source.length()));facts.setProperty("image.format","raw-ext4");
        long copied=0;
        try(ReadOnlyExt4 image=new ReadOnlyExt4(source)){
            if(options.contains("--native"))for(String path:new String[]{"/system/bin/vold","/system/lib64/service.incremental.so"}){
                ReadOnlyExt4.Node node=image.lookup(path);if(!node.regular()||node.size>64L*1024*1024)throw new IOException("Invalid native input");
                String target=path.substring(1).replace('/','-');File file=new File(out,target);
                try(InputStream in=node.open()){Files.copy(in,file.toPath());}
                components.setProperty("source."+target,path);components.setProperty("sha."+target,RuntimeBundle.hash(file));components.setProperty("bytes."+target,Long.toString(node.size));copied+=node.size;
            }
            for(String path:new String[]{"/system/build.prop","/system/system/build.prop","/system_ext/build.prop","/system/system_ext/build.prop","/product/build.prop","/system/product/build.prop","/mi_product/build.prop","/mi_ext/build.prop"}){
                ReadOnlyExt4.Node node;try{node=image.lookup(path);}catch(FileNotFoundException missing){continue;}
                if(!node.regular()||node.size>1024*1024)throw new IOException("Invalid build properties");
                Properties values=new Properties();try(InputStream in=node.open()){values.load(in);}
                for(String key:values.stringPropertyNames())if(key.matches("ro\\.(?:(?:system\\.|product\\.|system_ext\\.|vendor\\.)?build\\.(?:version\\..*|fingerprint|id|display\\.id)|product\\.(?:(?:system|product|system_ext)\\.)?(?:model|device|name|brand|manufacturer|cpu\\.abi.*)|miui\\..*|mi\\.os\\..*|vndk\\.version)")){
                    facts.setProperty(path+":"+key,values.getProperty(key));
                }
            }
            for(String path:new String[]{"/system/apex","/system/framework","/system_ext/framework"}){
                SortedSet<String> names;try{names=image.list(path);}catch(FileNotFoundException missing){continue;}
                Files.write(new File(out,path.substring(1).replace('/','-')+".txt").toPath(),names,java.nio.charset.StandardCharsets.UTF_8);
                if(path.endsWith("apex")&&options.contains("--apex")){
                    for(String name:names){
                        if(!name.matches("com\\.(?:google\\.)?android\\.[A-Za-z0-9_.-]+\\.(?:apex|capex)")||name.matches(".*(?:vndk|compatible|\\.virt\\.|\\.compos\\.|cts\\.shim).*"))continue;
                        ReadOnlyExt4.Node node=image.lookup(path+"/"+name);
                        if(!node.regular()||node.size>256L*1024*1024)throw new IOException("Invalid APEX input");
                        copied+=node.size;if(copied>2L*1024*1024*1024)throw new IOException("Inspection input limit exceeded");
                        String target=path.substring(1).replace('/','-')+"-"+name;File file=new File(out,target);
                        try(InputStream in=node.open()){Files.copy(in,file.toPath());}
                        components.setProperty("source."+target,path+"/"+name);components.setProperty("sha."+target,RuntimeBundle.hash(file));components.setProperty("bytes."+target,Long.toString(node.size));
                    }
                }
                if(!options.contains("--framework")||!path.endsWith("framework"))continue;
                for(String name:names){
                    if(!name.matches("(?:framework.*|services.*|miui.*|ext)\\.jar"))continue;
                    ReadOnlyExt4.Node node=image.lookup(path+"/"+name);if(!node.regular()||node.size>128L*1024*1024)throw new IOException("Invalid framework input");
                    copied+=node.size;if(copied>2L*1024*1024*1024)throw new IOException("Inspection input limit exceeded");
                    String target=path.substring(1).replace('/','-')+"-"+name;File file=new File(out,target);
                    try(InputStream in=node.open()){Files.copy(in,file.toPath());}
                    components.setProperty("source."+target,path+"/"+name);components.setProperty("sha."+target,RuntimeBundle.hash(file));components.setProperty("bytes."+target,Long.toString(node.size));
                }
            }
        }
        try(OutputStream output=new FileOutputStream(new File(out,"image.properties"))){facts.store(output,"System-only GSI inspection");}
        try(OutputStream output=new FileOutputStream(new File(out,"components.properties"))){components.store(output,"Read-only framework inputs");}
        for(String key:new TreeSet<>(facts.stringPropertyNames()))System.out.println(key+"="+facts.getProperty(key));
        System.out.println("INSPECTION_INPUT_BYTES="+copied);
    }
}
