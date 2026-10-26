package dev.tempgsi.controller;
import android.os.Build;
import java.io.IOException;

public final class DeviceProfileTest {
    static void rejects()throws Exception {
        try{DeviceProfile.requireHost();}catch(IOException expected){return;}
        throw new AssertionError("Incompatible host accepted");
    }
    public static void main(String[] args)throws Exception {
        DeviceProfile.requireHost();
        if(!DeviceProfile.testedHost())throw new AssertionError("Tested metadata lost");
        Build.MODEL="Another phone";Build.FINGERPRINT="another/device/new-firmware";
        DeviceProfile.requireHost();
        if(DeviceProfile.testedHost())throw new AssertionError("Unknown phone advertised as tested");
        Build.VERSION.SDK_INT=35;rejects();Build.VERSION.SDK_INT=37;rejects();Build.VERSION.SDK_INT=36;
        Build.SUPPORTED_64_BIT_ABIS=new String[0];rejects();
        Build.SUPPORTED_64_BIT_ABIS=new String[]{"x86_64"};rejects();
        Build.SUPPORTED_64_BIT_ABIS=new String[]{"arm64-v8a"};DeviceProfile.requireHost();
        Build.MANUFACTURER="Xiaomi";rejects();Build.MANUFACTURER="samsung";
        Build.BRAND="google";rejects();Build.BRAND="samsung";DeviceProfile.requireHost();
        Build.VERSION.SDK_INT=37;DeviceProfile.requireSamsungHost();rejects();Build.VERSION.SDK_INT=36;
        System.out.println("PASS: Samsung only, arbitrary Samsung model/firmware; SDK and ARM64 requirements retained");
    }
}
