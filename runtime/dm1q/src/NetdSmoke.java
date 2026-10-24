import android.os.IBinder;
import java.io.FileOutputStream;
public final class NetdSmoke {
    public static void main(String[] args){try{
        Class<?> manager=Class.forName("android.os.ServiceManager");IBinder binder=null;
        for(int attempt=0;attempt<40&&binder==null;attempt++){
            binder=(IBinder)manager.getMethod("checkService",String.class).invoke(null,"netd");
            if(binder==null)Thread.sleep(250);
        }
        if(binder==null)throw new IllegalStateException("Private netd did not register within 10 seconds");
        Object service=Class.forName("android.net.INetd$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Object alive=Class.forName("android.net.INetd").getMethod("isAlive").invoke(service);
        if(!Boolean.TRUE.equals(alive))throw new IllegalStateException("Private INetd.isAlive returned false");
        System.out.println("PRIVATE_NETD_REAL_IS_ALIVE_TRUE_ISOLATED_NETWORK");
        try(FileOutputStream output=new FileOutputStream("/data/local/tmp/netd-alive.ok")){output.write(1);}
    }catch(Throwable error){error.printStackTrace();System.exit(1);}}
}
