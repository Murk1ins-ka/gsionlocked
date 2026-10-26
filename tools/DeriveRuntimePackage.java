import dev.tempgsi.controller.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Convert a pinned ColorOS format-1 package into format 2 without its GSI modules.
 * Does not alter scripts/framework guards, install anything or access a phone. */
public final class DeriveRuntimePackage {
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IOException("Usage: archive.zip expected-sha256 recipes.properties new-output.zip");
        File source=new File(args[0]),recipes=new File(args[2]),output=new File(args[3]);
        if(output.exists()||Files.isSymbolicLink(output.toPath()))throw new IOException("Existing output is preserved");
        String image;
        try(ZipFile zip=new ZipFile(source)){
            ZipEntry entry=zip.getEntry("runtime.properties");if(entry==null||entry.getSize()<0||entry.getSize()>4*1024*1024)throw new IOException("Invalid source manifest");
            Properties values=new Properties();try(InputStream in=zip.getInputStream(entry)){values.load(in);}image=values.getProperty("gsi.sha256","");
        }
        RuntimeBundle bundle=new RuntimeBundle(source,args[1],image);
        if(!bundle.system.equals("coloros")||!bundle.imageComponents.isEmpty())throw new IOException("Expected original ColorOS runtime");
        List<GuestImageComponents.Recipe> components=GuestImageComponents.readRecipes(recipes);
        Properties manifest=new Properties();manifest.putAll(bundle.manifest);manifest.setProperty("format","2");
        Set<String> derived=new HashSet<>();long removed=0;
        try(ZipFile zip=new ZipFile(source)){
            for(GuestImageComponents.Recipe recipe:components){
                String path="oneos/"+recipe.output;
                if(!derived.add(path)||!recipe.sha256.equals(bundle.files.get(path)))throw new IOException("Recipe differs from authenticated runtime: "+path);
                ZipEntry entry=zip.getEntry("payload/"+path);
                if(entry==null||entry.getSize()!=recipe.bytes)throw new IOException("Recipe length differs: "+path);
                manifest.setProperty("image."+path,recipe.source);manifest.setProperty("bytes."+path,Long.toString(recipe.bytes));removed+=recipe.bytes;
            }
            File parent=output.getAbsoluteFile().getParentFile();if(!parent.isDirectory())throw new IOException("Output parent missing");
            File temporary=File.createTempFile("derived-runtime-",".part",parent);boolean published=false;
            try{
                try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))){
                    out.setLevel(3);ZipEntry meta=new ZipEntry("runtime.properties");meta.setTime(0);out.putNextEntry(meta);manifest.store(out,"Local GSI derivation; existing host guards retained");out.closeEntry();
                    byte[] bytes=new byte[1024*1024];
                    for(String path:bundle.files.keySet()){
                        if(derived.contains(path))continue;
                        ZipEntry entry=new ZipEntry("payload/"+path);entry.setTime(0);out.putNextEntry(entry);
                        try(InputStream in=zip.getInputStream(zip.getEntry("payload/"+path))){int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}out.closeEntry();
                    }
                }
                String hash=RuntimeBundle.hash(temporary);RuntimeBundle verified=new RuntimeBundle(temporary,hash,image);
                if(verified.imageComponents.size()!=components.size())throw new IOException("Derived manifest count differs");
                Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.ATOMIC_MOVE);published=true;
                System.out.println("LOCAL_COMPONENTS="+components.size());System.out.println("DERIVED_UNPACKED_BYTES="+removed);
                System.out.println("PACKAGE_BYTES="+output.length());System.out.println("PACKAGE_SHA256="+hash);
                System.out.println("Host guards retained; candidate package only, not installed or published");
            }finally{if(!published)Files.deleteIfExists(temporary.toPath());}
        }
    }
}
