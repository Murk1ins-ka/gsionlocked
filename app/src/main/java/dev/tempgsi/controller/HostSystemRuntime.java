package dev.tempgsi.controller;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Builds a host-specific capsule from immutable system files, never host /data. */
public final class HostSystemRuntime {
    public interface Sources {
        File file(String absoluteSystemPath)throws Exception;
        void mode(File file,int mode)throws Exception;
    }
    static final String[] LIBRARIES={"libbase.so","libbinder.so","libbinder_ndk.so","libutils.so","libcutils.so","liblog.so","libc++.so"};
    static final Set<String> FACTS=new HashSet<>(Arrays.asList("manufacturer","brand","model","device","soc","sdk","vendor.api","kernel","composer.service","composer.interface","composer.executable","composer.rc","composer.rc.sha256","composer.discovery"));
    static void store(File file,Properties values)throws IOException {
        try(FileOutputStream out=new FileOutputStream(file)){values.store(out,"Generated from this host's system; contains no user data");out.getFD().sync();}
    }
    static void copy(File source,File target)throws IOException {
        if(!source.isFile())throw new IOException("System input missing: "+source.getName());
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot create local host runtime directory");
        String before=RuntimeBundle.hash(source);
        try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(target)){
            byte[] bytes=new byte[1024*1024];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);out.getFD().sync();
        }
        if(!RuntimeBundle.hash(target).equals(before)||!RuntimeBundle.hash(source).equals(before))throw new IOException("System input changed during preparation");
    }
    public static Properties capture(File destination,Sources sources,Properties facts)throws Exception {
        if(destination.exists()||Files.isSymbolicLink(destination.toPath()))throw new IOException("Existing local host runtime is preserved");
        if(!facts.getProperty("manufacturer","").equalsIgnoreCase("samsung")||!facts.getProperty("brand","").equalsIgnoreCase("samsung"))throw new IOException("Samsung host required");
        if(!destination.mkdir())throw new IOException("Cannot create local host runtime");sources.mode(destination,0700);
        Properties manifest=new Properties();manifest.setProperty("format","1");manifest.setProperty("purpose","host-inputs-not-a-launch-permission");
        for(String key:facts.stringPropertyNames()){
            if(!FACTS.contains(key))throw new IOException("Unexpected host fact (private data is not accepted)");
            String value=facts.getProperty(key);if(value.length()>256||value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IOException("Invalid host fact");
            manifest.setProperty("host."+key,value);
        }
        for(String name:new String[]{"services.jar","framework.jar"}){
            File output=new File(destination,name);copy(sources.file("/system/framework/"+name),output);sources.mode(output,0444);
            manifest.setProperty("sha."+name,RuntimeBundle.hash(output));manifest.setProperty("source."+name,"/system/framework/"+name);
        }
        // Boot-class-path definitions win over a duplicate system-server copy.
        // A malformed/stripped framework must NOT silently become a fallback.
        SortedMap<String,DexBinderTable.Entry> table;String abiSource="framework.jar";
        try{table=DexBinderTable.read(new File(destination,abiSource),"Landroid/os/IInstalld;");}
        catch(DexBinderTable.AbsentInterface absent){abiSource="services.jar";table=DexBinderTable.read(new File(destination,abiSource),"Landroid/os/IInstalld;");}
        manifest.setProperty("installd.source",abiSource);
        Properties mapping=new Properties();mapping.setProperty("format","1");mapping.setProperty("host.services.sha256",manifest.getProperty("sha.services.jar"));mapping.setProperty("host.framework.sha256",manifest.getProperty("sha.framework.jar"));
        StringBuilder text=new StringBuilder("# Extracted from this phone's "+abiSource+"; not a static device table\n");
        for(Map.Entry<String,DexBinderTable.Entry> e:table.entrySet()){
            text.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
            mapping.setProperty("method."+e.getKey()+"|"+e.getValue().signature,Integer.toString(e.getValue().code));
        }
        store(new File(destination,"installd.properties"),mapping);sources.mode(new File(destination,"installd.properties"),0444);
        try(FileOutputStream out=new FileOutputStream(new File(destination,"installd.table"))){out.write(text.toString().getBytes("UTF-8"));out.getFD().sync();}
        sources.mode(new File(destination,"installd.table"),0444);
        manifest.setProperty("installd.methods",Integer.toString(table.size()));
        for(String name:LIBRARIES){
            File source=sources.file("/system/lib64/"+name);
            if(!source.isFile()){manifest.setProperty("missing."+name,"true");continue;}
            File output=new File(destination,"lib64/"+name);copy(source,output);sources.mode(output,0444);
            manifest.setProperty("sha.lib64/"+name,RuntimeBundle.hash(output));manifest.setProperty("source.lib64/"+name,"/system/lib64/"+name);
        }
        File libDir=new File(destination,"lib64");if(libDir.exists())sources.mode(libDir,0700);
        for(String name:new String[]{"installd.properties","installd.table"})manifest.setProperty("sha."+name,RuntimeBundle.hash(new File(destination,name)));
        store(new File(destination,"host-runtime.properties"),manifest);sources.mode(new File(destination,"host-runtime.properties"),0444);
        verify(destination);return manifest;
    }
    public static Properties verify(File capsule)throws Exception {
        if(Files.isSymbolicLink(capsule.toPath())||!capsule.isDirectory())throw new IOException("Invalid host capsule directory");
        File marker=new File(capsule,"host-runtime.properties");if(Files.isSymbolicLink(marker.toPath())||!marker.isFile()||marker.length()>128*1024)throw new IOException("Invalid local host runtime marker");
        Properties m=new Properties();try(InputStream in=new FileInputStream(marker)){m.load(in);}
        if(!"1".equals(m.getProperty("format"))||!"host-inputs-not-a-launch-permission".equals(m.getProperty("purpose")))throw new IOException("Unknown host runtime format");
        for(String name:new String[]{"services.jar","framework.jar","installd.properties","installd.table"})if(!m.containsKey("sha."+name))throw new IOException("Incomplete local host runtime");
        for(String key:m.stringPropertyNames())if(key.startsWith("sha.")){
            String name=key.substring(4);if(!name.matches("(?:services\\.jar|framework\\.jar|installd\\.(?:properties|table)|lib64/[A-Za-z0-9_+.]+\\.so)"))throw new IOException("Unexpected local host runtime path");
            File f=new File(capsule,name);if(Files.isSymbolicLink(f.toPath())||!f.getCanonicalFile().toPath().startsWith(capsule.getCanonicalFile().toPath())||!m.getProperty(key).matches("[a-f0-9]{64}")||!RuntimeBundle.hash(f).equals(m.getProperty(key)))throw new IOException("Local host runtime file changed: "+name);
        }
        return m;
    }
    private HostSystemRuntime(){}
}
