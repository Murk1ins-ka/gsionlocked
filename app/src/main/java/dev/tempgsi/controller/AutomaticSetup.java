package dev.tempgsi.controller;

import android.content.Context;
import java.io.IOException;
import java.util.function.*;

/** First-start preparation. Never treats a partial installation as an empty phone. */
final class AutomaticSetup {
    static void ensure(Context context,String imageSha,Consumer<String> progress,BooleanSupplier cancelled)throws Exception {
        DeviceProfile.requireHost();
        boolean coloros=ColorOsProfile.matches(imageSha);
        if(!coloros&&!DeviceProfile.SHA256.equals(imageSha))throw new IOException("No compatible backend for this GSI image.");
        String probe=coloros
            ? "if [ -L /data/local/tmp/oneos ]; then echo SETUP_PARTIAL; elif [ -d /data/local/tmp/oneos ] && [ -f /data/local/tmp/oneos/fix.sha256 ] && [ -f /data/local/tmp/oneos/system.raw ]; then echo SETUP_PRESENT; elif [ -e /data/local/tmp/oneos ]; then echo SETUP_PARTIAL; else echo SETUP_MISSING; fi"
            : "if [ -L /data/local/tmp/gsionlocked-profile.properties ]; then echo SETUP_PARTIAL; elif [ -f /data/local/tmp/gsionlocked-profile.properties ] && [ -d /data/local/tmp/tempgsi-evox154 ] && [ -d /data/local/tmp/tempgsi-gsionlocked198 ]; then echo SETUP_PRESENT; elif [ -e /data/local/tmp/gsionlocked-profile.properties ] || [ -e /data/local/tmp/tempgsi-evox154 ] || [ -e /data/local/tmp/tempgsi-gsionlocked198 ]; then echo SETUP_PARTIAL; else echo SETUP_MISSING; fi";
        String state=RootCommands.run(context,probe).trim();
        if(state.equals("SETUP_PRESENT")){
            progress.accept("Existing environment found; launch integrity checks will run next.");
            return;
        }
        if(!state.equals("SETUP_MISSING"))throw new IOException("Existing or incomplete environment detected. Files preserved; automatic setup will not overwrite them.");
        if(cancelled.getAsBoolean())throw new IOException("Setup cancelled before installation.");
        progress.accept("First start: downloading and preparing the matching environment automatically…");
        progress.accept(RuntimeInstaller.install(context,imageSha,progress,cancelled));
        if(cancelled.getAsBoolean())throw new IOException("Environment prepared. Launch cancelled because the app was closed; reopen it and press Start.");
    }
    private AutomaticSetup(){}
}
