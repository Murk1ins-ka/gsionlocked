package dev.tempgsi.controller;

import android.content.Context;
import java.io.IOException;
import java.util.function.*;

public final class AutomaticSetupTest {
    static void reset(String state){RootCommands.state=state;RootCommands.calls=0;RuntimeInstaller.calls=0;RuntimeInstaller.fail=false;}
    static void fails(String sha,BooleanSupplier cancelled)throws Exception{
        try{AutomaticSetup.ensure(new Context(),sha,line->{},cancelled);}catch(IOException expected){return;}
        throw new AssertionError("Unsafe preparation accepted");
    }
    public static void main(String[] args)throws Exception{
        for(String sha:new String[]{DeviceProfile.SHA256,ColorOsProfile.SHA256}){
            reset("SETUP_PRESENT");AutomaticSetup.ensure(new Context(),sha,line->{},()->false);
            if(RuntimeInstaller.calls!=0)throw new AssertionError("Existing environment overwritten");
            reset("SETUP_MISSING");AutomaticSetup.ensure(new Context(),sha,line->{},()->false);
            if(RuntimeInstaller.calls!=1)throw new AssertionError("Missing environment not prepared");
            reset("SETUP_PARTIAL");fails(sha,()->false);
            if(RuntimeInstaller.calls!=0)throw new AssertionError("Partial data overwritten");
            reset("unexpected output");fails(sha,()->false);
            if(RuntimeInstaller.calls!=0)throw new AssertionError("Unknown state treated as empty");
            reset("SETUP_MISSING");fails(sha,()->true);
            if(RuntimeInstaller.calls!=0)throw new AssertionError("Cancelled before setup");
            reset("SETUP_MISSING");fails(sha,()->RuntimeInstaller.calls>0);
            reset("SETUP_MISSING");RuntimeInstaller.fail=true;fails(sha,()->false);
        }
        reset("SETUP_MISSING");fails("unsupported",()->false);
        if(RootCommands.calls!=0||RuntimeInstaller.calls!=0)throw new AssertionError("Unsupported image touched root");
        reset("SETUP_MISSING");android.os.Build.MANUFACTURER="Xiaomi";fails(DeviceProfile.SHA256,()->false);
        if(RootCommands.calls!=0)throw new AssertionError("Non-Samsung reached root setup");
        android.os.Build.MANUFACTURER="samsung";
        System.out.println("PASS: automatic first-start setup; existing/partial data protection; failure and cancellation stop the flow; no phone/network used");
    }
}
// Isolated fakes: the test cannot execute a shell, download or touch a phone.
final class RootCommands {
    static String state;static int calls;
    static String run(Context c,String command){calls++;return state;}
}
final class RuntimeInstaller {
    static int calls;static boolean fail;
    static String install(Context c,String sha,Consumer<String> progress,BooleanSupplier cancelled)throws IOException{
        calls++;if(fail)throw new IOException("Injected install failure");return "fixture installed";
    }
}
final class HostPreparation {
    static void capture(Context context,Consumer<String> progress,BooleanSupplier cancelled)throws IOException{
        if(cancelled.getAsBoolean())throw new IOException("Cancelled host capture fixture");
    }
}
final class ColorOsProfile {
    static final String SHA256="a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0";
    static boolean matches(String sha){return SHA256.equals(sha);}
}
