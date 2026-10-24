import dev.tempgsi.controller.RuntimeProfile;
import java.io.IOException;
import java.util.Properties;

public class RuntimeProfileTest {
    private static Properties profile(int revision){
        Properties p=new Properties();p.setProperty("entry","EvoxWork"+revision);p.setProperty("jar","work"+revision+".jar");
        for(String name:new String[]{"work"+revision+".jar","loader"+revision,"guardian"+revision,"artd-session.so"})
            p.setProperty("sha."+name,"0000000000000000000000000000000000000000000000000000000000000000");
        return p;
    }
    private static void rejects(Properties p)throws Exception{
        try{RuntimeProfile.requireInstallFixes(p);}catch(IOException expected){return;}
        throw new AssertionError("Unsafe/incomplete profile accepted");
    }
    public static void main(String[] args)throws Exception{
        if(RuntimeProfile.requireInstallFixes(profile(198))!=198)throw new AssertionError();
        rejects(profile(195));
        Properties p=profile(198);p.remove("sha.artd-session.so");rejects(p);
        p=profile(198);p.setProperty("jar","work195.jar");rejects(p);
        p=profile(198);p.setProperty("sha.loader198","invalid");rejects(p);
        p=profile(198);p.setProperty("entry","EvoxWork99999999999999999999");rejects(p);
        System.out.println("PASS: fixed runtime accepted; stale, mixed and incomplete profiles rejected");
    }
}
