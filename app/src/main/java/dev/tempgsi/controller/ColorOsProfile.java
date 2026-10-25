package dev.tempgsi.controller;

import java.io.*;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;

/** Separate Android 17 takeover; never routes ColorOS through the EvoX loader. */
final class ColorOsProfile {
    static final String NAME="ColorOS 17 · Find X9 Ultra IN";
    static final String SHA256="a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0";
    static boolean matches(String digest){return SHA256.equals(digest);}
    static boolean guest(){return Build.VERSION.SDK_INT==37;}
    static String command(Context context,String action)throws IOException {
        if(!action.equals("status")&&!action.equals("start")&&!action.equals("start-preserve")&&!action.equals("select"))throw new IOException("Unknown ColorOS action.");
        if(Build.VERSION.SDK_INT!=36||!"SM-S911B".equals(Build.MODEL))throw new IOException("ColorOS requires the prepared SM-S911B / One UI Android 16 host.");
        if(action.startsWith("start")){
            KeyguardManager lock=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
            if(lock.isDeviceSecure()||lock.isDeviceLocked())throw new IOException("Unlock One UI and disable PIN/password/biometrics before this experimental session.");
        }
        File script=new File(context.getFilesDir(),"coloros-session.sh");
        try(InputStream in=context.getAssets().open("coloros-session.sh");FileOutputStream out=new FileOutputStream(script)){
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
        }
        File helper=new File(context.getFilesDir(),"coloros-launch");
        if(action.startsWith("start"))try(InputStream in=context.getAssets().open("session-launch");FileOutputStream out=new FileOutputStream(helper)){
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
        }
        return "sh '"+script.getAbsolutePath().replace("'","'\\''")+"' "+action+" '"+helper.getAbsolutePath().replace("'","'\\''")+"' '"+context.getApplicationInfo().sourceDir.replace("'","'\\''")+"'";
    }
    static void requestReturn(Context context)throws IOException {
        if(!guest())throw new IOException("No ColorOS session detected.");
        try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"oneos_return"))){out.write(new byte[]{97,112,112,10});out.getFD().sync();}
    }
    private ColorOsProfile(){}
}
