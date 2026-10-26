package dev.tempgsi.controller;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;

/** Extracts authenticated APEX payloads directly from a selected raw GSI.
 * Neither the image nor an APEX is mounted; ZIP names never become host paths. */
public final class GuestImageComponents {
    public static final class Recipe {
        public final String output,source,sha256;public final long bytes;
        public Recipe(String output,String source,String sha256,long bytes)throws IOException {
            if(!output.matches("[A-Za-z0-9_.-]+_payload\\.img")||!source.matches("/(?:system/)?apex/[A-Za-z0-9_.+-]+\\.(?:apex|capex)")||!sha256.matches("[a-f0-9]{64}")||bytes<4096||bytes>2L*1024*1024*1024)throw new IOException("Invalid image component recipe");
            this.output=output;this.source=source;this.sha256=sha256;this.bytes=bytes;
        }
    }
    private static final class NoClose extends FilterInputStream {
        NoClose(InputStream input){super(input);}
        @Override public void close(){}
    }
    private static final class Limited extends FilterInputStream {
        final long limit;long count;
        Limited(InputStream input,long limit){super(input);this.limit=limit;}
        @Override public int read()throws IOException {byte[] one=new byte[1];return read(one,0,1)==-1?-1:one[0]&255;}
        @Override public int read(byte[] out,int offset,int size)throws IOException {
            int n=in.read(out,offset,size);if(n>0){count+=n;if(count>limit)throw new IOException("APEX entry exceeds extraction limit");}return n;
        }
        @Override public long skip(long n)throws IOException {
            byte[] bytes=new byte[(int)Math.min(Math.max(n,1),65536)];long total=0;int got;
            while(total<n&&(got=read(bytes,0,(int)Math.min(bytes.length,n-total)))!=-1)total+=got;return total;
        }
        @Override public void close(){}
    }
    private static void drain(InputStream input,BooleanSupplier cancelled)throws IOException {
        byte[] bytes=new byte[65536];while(input.read(bytes)!=-1)if(cancelled.getAsBoolean())throw new IOException("Image component extraction cancelled");
    }
    private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private static boolean payload(InputStream input,File output,Recipe recipe,int depth,BooleanSupplier cancelled)throws IOException {
        if(depth>1)throw new IOException("Unexpected nested compressed APEX");
        boolean found=false;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(new NoClose(input))){
            ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                if(++entries>256)throw new IOException("Too many APEX ZIP entries");
                String name=entry.getName();long limit=(name.equals("apex_payload.img")?recipe.bytes:name.equals("original_apex")?recipe.bytes+64L*1024*1024:16L*1024*1024);
                if(entry.getSize()>limit)throw new IOException("APEX ZIP entry size exceeds recipe limit");
                Limited content=new Limited(zip,limit);
                if(name.equals("apex_payload.img")){
                    if(found||entry.isDirectory())throw new IOException("Duplicate/invalid APEX payload");
                    MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
                    long total=0;try(FileOutputStream out=new FileOutputStream(output)){
                        byte[] bytes=new byte[1024*1024];int n;while((n=content.read(bytes))!=-1){
                            if(cancelled.getAsBoolean())throw new IOException("Image component extraction cancelled");
                            total+=n;digest.update(bytes,0,n);out.write(bytes,0,n);
                        }out.getFD().sync();
                    }
                    if(total!=recipe.bytes||!hex(digest.digest()).equals(recipe.sha256))throw new IOException("Extracted APEX payload differs: "+recipe.output);
                    found=true;
                }else if(name.equals("original_apex")){
                    if(found||entry.isDirectory())throw new IOException("Duplicate/invalid compressed APEX");
                    found=payload(content,output,recipe,depth+1,cancelled);
                    // Consume central-directory/trailing bytes through the outer entry limit.
                    drain(content,cancelled);
                }else drain(content,cancelled);
                zip.closeEntry();
            }
        }return found;
    }
    public static void extractOne(ReadOnlyExt4 image,File directory,Recipe recipe,BooleanSupplier cancelled)throws IOException {
        File target=new File(directory,recipe.output);
        if(!directory.isDirectory()||Files.isSymbolicLink(directory.toPath()))throw new IOException("Component output directory is invalid");
        if(target.exists()||Files.isSymbolicLink(target.toPath()))throw new IOException("Existing image component is preserved");
        ReadOnlyExt4.Node source=image.lookup(recipe.source);if(!source.regular()||source.size>recipe.bytes+64L*1024*1024)throw new IOException("Invalid source APEX inode");
        File temporary=File.createTempFile("component-",".part",directory);boolean published=false;
        try{
            try(InputStream in=source.open()){if(!payload(in,temporary,recipe,0,cancelled))throw new IOException("APEX has no payload: "+recipe.source);}
            if(cancelled.getAsBoolean())throw new IOException("Image component extraction cancelled");
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);published=true;
        }finally{if(!published)Files.deleteIfExists(temporary.toPath());}
    }
    public static void extract(File selectedImage,File destination,List<Recipe> recipes,Consumer<String> progress,BooleanSupplier cancelled)throws IOException {
        if(destination.exists()||Files.isSymbolicLink(destination.toPath()))throw new IOException("Existing component directory is preserved");
        if(recipes.isEmpty())throw new IOException("No image components declared");Set<String> outputs=new HashSet<>();long total=0;
        for(Recipe recipe:recipes){if(!outputs.add(recipe.output))throw new IOException("Duplicate derived component");total+=recipe.bytes;}
        if(destination.getParentFile().getUsableSpace()<total+64L*1024*1024)throw new IOException("Not enough space to derive image components");
        if(!destination.mkdir())throw new IOException("Cannot create image component staging");
        boolean complete=false;
        try(ReadOnlyExt4 image=new ReadOnlyExt4(selectedImage)){
            int count=0;for(Recipe recipe:recipes){progress.accept("Deriving "+recipe.output+" from selected GSI ("+(++count)+"/"+recipes.size()+")");extractOne(image,destination,recipe,cancelled);}
            complete=true;
        }finally{if(!complete)removeOwn(destination);}
    }
    private static void removeOwn(File directory)throws IOException {
        // Only this freshly created flat directory belongs to the extractor.
        File[] files=directory.listFiles();if(files==null)throw new IOException("Cannot enumerate own component staging");
        for(File file:files){if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath()))throw new IOException("Unexpected directory in own component staging");Files.deleteIfExists(file.toPath());}
        Files.deleteIfExists(directory.toPath());
    }
    public static List<Recipe> readRecipes(File properties)throws IOException {
        Properties p=new Properties();try(InputStream in=new FileInputStream(properties)){p.load(in);}
        if(!"1".equals(p.getProperty("format")))throw new IOException("Unknown GSI component recipe format");
        ArrayList<Recipe> result=new ArrayList<>();
        for(String output:p.getProperty("outputs","").split(",")){
            long bytes;try{bytes=Long.parseLong(p.getProperty("bytes."+output,""));}catch(NumberFormatException e){throw new IOException("Invalid component size",e);}
            result.add(new Recipe(output,p.getProperty("source."+output,""),p.getProperty("sha."+output,""),bytes));
        }return result;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IOException("Usage: GuestImageComponents image.img recipes.properties new-output-directory");
        extract(new File(args[0]),new File(args[2]),readRecipes(new File(args[1])),System.out::println,()->false);
        System.out.println("GSI_COMPONENTS_DERIVED=1");
    }
    private GuestImageComponents(){}
}
