package dev.tempgsi.controller;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class RootCommands {
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    public static String run(android.content.Context c,String command)throws Exception{
        return stream(c,command,line->{},45,()->false,false);
    }
    public static String stream(android.content.Context c,String command,Consumer<String> output,int seconds,BooleanSupplier cancelled,boolean allowTimeout)throws Exception{
        File client=new File(c.getApplicationInfo().nativeLibraryDir,"libksud.so");
        boolean bundled=client.isFile();
        Process p=new ProcessBuilder(bundled?new String[]{client.getAbsolutePath(),"debug","su","-g"}:new String[]{"su","-c",command}).redirectErrorStream(true).start();
        ExecutorService reader=Executors.newSingleThreadExecutor();
        StringBuilder result=new StringBuilder();
        try{
            Future<?> drain=reader.submit(()->{
                try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
                    String line;while((line=in.readLine())!=null){
                        result.append(line).append('\n');if(result.length()>32768)result.delete(0,result.length()-32768);
                        output.accept(line);
                    }
                }catch(IOException e){if(p.isAlive())throw new UncheckedIOException(e);}
            });
            try(OutputStream out=p.getOutputStream()){if(bundled)out.write(("exec /system/bin/sh -c "+quote(command)+"\n").getBytes("UTF-8"));}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
            boolean stopped=false;
            while(!p.waitFor(200,TimeUnit.MILLISECONDS)){
                if(cancelled.getAsBoolean()){stopped=true;break;}
                if(System.nanoTime()>=deadline){if(!allowTimeout)throw new IOException("Root request timed out. Check access in KernelSU.");stopped=true;break;}
            }
            if(stopped){p.destroyForcibly();return "";}
            drain.get(3,TimeUnit.SECONDS);
            String value=result.toString();
            if(p.exitValue()==159)throw new IOException("Root client blocked (SIGSYS). Restore temporary root/access in KernelSU before retrying.");
            if(p.exitValue()!=0&&!(allowTimeout&&p.exitValue()==124))throw new IOException(value.trim().isEmpty()?"Command failed (exit "+p.exitValue()+").":value.trim());
            return value;
        }finally{if(p.isAlive())p.destroyForcibly();reader.shutdownNow();}
    }
}
