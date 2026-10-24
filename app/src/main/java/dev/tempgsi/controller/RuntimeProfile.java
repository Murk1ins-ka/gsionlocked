package dev.tempgsi.controller;
import java.io.IOException;
import java.util.Properties;

/** Launch contract for the persistent guest installer/storage fixes. */
public final class RuntimeProfile {
    private RuntimeProfile() {}
    public static int requireInstallFixes(Properties profile)throws IOException {
        String entry=profile.getProperty("entry","");
        if(!entry.matches("EvoxWork[0-9]+"))throw new IOException("Invalid runtime entry.");
        int revision;
        try{revision=Integer.parseInt(entry.substring(8));}
        catch(NumberFormatException e){throw new IOException("Invalid runtime revision.");}
        if(revision<198)throw new IOException("Runtime update required: profile 198 or newer includes the Play Store storage fix. The APK alone cannot update the prepared runtime.");
        String jar="work"+revision+".jar";
        if(!jar.equals(profile.getProperty("jar")))throw new IOException("Runtime entry and jar differ.");
        for(String name:new String[]{jar,"loader"+revision,"guardian"+revision,"artd-session.so"}){
            if(!profile.getProperty("sha."+name,"").matches("[a-f0-9]{64}"))throw new IOException("Runtime install fixes are incomplete: "+name);
        }
        return revision;
    }
}
