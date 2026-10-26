package dev.tempgsi.controller;

import android.os.Build;
import java.io.IOException;

/** Platform requirements; tested-device metadata is informational, not an allowlist. */
final class DeviceProfile {
    static final String SHA256="5e8333e15798592bae059bcb78ba85394418afc8f035a13fc0e97c086b8c1e49";
    static final long IMAGE_BYTES=3863474176L;
    static final String SOURCE="/data/local/tmp/tempgsi-evox154/system.img";
    static void requireHost() throws IOException {
        if(!SamsungProfiles.samsung(Build.MANUFACTURER,Build.BRAND))
            throw new IOException("Samsung phones only. Other manufacturers are not supported.");
        if(Build.VERSION.SDK_INT!=36)
            throw new IOException("The current launcher requires an Android 16 host and a matching prepared runtime.");
        if(!java.util.Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a"))
            throw new IOException("The current runtime requires arm64-v8a support.");
    }
    static boolean testedHost(){return "SM-S911B".equals(Build.MODEL)&&Build.VERSION.SDK_INT==36&&Build.FINGERPRINT.contains("/dm1q")&&Build.FINGERPRINT.contains("S911BXXSAFZG1");}
    private DeviceProfile(){}
}
