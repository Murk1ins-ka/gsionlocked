import java.io.*;
import java.util.*;

/** Owns only the One UI Wi-Fi enabled state; never changes airplane mode. */
public final class HostWifiLease implements AutoCloseable {
    interface Commands {
        String run(String... arguments)throws Exception;
        long now();
        void sleep(long milliseconds)throws Exception;
    }
    private final Commands commands;
    private boolean restore;
    public HostWifiLease(){this(new Commands(){
        public String run(String... arguments)throws Exception{
            List<String> command=new ArrayList<>(Arrays.asList("/system/bin/timeout","5"));Collections.addAll(command,arguments);
            Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=process.getInputStream()){
                byte[] buffer=new byte[1024];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>65536){process.destroy();throw new IOException("Wi-Fi command output too large");}bytes.write(buffer,0,count);}
            }
            String output=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).trim();
            if(process.waitFor()!=0)throw new IOException("Wi-Fi command failed: "+command+" "+output);
            return output;
        }
        public long now(){return System.nanoTime()/1000000;}
        public void sleep(long milliseconds)throws InterruptedException{Thread.sleep(milliseconds);}
    });}
    HostWifiLease(Commands commands){this.commands=commands;}
    private String state()throws Exception{return commands.run("/system/bin/settings","get","global","wifi_on");}
    public void prepare()throws Exception{
        String original=state();
        if(!original.equals("0")&&!original.equals("1")&&!original.equals("2"))throw new IOException("Unexpected host wifi_on: "+original);
        // Mark restoration before a command which might change state and fail.
        restore=!original.equals("0");
        if(restore)commands.run("/system/bin/svc","wifi","disable");
        long until=commands.now()+10000;
        do{if(state().equals("0")){System.out.println("WORK_HOST_WIFI_QUIESCED_ORIGINAL="+original);return;}commands.sleep(250);}while(commands.now()<until);
        throw new IOException("Host Wi-Fi did not disable; guest not started");
    }
    @Override public void close()throws Exception{
        if(!restore)return;
        long until=commands.now()+120000;Exception last=null;
        do{
            try{
                if(commands.run("/system/bin/service","check","wifi").equals("Service wifi: found")){
                    commands.run("/system/bin/svc","wifi","enable");
                    String current=state();
                    if(current.equals("1")||current.equals("2")){restore=false;System.out.println("WORK_HOST_WIFI_REENABLE_CONFIRMED_STATE="+current);return;}
                }
            }catch(Exception unavailable){last=unavailable;}
            commands.sleep(500);
        }while(commands.now()<until);
        throw new IOException("Host Wi-Fi restoration not confirmed",last);
    }
}
