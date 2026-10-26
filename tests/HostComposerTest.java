package dev.tempgsi.controller;
import java.io.*;
import java.util.*;

public final class HostComposerTest {
    static List<HostComposer.Service> parse(String text)throws Exception{return HostComposer.parse("/vendor/etc/init/test.rc",text);}
    static void reject(String text)throws Exception{
        try{HostComposer.select(parse(text),name->"running");throw new AssertionError("Unsafe/ambiguous service accepted");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception {
        String q="service vendor.qti.hardware.display.composer /vendor/bin/hw/vendor.qti.hardware.display.composer-service\n    interface android.hardware.graphics.composer@2.4::IComposer default\n";
        HostComposer.Service qualcomm=HostComposer.select(parse(q),name->"running");
        if(!qualcomm.name.equals("vendor.qti.hardware.display.composer")||!qualcomm.composerInterface().contains("@2.4"))throw new AssertionError("HIDL discovery");
        String exynos=" # comment\nservice vendor.graphics.composer \"/vendor/bin/hw/android.hardware.graphics.composer3-service.exynos\"\n    interface aidl \\\n        android.hardware.graphics.composer3.IComposer/default\n    disabled\n    onrestart restart surfaceflinger\n";
        HostComposer.Service samsung=HostComposer.select(parse(exynos),name->"running");
        if(!samsung.name.equals("vendor.graphics.composer")||!samsung.composerInterface().contains("composer3"))throw new AssertionError("AIDL discovery");
        List<HostComposer.Service> both=new ArrayList<>(parse(q));both.addAll(parse(exynos));
        if(!HostComposer.select(both,name->name.equals(samsung.name)?"running":"stopped").name.equals(samsung.name))throw new AssertionError("Inactive definition selected");
        reject(q+exynos);reject(q+q);reject(q+"    critical\n");reject(q+"    reboot_on_failure reboot\n");reject(q+"    override\n");
        reject("service vendor.other /vendor/bin/other\non boot\n    interface aidl android.hardware.graphics.composer3.IComposer/default\n");
        reject("service injected;command /vendor/bin/composer\n interface aidl android.hardware.graphics.composer3.IComposer/default\n");
        reject("service vendor.composer /data/local/tmp/composer\n interface aidl android.hardware.graphics.composer3.IComposer/default\n");
        reject(q.replace(" default"," secondary"));
        try{HostComposer.select(parse(q),name->"stopped");throw new AssertionError("Inactive composer accepted");}catch(IOException expected){}
        String legacy=q.substring(0,q.indexOf('\n')+1);
        if(parse("on boot\n    exec /vendor/bin/sh -c 'unrelated \\\"script\n"+legacy).size()!=1)throw new AssertionError("Unrelated command parsed as declaration");
        SortedMap<String,String> registry=HostComposer.hidlRegistry("Interface PID\nandroid.hardware.graphics.composer@2.4::IComposer/default 1777\n");
        HostComposer.Running owned=new HostComposer.Running(){
            public String service(String name){return "running";}
            public String pid(String name){return "1777";}
            public String executable(String pid){return "/vendor/bin/hw/vendor.qti.hardware.display.composer-service";}
        };
        if(!HostComposer.selectRegistered(parse(legacy),owned,registry).name.equals(qualcomm.name))throw new AssertionError("Legacy registry discovery");
        for(String invalid:new String[]{legacy+legacy,legacy+"    critical\n"}){
            try{HostComposer.selectRegistered(parse(invalid),owned,registry);throw new AssertionError("Unsafe registry ownership");}catch(IOException expected){}
        }
        registry.put("android.hardware.graphics.composer@2.4::IComposer/default","999");
        try{HostComposer.selectRegistered(parse(legacy),owned,registry);throw new AssertionError("Unrelated PID accepted");}catch(IOException expected){}
        registry.put("android.hardware.graphics.composer@2.4::IComposer/default","1777");
        HostComposer.Running wrongBinary=new HostComposer.Running(){public String service(String n){return "running";}public String pid(String n){return "1777";}public String executable(String p){return "/vendor/bin/unrelated";}};
        try{HostComposer.selectRegistered(parse(legacy),wrongBinary,registry);throw new AssertionError("Wrong executable accepted");}catch(IOException expected){}
        System.out.println("PASS: Qualcomm HIDL / Samsung-style AIDL composer discovery, active service selection and ambiguous/critical/action/path rejection");
    }
}
