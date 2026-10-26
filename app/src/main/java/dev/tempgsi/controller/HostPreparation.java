package dev.tempgsi.controller;
import android.content.Context;
import java.io.IOException;
import java.util.function.*;

final class HostPreparation {
    static void capture(Context context,Consumer<String> progress,BooleanSupplier cancelled)throws Exception {
        DeviceProfile.requireSamsungHost();if(cancelled.getAsBoolean())throw new IOException("Host preparation cancelled");
        progress.accept("Preparing host compatibility files from this Samsung's own system…");
        String apk=context.getApplicationInfo().sourceDir;
        String command="CLASSPATH='"+apk.replace("'","'\\''")+"' /system/bin/app_process /system/bin dev.tempgsi.controller.HostPrepareMain";
        String result=RootCommands.stream(context,command,progress,300,cancelled,false);
        if(!result.contains("HOST_PREPARED=/data/local/tmp/gsionlocked-host-runtimes/"))throw new IOException("Local host runtime preparation did not complete");
    }
    private HostPreparation(){}
}
