package dev.tempgsi.controller;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.function.*;
import org.json.JSONObject;

/** Downloads only the immutable package pinned in this APK's catalog. */
final class RuntimeInstaller {
    private static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
    static String install(Context context,String imageSha,Consumer<String> progress,BooleanSupplier cancelled)throws Exception {
        DeviceProfile.requireHost();
        String system=ColorOsProfile.matches(imageSha)?"coloros":DeviceProfile.SHA256.equals(imageSha)?"evox":null;
        if(system==null)throw new IOException("Select the supported ColorOS or Evolution X .img first.");
        File image=new File(context.getFilesDir(),"gsi/selected.img");
        if(!image.isFile())throw new IOException("Use Select GSI → Browse and import your unpacked .img before installing its runtime.");
        progress.accept("Checking imported GSI SHA-256…");
        if(!RuntimeBundle.hash(image).equals(imageSha))throw new IOException("Imported image does not match the selected system. Import the correct .img again.");
        JSONObject catalog;try(InputStream in=context.getAssets().open("runtime-catalog.json")){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)b.write(bytes,0,n);catalog=new JSONObject(b.toString("UTF-8"));}
        JSONObject item=catalog.getJSONObject(system);String expected=item.getString("sha256");long length=item.getLong("bytes");
        progress.accept("Checking root and host framework before downloading…");
        String host=RootCommands.run(context,"id -u; sha256sum /system/framework/services.jar");
        if(!host.trim().startsWith("0\n")||!host.contains(item.getString("host_framework_sha256")+" "))throw new IOException("No runtime package matches this host framework. A device-specific port is required; no download or installation started.");
        if(system.equals("coloros"))RootCommands.run(context,"test -x /data/adb/ksu/bin/busybox && test -x /data/adb/ksu/bin/resetprop || { echo 'ColorOS preparation requires KernelSU busybox/resetprop'; exit 2; }");
        File folder=new File(context.getFilesDir(),"runtime-download");if(!folder.isDirectory()&&!folder.mkdir())throw new IOException("Cannot create runtime download directory");
        File archive=new File(folder,system+".zip");
        if(!archive.isFile()||archive.length()!=length||!RuntimeBundle.hash(archive).equals(expected)){
            File partial=new File(folder,system+".part");
            try{
                if(new android.os.StatFs(folder.getPath()).getAvailableBytes()<length+1073741824L)throw new IOException("Not enough space to download runtime plus 1 GiB reserve");
                URL url=new URL(item.getString("url"));HttpURLConnection connection=null;
                try{
                    for(int hop=0;hop<6;hop++){
                        if(!url.getProtocol().equals("https"))throw new IOException("Runtime download must use HTTPS");
                        connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(30000);connection.setRequestProperty("Accept-Encoding","identity");
                        int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new IOException("Invalid runtime redirect");url=new URL(url,location);connection=null;continue;}
                        if(code!=200)throw new IOException("Runtime download HTTP "+code);break;
                    }
                    if(connection==null||connection.getResponseCode()!=200)throw new IOException("Too many runtime redirects");
                    long total=0,next=0;try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
                        byte[] bytes=new byte[1024*1024];int n;while((n=in.read(bytes))!=-1){if(cancelled.getAsBoolean())throw new IOException("Runtime download cancelled");total+=n;if(total>length)throw new IOException("Runtime download exceeds pinned size");out.write(bytes,0,n);if(total>=next){progress.accept("Runtime download: "+(total*100/length)+"% ("+total/1048576+" MiB)");next=total+8*1048576;} }out.getFD().sync();
                    }
                    if(total!=length||!RuntimeBundle.hash(partial).equals(expected))throw new IOException("Runtime download is incomplete or SHA-256 differs");
                    Files.move(partial.toPath(),archive.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                }finally{if(connection!=null)connection.disconnect();}
            }finally{Files.deleteIfExists(partial.toPath());}
        }
        if(cancelled.getAsBoolean())throw new IOException("Runtime installation cancelled before root preparation");
        progress.accept("Runtime package downloaded and verified. Preparing files with root…");
        String command="CLASSPATH="+q(context.getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin dev.tempgsi.controller.RuntimeInstallMain "+q(archive.getPath())+" "+q(expected)+" "+q(image.getPath())+" "+q(imageSha);
        String result=RootCommands.stream(context,command,progress,1800,()->false,false);
        if(!result.contains("RUNTIME_INSTALLED="+system))throw new IOException("Runtime installer did not confirm completion");
        return "Runtime installed. You can now press Start. The guest has not been launched.";
    }
    private RuntimeInstaller(){}
}
