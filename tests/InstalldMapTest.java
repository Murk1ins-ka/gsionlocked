package com.android.server.pm;
import java.util.Properties;
import android.os.RemoteException;
public final class InstalldMapTest {
    static void denied(Properties p,String key)throws Exception{
        try{GsionlockedInstalldMap.resolve(p,key);}catch(RemoteException expected){return;}
        throw new AssertionError("Unapproved host call accepted");
    }
    public static void main(String[] args)throws Exception{
        String key="createUserData|(Ljava/lang/String;III)V";Properties p=new Properties();p.setProperty("method."+key,"1");
        if(GsionlockedInstalldMap.resolve(p,key)!=1)throw new AssertionError();
        p.setProperty("method."+key,"301");if(GsionlockedInstalldMap.resolve(p,key)!=301)throw new AssertionError("Host code not selected dynamically");
        denied(p,"createUserData|(Ljava/lang/String;)V");
        p.setProperty("method.destroyUserData|(Ljava/lang/String;II)V","2");denied(p,"destroyUserData|(Ljava/lang/String;II)V");
        p.setProperty("method."+key,"0");denied(p,key);p.setProperty("method."+key,"16777216");denied(p,key);
        denied(p,null);System.out.println("PASS: dynamic installd codes; exact signatures and destructive-call guard retained");
    }
}
