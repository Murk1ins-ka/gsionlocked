package dev.tempgsi.controller;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Authenticated, data-free runtime package. No arbitrary installer scripts. */
public final class RuntimeBundle {
    public final Properties manifest=new Properties();
    public final SortedMap<String,String> files=new TreeMap<>(),links=new TreeMap<>(),hostCopies=new TreeMap<>();
    public final SortedMap<String,String> imageComponents=new TreeMap<>();
    public final SortedMap<String,Long> componentBytes=new TreeMap<>();
    public final Set<String> roots=new LinkedHashSet<>();
    public final Set<String> directories=new LinkedHashSet<>();
    public long payloadBytes;
    public final String system;
    public static String hash(InputStream in)throws IOException {
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)d.update(b,0,n);StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    public static String hash(File f)throws IOException {try(InputStream in=new FileInputStream(f)){return hash(in);}}
    static void safePath(String name)throws IOException {
        if(!name.matches("[A-Za-z0-9_@+.,/-]+")||name.startsWith("/")||name.endsWith("/")||name.contains("//"))throw new IOException("Invalid runtime path: "+name);
        for(String part:name.split("/"))if(part.equals(".")||part.equals(".."))throw new IOException("Runtime path escapes its directory");
    }
    private void owned(String path)throws IOException {
        safePath(path);String root=path.split("/",2)[0];if(!roots.contains(root))throw new IOException("Runtime path is outside declared roots: "+path);
        if(path.matches(".*(^|/)(data|userdata|efs|accounts|keystore|user_de|user)(/.*)?"))throw new IOException("Runtime package contains user/security data");
    }
    public RuntimeBundle(File archive,String expected,String imageSha)throws IOException {
        if(!expected.matches("[a-f0-9]{64}")||!hash(archive).equals(expected))throw new IOException("Runtime package SHA-256 differs");
        try(ZipFile zip=new ZipFile(archive)){
            ZipEntry meta=zip.getEntry("runtime.properties");if(meta==null||meta.getSize()<0||meta.getSize()>4*1024*1024)throw new IOException("Invalid runtime manifest");
            try(InputStream in=zip.getInputStream(meta)){manifest.load(in);}
            String format=manifest.getProperty("format");
            if(!"1".equals(format)&&!"2".equals(format))throw new IOException("Unsupported runtime package format");
            system=manifest.getProperty("system","");if(!system.equals("coloros")&&!system.equals("evox"))throw new IOException("Unknown runtime system");
            if(!imageSha.equals(manifest.getProperty("gsi.sha256")))throw new IOException("Runtime does not match the selected GSI");
            for(String root:manifest.getProperty("roots","").split(",")){
                if(!root.matches("(?:oneos|tempgsi-[A-Za-z0-9_.-]+|gsionlocked-[A-Za-z0-9_.-]+)"))throw new IOException("Unexpected runtime root: "+root);
                if(!roots.add(root))throw new IOException("Duplicate runtime root");
            }
            for(String key:manifest.stringPropertyNames()){
                if(key.startsWith("sha.")){String path=key.substring(4),digest=manifest.getProperty(key);owned(path);if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid runtime digest");files.put(path,digest);}
                else if(key.startsWith("link.")){String path=key.substring(5),target=manifest.getProperty(key);owned(path);if(target.startsWith("/")){if(!target.startsWith("/system/")&&!target.startsWith("/apex/"))throw new IOException("Invalid runtime link target");safePath(target.substring(1));}else safePath(target);links.put(path,target);}
                else if(key.startsWith("host." )&&!key.equals("host.services.sha256")){String path=key.substring(5),source=manifest.getProperty(key);owned(path);if(!source.matches("/(?:system|system_ext|vendor|apex)/[A-Za-z0-9_@+.,/-]+"))throw new IOException("Invalid host component path");safePath(source.substring(1));hostCopies.put(path,source);}
            }
            long derivedBytes=0;
            for(String key:manifest.stringPropertyNames())if(key.startsWith("image.")){
                String path=key.substring(6),source=manifest.getProperty(key);owned(path);
                if(!"2".equals(format)||!files.containsKey(path)||!path.matches(".+/[A-Za-z0-9_.-]+_payload\\.img")||!source.matches("/(?:system/)?apex/[A-Za-z0-9_.+-]+\\.(?:apex|capex)"))throw new IOException("Invalid derived image component");
                long size;try{size=Long.parseLong(manifest.getProperty("bytes."+path,""));}catch(NumberFormatException e){throw new IOException("Invalid derived component size",e);}
                if(size<4096||size>2L*1024*1024*1024)throw new IOException("Invalid derived component size");
                derivedBytes+=size;if(derivedBytes>6L*1024*1024*1024)throw new IOException("Derived runtime too large");
                imageComponents.put(path,source);componentBytes.put(path,size);
            }
            for(String path:manifest.getProperty("directories","").split(","))if(!path.isEmpty()){owned(path);directories.add(path);}
            if(files.size()<10)throw new IOException("Runtime package is incomplete");
            Set<String> paths=new HashSet<>(files.keySet());for(String path:links.keySet())if(!paths.add(path))throw new IOException("Duplicate runtime target");for(String path:hostCopies.keySet())if(!paths.add(path))throw new IOException("Duplicate runtime target");
            for(String path:paths){int slash=path.indexOf('/');while(slash>=0){if(paths.contains(path.substring(0,slash)))throw new IOException("Runtime path has a file/link parent");slash=path.indexOf('/',slash+1);}}
            Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();long total=0;
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();String name=e.getName();if(!seen.add(name)||e.isDirectory())throw new IOException("Unexpected/duplicate ZIP entry");
                if(name.equals("runtime.properties"))continue;
                if(!name.startsWith("payload/")||!files.containsKey(name.substring(8))||imageComponents.containsKey(name.substring(8))||e.getSize()<0)throw new IOException("Undeclared runtime payload: "+name);
                total+=e.getSize();if(total>6L*1024*1024*1024)throw new IOException("Runtime package too large");
                try(InputStream in=zip.getInputStream(e)){if(!hash(in).equals(files.get(name.substring(8))))throw new IOException("Corrupt runtime file: "+name);}
            }
            if(seen.size()!=files.size()-imageComponents.size()+1)throw new IOException("Missing runtime payload entries");payloadBytes=total+derivedBytes;
            if(payloadBytes>6L*1024*1024*1024)throw new IOException("Runtime package too large");
            String framework=manifest.getProperty("host.services.sha256","");if(!framework.matches("[a-f0-9]{64}"))throw new IOException("Missing host ABI contract");
        }
    }
}
