package com.android.server.pm;

import java.io.*;
import java.util.*;
import android.os.RemoteException;

/** Guest dispatch adapter. Generated host codes are data, never loaded host code. */
public final class GsionlockedInstalldMap {
    private static final Set<String> ALLOWED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "createUserData","setFirstBoot","createAppData","createAppDataBatched",
        "restoreconAppData","fixupAppData","getAppSize","getUserSize","getExternalSize",
        "getAppCrates","getUserCrates","controlDexOptBlocking","mergeProfiles","dumpProfiles",
        "copySystemProfile","createProfileSnapshot","linkNativeLibraryDirectory","createOatDirs",
        "linkFile","hashSecondaryDexFile","invalidateMounts","isQuotaSupported","prepareAppProfile",
        "tryMountDataMirror","getOdexVisibility","createFsveritySetupAuthToken","enableFsverity"
    )));
    private static volatile Properties cached;
    private static Properties load()throws IOException {
        Properties p=cached;if(p!=null)return p;
        synchronized(GsionlockedInstalldMap.class){
            if(cached!=null)return cached;
            File f=new File("/data/gsionlocked-host-abi/installd.properties");
            if(!f.isFile()||f.length()>128*1024)throw new IOException("Host ABI mapping is absent or oversized");
            p=new Properties();try(InputStream in=new FileInputStream(f)){p.load(in);}
            if(!"1".equals(p.getProperty("format"))||!p.getProperty("host.services.sha256","").matches("[a-f0-9]{64}"))
                throw new IOException("Invalid host ABI mapping");
            cached=p;return p;
        }
    }
    static int resolve(Properties p,String key)throws RemoteException {
        int split=key==null?-1:key.indexOf('|');
        if(split<1||!ALLOWED.contains(key.substring(0,split)))throw new RemoteException("Installd method is not permitted in a guest");
        String value=p.getProperty("method."+key);
        if(value==null||!value.matches("[1-9][0-9]{0,7}"))throw new RemoteException("No host installd method with the exact guest signature: "+key);
        try{int code=Integer.parseInt(value);if(code<=0||code>0xffffff)throw new NumberFormatException();return code;}
        catch(NumberFormatException e){throw new RemoteException("Invalid host installd transaction code");}
    }
    public static int code(String key)throws RemoteException {
        try{return resolve(load(),key);}catch(IOException e){throw new RemoteException("Host ABI mapping unavailable: "+e.getMessage());}
    }
    private GsionlockedInstalldMap(){}
}
