import android.os.IBinder;
import java.io.FileOutputStream;
public final class VoldSmoke {
    public static void main(String[] args)throws Exception {
        Class<?> manager=Class.forName("android.os.ServiceManager");
        IBinder binder=(IBinder)manager.getMethod("waitForService",String.class).invoke(null,"vold");
        Object service=Class.forName("android.os.IVold$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Object checkpoint=Class.forName("android.os.IVold").getMethod("needsCheckpoint").invoke(service);
        if(!Boolean.FALSE.equals(checkpoint))throw new IllegalStateException("Private tmpfs unexpectedly requires checkpoint");
        System.out.println("PRIVATE_VOLD_REAL_NEEDS_CHECKPOINT_FALSE_NO_BLOCK_STORAGE");
        try(FileOutputStream output=new FileOutputStream("/data/local/tmp/vold-checkpoint.ok")){output.write(1);}
    }
}
