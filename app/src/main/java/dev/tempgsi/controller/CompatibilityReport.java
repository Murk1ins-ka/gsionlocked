package dev.tempgsi.controller;

import android.os.Build;
import android.app.Activity;
import android.content.Intent;

/** Local, read-only identity report; no root, radio/account queries or automatic upload. */
final class CompatibilityReport {
    static String text(){
        String profile=SamsungProfiles.recognized(Build.MODEL,Build.DEVICE,Build.SOC_MODEL);
        StringBuilder b=new StringBuilder("gsionlocked — Samsung only\n");
        b.append("Manufacturer: ").append(Build.MANUFACTURER).append("\nBrand: ").append(Build.BRAND)
            .append("\nModel: ").append(Build.MODEL).append("\nDevice: ").append(Build.DEVICE)
            .append("\nSoC: ").append(Build.SOC_MODEL).append("\nHost SDK: ").append(Build.VERSION.SDK_INT)
            .append("\n64-bit ABIs: ").append(java.util.Arrays.toString(Build.SUPPORTED_64_BIT_ABIS));
        if(!SamsungProfiles.samsung(Build.MANUFACTURER,Build.BRAND))
            b.append("\n\nBLOCKED: Samsung phones only.");
        else {
            b.append("\n\nOneOS identity: ").append(profile==null?"not recognized":profile);
            b.append("\nThis identifies the phone; it does not enable a runtime.");
            if(DeviceProfile.testedHost())b.append("\nLocal tested host: S23/FZG1. Runtime/image checks are still required.");
            else b.append("\nThis host has not been validated in gsionlocked. It needs a matching runtime port.");
            if(Build.VERSION.SDK_INT!=36)b.append("\nCurrent gsionlocked backend requires host Android 16. Upstream Android 17 scripts are porting sources only.");
            b.append("\nDo not reuse the S23 runtime on another phone by removing the framework check.");
        }
        b.append("\n\nUpstream reference: ").append(SamsungProfiles.UPSTREAM);
        b.append("\nPorting: https://github.com/Murk1ins-ka/gsionlocked/blob/main/docs/SAMSUNG_PORTING.md");
        return b.toString();
    }
    static void show(Activity activity){
        String report=text();
        SystemSheet.show(activity,"GSIONLOCKED / SAMSUNG","Совместимость / Compatibility",body->{
            android.widget.TextView view=Ui.text(body,report,13,Ui.TEXT);view.setTextIsSelectable(true);
        },"Поделиться / Share",()->{
            Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,report);
            activity.startActivity(Intent.createChooser(intent,"Share compatibility report"));
        });
    }
    private CompatibilityReport(){}
}
