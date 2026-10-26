package dev.tempgsi.controller;

import android.os.Build;
import android.system.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;

/** Root entry point: only snapshots immutable system inputs; never starts/stops Android. */
public final class HostPrepareMain {
    private static final File ROOT=new File("/data/local/tmp/gsionlocked-host-runtimes");
    private static File immutableFile(String path)throws Exception{
        if(!path.matches("/system/(?:framework/(?:services|framework)\\.jar|lib64/[A-Za-z0-9_+.]+\\.so)"))throw new IOException("Only fixed system inputs may be copied");
        File source=new File(path);String actual=source.getCanonicalPath();
        if(!actual.startsWith("/system/")&&!actual.startsWith("/apex/"))throw new IOException("System input resolved outside immutable system roots");return source;
    }
    static void safeRootDirectory(File file)throws Exception {
        if(!file.exists()&&!file.mkdir())throw new IOException("Cannot create host runtime directory");
        StructStat stat=Os.lstat(file.getPath());if(stat.st_uid!=0||!OsConstants.S_ISDIR(stat.st_mode)||(stat.st_mode&022)!=0)throw new IOException("Host runtime directory is not private root storage");
        Os.chmod(file.getPath(),0700);
    }
    private static void remove(File file)throws IOException {
        if(Files.isSymbolicLink(file.toPath())){Files.delete(file.toPath());return;}
        if(file.isDirectory()){File[] files=file.listFiles();if(files==null)throw new IOException("Cannot read own staging directory");for(File child:files)remove(child);}
        Files.deleteIfExists(file.toPath());
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
    static void run(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0||args.length!=0)throw new IOException("Root host preparation takes no external paths");
        DeviceProfile.requireSamsungHost();safeRootDirectory(ROOT);
        File lockFile=new File(ROOT,"prepare.lock");
        if(Files.isSymbolicLink(lockFile.toPath()))throw new IOException("Host preparation lock is a symlink");
        try(RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock lock=file.getChannel().tryLock()){
            if(lock==null)throw new IOException("Host preparation already running");Os.chmod(lockFile.getPath(),0600);
            Properties facts=new Properties();facts.setProperty("manufacturer",Build.MANUFACTURER);facts.setProperty("brand",Build.BRAND);
            facts.setProperty("model",Build.MODEL);facts.setProperty("device",Build.DEVICE);facts.setProperty("soc",Build.SOC_MODEL);
            facts.setProperty("sdk",Integer.toString(Build.VERSION.SDK_INT));facts.setProperty("kernel",System.getProperty("os.version","unknown"));
            String services=RuntimeBundle.hash(immutableFile("/system/framework/services.jar")),framework=RuntimeBundle.hash(immutableFile("/system/framework/framework.jar"));
            StringBuilder identity=new StringBuilder("host-v1\n"+Build.VERSION.SDK_INT+"\n"+services+"\n"+framework);Map<String,String> libraries=new TreeMap<>();
            for(String name:HostSystemRuntime.LIBRARIES){File source=immutableFile("/system/lib64/"+name);String sha=source.isFile()?RuntimeBundle.hash(source):"missing";libraries.put(name,sha);identity.append('\n').append(name).append('=').append(sha);}
            String key=RuntimeBundle.hash(new ByteArrayInputStream(identity.toString().getBytes("UTF-8")));
            File destination=new File(ROOT,key);Properties profile;
            if(destination.exists()||Files.isSymbolicLink(destination.toPath())){safeRootDirectory(destination);profile=HostSystemRuntime.verify(destination);}
            else {
                File staging=Files.createTempDirectory(ROOT.toPath(),"prepare-").toFile();Os.chmod(staging.getPath(),0700);
                try{
                    File capsule=new File(staging,"capsule");
                    profile=HostSystemRuntime.capture(capsule,new HostSystemRuntime.Sources(){
                        public File file(String path)throws Exception{return immutableFile(path);}
                        public void mode(File path,int mode)throws Exception{Os.chmod(path.getPath(),mode);}
                    },facts);
                    if(!services.equals(profile.getProperty("sha.services.jar"))||!framework.equals(profile.getProperty("sha.framework.jar")))throw new IOException("Host updated while preparing runtime");
                    for(String name:HostSystemRuntime.LIBRARIES)if(!libraries.get(name).equals(profile.getProperty("sha.lib64/"+name,"missing")))throw new IOException("Host libraries updated while preparing runtime");
                    Files.move(capsule.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
                }finally{remove(staging);}
            }
            System.out.println("HOST_INSTALLD_METHODS="+profile.getProperty("installd.methods"));
            System.out.println("HOST_MAP_SHA256="+profile.getProperty("sha.installd.properties"));
            System.out.println("HOST_PREPARED="+destination.getPath());
            System.out.println("System-only host inputs copied to private staging. No account, key, user app data or system partition was changed.");
        }
    }
}
