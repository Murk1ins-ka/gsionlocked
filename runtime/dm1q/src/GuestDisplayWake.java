import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.io.FileOutputStream;

/** Bounded diagnostic display hold on the private guest's power Binder only. */
public final class GuestDisplayWake extends BroadcastReceiver implements Runnable {
    private PowerManager observedPower;
    private Context observedContext;
    private Handler handler;
    private int publishedState=-1;
    private void publish(){
        try{
            int state=observedPower.isInteractive()?1:0;
            try(FileOutputStream output=new FileOutputStream("/data/local/tmp/touch-state.request")){output.write(state==1?'1':'0');}
            if(state!=publishedState)System.out.println("PRIVATE_DISPLAY_INPUT_STATE_REQUEST="+state);
            publishedState=state;
        }catch(Exception error){System.err.println("PRIVATE_DISPLAY_HEARTBEAT_FAILED="+error);error.printStackTrace();System.exit(1);}
    }
    @Override public void onReceive(Context context,Intent intent){publish();}
    @Override public void run(){publish();handler.postDelayed(this,5000);}
    @SuppressWarnings("deprecation")
    public static void main(String[] args){
        try{startObserver();}catch(Throwable error){System.err.println("PRIVATE_DISPLAY_TEST_WAKELOCK_FAILED="+error);error.printStackTrace();System.exit(1);}
    }
    @SuppressWarnings("deprecation")
    private static void startObserver()throws Exception{
        if(android.os.Process.myUid()!=1000)throw new SecurityException("Private system UID required");
        Class<?> manager=Class.forName("android.os.ServiceManager");
        for(int attempt=0;attempt<100;attempt++){
            if(manager.getMethod("checkService",String.class).invoke(null,"power")!=null)break;
            if(attempt==99)throw new IllegalStateException("Guest power service unavailable");
            Thread.sleep(200);
        }
        android.os.Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");
        Object activityThread=thread.getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getMethod("getSystemContext").invoke(activityThread);
        PowerManager power=context.getSystemService(PowerManager.class);
        PowerManager.WakeLock hold=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"TempGSI:bounded-display-test");
        hold.setReferenceCounted(false);
        long end=SystemClock.elapsedRealtime()+65000;int previous=-1;long lastPublished=0;
        try{
            hold.acquire(65000);System.out.println("PRIVATE_DISPLAY_TEST_WAKELOCK_ACQUIRED_UID_1000");
            while(SystemClock.elapsedRealtime()<end){
                String booted=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"sys.boot_completed");
                if("1".equals(booted)){System.out.println("PRIVATE_DISPLAY_BOOT_HOLD_RELEASED_AFTER_BOOT_COMPLETE");break;}
                if(!power.isInteractive()){
                    if(hold.isHeld())hold.release();hold.acquire(end-SystemClock.elapsedRealtime()+1000);
                    System.out.println("PRIVATE_DISPLAY_TEST_REACTIVATED_AFTER_GUEST_SLEEP");
                }
                boolean interactive=power.isInteractive();int state=interactive?1:0;
                if(state!=previous||SystemClock.elapsedRealtime()-lastPublished>=5000){
                    // Guardian reads the shared page cache; disk durability is
                    // unnecessary for a five-second live heartbeat.
                    try(FileOutputStream output=new FileOutputStream("/data/local/tmp/touch-state.request")){output.write(state==1?'1':'0');}
                    if(state!=previous)System.out.println("PRIVATE_DISPLAY_INPUT_STATE_REQUEST="+state);
                    previous=state;lastPublished=SystemClock.elapsedRealtime();
                }
                Thread.sleep(500);
            }
        }finally{if(hold.isHeld())hold.release();}
        // Normal long sessions keep following display state after the bounded
        // bring-up hold ends. No wake lock is retained during daily operation.
        GuestDisplayWake observer=new GuestDisplayWake();observer.observedPower=power;observer.observedContext=context;
        observer.handler=new Handler(Looper.getMainLooper());
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(observer,filter,null,observer.handler,Context.RECEIVER_NOT_EXPORTED);
        observer.run();System.out.println("PRIVATE_DISPLAY_EVENT_OBSERVER_NO_IDLE_POLLING144");
        Looper.loop();
    }
}
