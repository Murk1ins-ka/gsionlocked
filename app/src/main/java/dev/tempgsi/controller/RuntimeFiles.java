package dev.tempgsi.controller;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** File transaction shared by Android installer and offline filesystem tests. */
public final class RuntimeFiles {
    public interface Platform {
        File hostFile(String path)throws Exception;
        void mode(File path,int value)throws Exception;
        void symlink(String target,File path)throws Exception;
    }
    static File child(File parent,String name)throws IOException {
        RuntimeBundle.safePath(name);File f=new File(parent,name);if(!f.getCanonicalFile().toPath().startsWith(parent.getCanonicalFile().toPath()))throw new IOException("Runtime path escaped");return f;
    }
    static void copy(InputStream in,File target)throws IOException {
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot create runtime directory");
        try(FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
    }
    static void removeOwn(File path)throws IOException {
        if(Files.isSymbolicLink(path.toPath())){Files.delete(path.toPath());return;}
        if(path.isDirectory()){File[] files=path.listFiles();if(files==null)throw new IOException("Cannot enumerate staging");for(File f:files)removeOwn(f);}
        Files.deleteIfExists(path.toPath());
    }
    static void directoriesReadable(File dir,Platform platform)throws Exception {
        platform.mode(dir,0755);File[] files=dir.listFiles();if(files==null)throw new IOException("Cannot enumerate staged runtime");for(File f:files)if(!Files.isSymbolicLink(f.toPath())&&f.isDirectory())directoriesReadable(f,platform);
    }
    public static void install(RuntimeBundle bundle,File archive,File image,File staging,File destination,Platform platform,Consumer<String> progress)throws Exception {
        if(staging.exists())throw new IOException("Staging already exists");
        for(String root:bundle.roots){File f=new File(destination,root);if(f.exists()||Files.isSymbolicLink(f.toPath()))throw new IOException("Runtime target already exists: "+root+". Existing data is preserved.");}
        File marker=new File(destination,"gsionlocked-profile.properties");
        if(bundle.system.equals("evox")&&(marker.exists()||Files.isSymbolicLink(marker.toPath())))throw new IOException("Existing launch profile is preserved");
        if(!staging.mkdir())throw new IOException("Cannot create payload staging");
        for(String name:bundle.directories)if(!child(staging,name).mkdirs()&&!child(staging,name).isDirectory())throw new IOException("Cannot create runtime directory");
        progress.accept("RUNTIME_EXTRACT_FILES");
        try(ZipFile zip=new ZipFile(archive)){
            for(String name:bundle.files.keySet()){
                if(bundle.imageComponents.containsKey(name))continue;
                File out=child(staging,name);try(InputStream in=zip.getInputStream(zip.getEntry("payload/"+name))){copy(in,out);}
                if(!RuntimeBundle.hash(out).equals(bundle.files.get(name)))throw new IOException("Extracted runtime hash differs");
                int mode=Integer.parseInt(bundle.manifest.getProperty("mode."+name,"644"),8);if(mode!=0644&&mode!=0755&&mode!=0600)throw new IOException("Invalid payload mode");platform.mode(out,mode);
            }
        }
        for(Map.Entry<String,String> item:bundle.hostCopies.entrySet()){File out=child(staging,item.getKey());try(InputStream in=new FileInputStream(platform.hostFile(item.getValue()))){copy(in,out);}platform.mode(out,0644);}
        for(Map.Entry<String,String> item:bundle.links.entrySet()){File out=child(staging,item.getKey());if(!out.getParentFile().isDirectory()&&!out.getParentFile().mkdirs())throw new IOException("Cannot create link directory");platform.symlink(item.getValue(),out);}
        String relative=bundle.system.equals("coloros")?"oneos/system.raw":"tempgsi-evox154/system.img";
        File imageOut=child(staging,relative);if(imageOut.exists()||Files.isSymbolicLink(imageOut.toPath()))throw new IOException("Runtime must not contain a GSI image");
        progress.accept("RUNTIME_COPY_AND_VERIFY_GSI");try(InputStream in=new FileInputStream(image)){copy(in,imageOut);}
        if(!RuntimeBundle.hash(imageOut).equals(bundle.manifest.getProperty("gsi.sha256")))throw new IOException("Selected GSI changed or does not match");platform.mode(imageOut,0444);
        if(!bundle.imageComponents.isEmpty()){
            progress.accept("RUNTIME_DERIVE_COMPONENTS_FROM_GSI");
            try(ReadOnlyExt4 selected=new ReadOnlyExt4(imageOut)){
                for(Map.Entry<String,String> item:bundle.imageComponents.entrySet()){
                    File target=child(staging,item.getKey()),parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create derived runtime directory");
                    GuestImageComponents.Recipe recipe=new GuestImageComponents.Recipe(target.getName(),item.getValue(),bundle.files.get(item.getKey()),bundle.componentBytes.get(item.getKey()));
                    progress.accept("Deriving "+target.getName()+" from verified GSI");
                    GuestImageComponents.extractOne(selected,parent,recipe,()->false);
                    platform.mode(target,0644);
                }
            }
        }
        for(String root:bundle.roots){File f=child(staging,root);if(Files.isSymbolicLink(f.toPath())||(!f.isDirectory()&&!f.isFile()))throw new IOException("Invalid runtime root");if(f.isDirectory())directoriesReadable(f,platform);}
        File stagedProfile=null;
        if(bundle.system.equals("evox")){
            String value=bundle.manifest.getProperty("profile.contents","");if(!value.startsWith("directory=/data/local/tmp/tempgsi-gsionlocked198\n"))throw new IOException("Invalid launch profile");
            Properties parsed=new Properties();parsed.load(new StringReader(value));RuntimeProfile.requireInstallFixes(parsed);
            stagedProfile=new File(staging,"launch-profile.properties");try(InputStream in=new ByteArrayInputStream(value.getBytes("UTF-8"))){copy(in,stagedProfile);}platform.mode(stagedProfile,0600);
        }
        ArrayList<File> published=new ArrayList<>();
        try{
            progress.accept("RUNTIME_PUBLISH_VERIFIED_FILES");
            for(String root:bundle.roots){File to=new File(destination,root);if(to.exists()||Files.isSymbolicLink(to.toPath()))throw new IOException("Runtime target appeared during installation");Files.move(child(staging,root).toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);published.add(to);progress.accept("RUNTIME_ROOT_READY="+root);}
            if(stagedProfile!=null){if(marker.exists()||Files.isSymbolicLink(marker.toPath()))throw new IOException("Launch profile appeared during installation");Files.move(stagedProfile.toPath(),marker.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        }catch(Throwable failure){for(File target:published)removeOwn(target);throw failure;}
    }
    private RuntimeFiles(){}
}
