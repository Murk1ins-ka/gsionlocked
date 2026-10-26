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
    private static String serviceState(String name)throws IOException {
        return query("/system/bin/getprop","init.svc."+name).trim();
    }
    private static String query(String... command)throws IOException {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream result=new ByteArrayOutputStream();IOException[] failure=new IOException[1];
        Thread reader=new Thread(()->{
            try(InputStream in=process.getInputStream()){
                byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(result.size()+n>1024*1024)throw new IOException("System discovery output too large");result.write(bytes,0,n);}
            }catch(IOException e){failure[0]=e;process.destroy();}
        },"host-service-query");reader.setDaemon(true);reader.start();
        try{
            try{
                if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("System discovery query timed out");
                reader.join(1000);if(reader.isAlive())throw new IOException("System discovery output timed out");
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("System discovery interrupted",e);}
            if(failure[0]!=null)throw failure[0];if(process.exitValue()!=0)throw new IOException("System discovery query failed");
            return result.toString("UTF-8");
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
    private static void composerFacts(Properties facts)throws Exception {
        ArrayList<HostComposer.Service> services=new ArrayList<>();int count=0;long total=0;
        for(String root:new String[]{"/system","/system_ext","/vendor","/odm","/product"}){
            File directory=new File(root+"/etc/init");if(!directory.isDirectory())continue;
            File[] files=directory.listFiles();if(files==null)throw new IOException("Cannot read system init directory");
            Arrays.sort(files,Comparator.comparing(File::getName));
            for(File file:files){
                if(!file.getName().endsWith(".rc")||!file.isFile())continue;
                String resolved=file.getCanonicalPath();
                if(!resolved.matches("/(?:system|system_ext|vendor|odm|product)/.*"))throw new IOException("Init input outside system roots");
                long size=file.length();total+=size;
                if(++count>4096||size>2*1024*1024||total>32*1024*1024)throw new IOException("System init input exceeds discovery limits");
                byte[] bytes=Files.readAllBytes(file.toPath());if(bytes.length!=size)throw new IOException("Init file changed during discovery");
                services.addAll(HostComposer.parse(file.getPath(),new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
            }
        }
        HostComposer.Service selected;String discovery="declared-running";
        try{selected=HostComposer.select(services,HostPrepareMain::serviceState);}
        catch(HostComposer.NoDeclaredComposer unavailable){
            SortedMap<String,String> registry=HostComposer.hidlRegistry(query("/system/bin/lshal","-i","-p"));
            selected=HostComposer.selectRegistered(services,new HostComposer.Running(){
                public String service(String name)throws IOException{return serviceState(name);}
                public String pid(String name)throws IOException{return query("/system/bin/getprop","init.svc_debug_pid."+name).trim();}
                public String executable(String pid)throws IOException{return new File("/proc/"+pid+"/exe").getCanonicalPath();}
            },registry);discovery="registered-pid-executable";
        }
        File executable=new File(selected.executable);
        if(!executable.isFile()||!executable.getCanonicalPath().matches("/(?:system|system_ext|vendor|odm)/.*"))throw new IOException("Declared display composer executable missing or outside system roots");
        facts.setProperty("composer.service",selected.name);facts.setProperty("composer.interface",selected.composerInterface());
        facts.setProperty("composer.executable",selected.executable);facts.setProperty("composer.rc",selected.source);facts.setProperty("composer.rc.sha256",selected.sourceSha);
        facts.setProperty("composer.discovery",discovery);
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
            try{composerFacts(facts);}catch(IOException unsupported){
                // Input capture remains useful, but the common launch candidate requires discovery.
                for(String name:new ArrayList<>(facts.stringPropertyNames()))if(name.startsWith("composer."))facts.remove(name);
                facts.setProperty("composer.discovery","unresolved");
                System.out.println("HOST_COMPOSER_UNRESOLVED=1");
                System.out.println("HOST_COMPOSER_DIAGNOSTIC="+unsupported.getMessage().replace('\n',' ').replace('\r',' '));
            }
            String services=RuntimeBundle.hash(immutableFile("/system/framework/services.jar")),framework=RuntimeBundle.hash(immutableFile("/system/framework/framework.jar"));
            StringBuilder identity=new StringBuilder("host-v2\n"+Build.VERSION.SDK_INT+"\n"+services+"\n"+framework);Map<String,String> libraries=new TreeMap<>();
            for(String name:new TreeSet<>(facts.stringPropertyNames()))identity.append('\n').append(name).append('=').append(facts.getProperty(name));
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
            if(!"unresolved".equals(profile.getProperty("host.composer.discovery"))){
                System.out.println("HOST_COMPOSER_SERVICE="+profile.getProperty("host.composer.service"));
                System.out.println("HOST_COMPOSER_INTERFACE="+profile.getProperty("host.composer.interface"));
            }
            System.out.println("HOST_PREPARED="+destination.getPath());
            System.out.println("System-only host inputs copied to private staging. No account, key, user app data or system partition was changed.");
        }
    }
}
