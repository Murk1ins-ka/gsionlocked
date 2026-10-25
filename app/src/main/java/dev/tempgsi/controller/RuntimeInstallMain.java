package dev.tempgsi.controller;

import android.system.Os;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Prepares files only. Never stops One UI, starts a guest or modifies a partition. */
public final class RuntimeInstallMain {
    private static final File BASE=new File("/data/local/tmp");
    private static void copy(InputStream in,File target)throws IOException {
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot create runtime directory");
        try(FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
    }
    private static File child(File root,String name)throws IOException {RuntimeBundle.safePath(name);File f=new File(root,name);if(!f.getCanonicalPath().startsWith(root.getCanonicalPath()+"/"))throw new IOException("Runtime path escaped");return f;}
    private static void removeOwn(File path)throws IOException {
        if(Files.isSymbolicLink(path.toPath())){Files.delete(path.toPath());return;}
        if(path.isDirectory()){File[] files=path.listFiles();if(files==null)throw new IOException("Cannot enumerate installer staging");for(File f:files)removeOwn(f);}
        Files.deleteIfExists(path.toPath());
    }
    public static void main(String[] args){try{install(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
    static void install(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0||args.length!=4)throw new IOException("Root installer and four arguments required");
        DeviceProfile.requireHost();
        if(!"1".equals(query("getprop","sys.boot_completed")))throw new IOException("Host is not ready");
        String hostPid=query("pidof","system_server");if(!hostPid.matches("[0-9]+"))throw new IOException("Expected one host SystemServer");
        File lockPath=new File(BASE,"gsionlocked-install.lock");if(Files.isSymbolicLink(lockPath.toPath()))throw new IOException("Installer lock is a symlink");
        try(RandomAccessFile lockFile=new RandomAccessFile(lockPath,"rw");FileLock lock=lockFile.getChannel().tryLock()){
            if(lock==null)throw new IOException("Another runtime installation is active");Os.chmod(lockPath.getPath(),0600);
            File stage=Files.createTempDirectory(BASE.toPath(),"gsionlocked-install-").toFile();Os.chmod(stage.getPath(),0700);
            try{
                File archive=new File(stage,"bundle.zip");try(InputStream in=new FileInputStream(args[0])){copy(in,archive);}
                System.out.println("RUNTIME_VERIFY_PACKAGE");RuntimeBundle bundle=new RuntimeBundle(archive,args[1],args[3]);
                if(!RuntimeBundle.hash(new File("/system/framework/services.jar")).equals(bundle.manifest.getProperty("host.services.sha256")))throw new IOException("This runtime does not match the host framework. A port for this firmware is required.");
                for(String source:bundle.hostCopies.values())if(!new File(source).isFile())throw new IOException("Required host component missing: "+source);
                for(String root:bundle.roots)if(new File(BASE,root).exists()||Files.isSymbolicLink(new File(BASE,root).toPath()))throw new IOException("Runtime target already exists: "+root+". Existing data is preserved.");
                File image=new File(args[2]);long hostBytes=0;for(String source:bundle.hostCopies.values())hostBytes+=new File(source).length();long required=image.length()+bundle.payloadBytes+hostBytes+1073741824L;
                if(new android.os.StatFs(BASE.getPath()).getAvailableBytes()<required)throw new IOException("Not enough space for runtime staging, image and 1 GiB reserve");
                RuntimeFiles.install(bundle,archive,image,new File(stage,"payload"),BASE,new RuntimeFiles.Platform(){
                    public File hostFile(String path){return new File(path);}
                    public void mode(File path,int mode)throws Exception{Os.chmod(path.getPath(),mode);}
                    public void symlink(String target,File path)throws Exception{Os.symlink(target,path.getPath());}
                },System.out::println);
                System.out.println("RUNTIME_INSTALLED="+bundle.system);System.out.println("Guest not started. Host services unchanged.");
            }finally{removeOwn(stage);}
        }
    }
    private static void makeDirectoriesReadable(File dir)throws Exception {Os.chmod(dir.getPath(),0755);File[] files=dir.listFiles();if(files==null)throw new IOException("Cannot enumerate staged runtime");for(File f:files)if(!Files.isSymbolicLink(f.toPath())&&f.isDirectory())makeDirectoriesReadable(f);}
    private static String query(String... command)throws Exception {
        ArrayList<String> argv=new ArrayList<>();argv.add("/system/bin/"+command[0]);for(int i=1;i<command.length;i++)argv.add(command[i]);Process p=new ProcessBuilder(argv).redirectErrorStream(true).start();
        ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=p.getInputStream()){byte[] block=new byte[1024];int n;while((n=in.read(block))!=-1){if(b.size()+n>8192)throw new IOException("Unexpected host query output");b.write(block,0,n);}}
        if(p.waitFor()!=0)throw new IOException("Host query failed: "+command[0]);return b.toString("UTF-8").trim();
    }
}
