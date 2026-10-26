package dev.tempgsi.controller;
public final class SamsungProfilesTest {
    public static void main(String[] args){
        if(SamsungProfiles.KNOWN.length!=13)throw new AssertionError("Missing upstream identities");
        for(String[] p:SamsungProfiles.KNOWN){
            if(!p[0].equals(SamsungProfiles.recognized(p[1]+"B",p[2],p[3])))throw new AssertionError(p[0]);
            if(SamsungProfiles.recognized(p[1]+"B",p[2],"Exynos")!=null)throw new AssertionError("Wrong SoC accepted");
            if(SamsungProfiles.recognized(p[1]+"B","wrong",p[3])!=null)throw new AssertionError("Wrong device accepted");
        }
        if(SamsungProfiles.recognized(null,"dm1q","SM8550")!=null)throw new AssertionError();
        if(!SamsungProfiles.samsung("Samsung","SAMSUNG")||SamsungProfiles.samsung("Xiaomi","samsung")||SamsungProfiles.samsung("samsung","google"))throw new AssertionError("Manufacturer policy");
        System.out.println("PASS: 13 OneOS Samsung identities; model/device/SoC must agree; recognition grants no launch permission");
    }
}
