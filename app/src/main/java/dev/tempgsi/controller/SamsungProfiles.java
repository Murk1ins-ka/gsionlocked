package dev.tempgsi.controller;

/** Profile identities adapted from OneOS 73c3be3 (Apache-2.0).
 * Recognition is NOT a launch permission or evidence of testing in gsionlocked. */
final class SamsungProfiles {
    static final String UPSTREAM="73c3be36c35462742503380e1f025fead3cea48c";
    static final String[][] KNOWN={
        {"Galaxy S23","SM-S911","dm1q","SM8550"},
        {"Galaxy S23+","SM-S916","dm2q","SM8550"},
        {"Galaxy S24 Ultra","SM-S928","e3q","SM8650"},
        {"Galaxy S25","SM-S931","pa1q","SM8750"},
        {"Galaxy S25+","SM-S936","pa2q","SM8750"},
        {"Galaxy S25 Ultra","SM-S938","pa3q","SM8750"},
        {"Galaxy S26","SM-S942","m1q","SM8850"},
        {"Galaxy S26 (docomo)","SC-51G","m1q","SM8850"},
        {"Galaxy S26+","SM-S947","m2q","SM8850"},
        {"Galaxy S26 Ultra","SM-S948","m3q","SM8850"},
        {"Galaxy Z Fold7","SM-F966","q7q","SM8750"},
        {"Galaxy Z Fold8","SM-F971","h8q","SM8850"},
        {"Galaxy Z Fold8 Ultra","SM-F976","q8q","SM8850"}
    };
    static boolean samsung(String manufacturer,String brand){
        return "samsung".equalsIgnoreCase(manufacturer==null?"":manufacturer.trim())
            && "samsung".equalsIgnoreCase(brand==null?"":brand.trim());
    }
    static String recognized(String model,String device,String soc){
        if(model==null||device==null||soc==null)return null;
        for(String[] p:KNOWN)if(model.trim().toUpperCase(java.util.Locale.ROOT).startsWith(p[1])
            &&device.trim().equalsIgnoreCase(p[2])&&soc.trim().equalsIgnoreCase(p[3]))return p[0];
        return null;
    }
    private SamsungProfiles(){}
}
