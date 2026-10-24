package dev.tempgsi.controller;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import android.system.Os;
import android.system.StructStat;

/** Root-owned prepared profile; no fallback to an older, incompatible system. */
public final class SessionMain {
    public static final boolean UNBOUNDED_MODE_AVAILABLE=true;
    private static String sha(File file)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] data=new byte[65536];int n;while((n=in.read(data))!=-1)d.update(data,0,n);}
        StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format("%02x",b&255));return out.toString();
    }
    public static void main(String[] args){
        try{run(args);}catch(Throwable error){error.printStackTrace(System.out);System.out.println("GSIONLOCKED_SESSION_EXIT=1");System.exit(1);}
    }
    private static void run(String[] args)throws Exception{
        if(android.os.Process.myUid()!=0||args.length!=4||!args[2].equals("--unlimited"))throw new SecurityException("Detached root launcher required");
        DeviceProfile.requireHost();
        if(!DeviceProfile.SHA256.equals(args[3]))throw new IOException("Selected GSI does not match this runtime profile.");
        int mib=Integer.parseInt(args[1]);if(mib<512)throw new IOException("Invalid data size");
        System.out.println("Reading /data/local/tmp/gsionlocked-profile.properties");
        File marker=new File("/data/local/tmp/gsionlocked-profile.properties");StructStat st=Os.lstat(marker.getPath());
        if(st.st_uid!=0||(st.st_mode&077)!=0||!android.system.OsConstants.S_ISREG(st.st_mode))throw new IOException("Prepared profile ownership differs");
        long allocated=0;File data=new File("/data/local/tmp/tempgsi-evox154/guest-data-v1.properties");
        if(data.isFile()){Properties previous=new Properties();try(InputStream in=new FileInputStream(data)){previous.load(in);}allocated=Long.parseLong(previous.getProperty("bytes","0"));if(allocated<0)throw new IOException("Invalid storage metadata");}
        long requested=mib*1048576L,free=new android.os.StatFs("/data").getAvailableBytes();
        System.out.println("Storage preflight: requested="+requested+" allocated="+allocated+" free="+free);
        if(requested>allocated&&free<requested+1073741824L)throw new IOException("Insufficient storage plus 1 GiB reserve");
        Properties p=new Properties();try(InputStream in=new FileInputStream(marker)){p.load(in);}
        String dir=p.getProperty("directory",""),entry=p.getProperty("entry",""),jar=p.getProperty("jar","");
        if(!dir.matches("/data/local/tmp/tempgsi-[a-z]+[0-9]+")||!entry.matches("EvoxWork[0-9]+")||!jar.matches("work[0-9]+\\.jar"))throw new IOException("Invalid prepared profile");
        int revision=RuntimeProfile.requireInstallFixes(p);
        int count=0;for(String key:p.stringPropertyNames())if(key.startsWith("sha.")){
            String name=key.substring(4),digest=p.getProperty(key);if(!name.matches("[A-Za-z0-9._-]+")||!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid profile hash");
            File file=new File(dir,name);if(!file.isFile()||!sha(file).equals(digest))throw new IOException("Prepared file differs: "+name);count++;System.out.println("SHA-256 OK: "+name);
        }
        if(count<10||!p.containsKey("sha."+jar))throw new IOException("Prepared profile incomplete");
        System.out.println("Runtime "+revision+" verified: installer storage views, ART lifetime and download UID lease fixes enabled.");
        ProcessBuilder launch=new ProcessBuilder("/system/bin/app_process","-Xms16m","-Xmx64m","/system/bin",entry,"--stay-in-lineage-mobile145","--data-mib",Integer.toString(mib),"--controller-apk",args[0]);
        launch.environment().put("CLASSPATH",dir+"/"+jar);
        System.out.println("CLASSPATH="+dir+"/"+jar);System.out.println("$ "+String.join(" ",launch.command()));
        int result=launch.inheritIO().start().waitFor();System.out.println("GSIONLOCKED_SESSION_EXIT="+result);System.exit(result);
    }
}
