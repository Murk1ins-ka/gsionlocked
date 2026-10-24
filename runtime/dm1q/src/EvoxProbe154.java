import android.os.IBinder;
import java.io.*;
import java.security.MessageDigest;
public final class EvoxProbe154 {
    static final String PREFIX="tempgsi_evox154_probe";
    static final String NAME="evox154_system";
    static final String DATA="lineage_userdata_session";
    static final long SIZE=3863474176L;
    static final String HASH="5e8333e15798592bae059bcb78ba85394418afc8f035a13fc0e97c086b8c1e49";
    static Class<?> api;
    // Keep the registered lazy-service binder alive while using its child image binder.
    static Object gsiService;
    static Object images() throws Exception {
        IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("waitForService",String.class).invoke(null,"gsiservice");
        Object g=Class.forName("android.gsi.IGsiService$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
        Class<?> type=Class.forName("android.gsi.IGsiService");
        if((Boolean)type.getMethod("isGsiEnabled").invoke(g)||(Boolean)type.getMethod("isGsiRunning").invoke(g))throw new IllegalStateException("Require disabled DSU");
        gsiService=g;
        return type.getMethod("openImageService",String.class).invoke(g,PREFIX);
    }
    static String digest(String path,Object keepalive)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[4*1024*1024];long total=0,next=64*1024*1024;
        try(FileInputStream in=new FileInputStream(path)){
            int n;while((n=in.read(buffer))!=-1){digest.update(buffer,0,n);total+=n;if(keepalive!=null&&total>=next){api.getMethod("isImageMapped",String.class).invoke(keepalive,NAME);next+=64*1024*1024;}}
        }
        if(total!=SIZE)throw new IOException("Wrong image size "+total);
        StringBuilder value=new StringBuilder();for(byte x:digest.digest())value.append(String.format("%02x",x&255));return value.toString();
    }
    public static void main(String[] args){
        if(args.length==1&&args[0].equals("--cleanup-only")){
            try {
                api=Class.forName("android.gsi.IImageService");Object image=images();
                if((Boolean)api.getMethod("isImageMapped",String.class).invoke(image,DATA))api.getMethod("unmapImageDevice",String.class).invoke(image,DATA);
                if((Boolean)api.getMethod("backingImageExists",String.class).invoke(image,DATA))api.getMethod("deleteBackingImage",String.class).invoke(image,DATA);
                if((Boolean)api.getMethod("isImageMapped",String.class).invoke(image,NAME))api.getMethod("unmapImageDevice",String.class).invoke(image,NAME);
                System.out.println("LINEAGE_PROBE_MAPPED="+api.getMethod("isImageMapped",String.class).invoke(image,NAME));return;
            }catch(Throwable t){t.printStackTrace(System.out);System.exit(1);}
        }
        if(args.length==1&&args[0].equals("--verify-system-only")){
            boolean owned=false;Object image=null;
            try{
                api=Class.forName("android.gsi.IImageService");image=images();
                if(!(Boolean)api.getMethod("backingImageExists",String.class).invoke(image,NAME))throw new IOException("Prepared system is missing");
                if(!(Boolean)api.getMethod("isImageMapped",String.class).invoke(image,NAME)){
                    Class<?> mapped=Class.forName("android.gsi.MappedImage");api.getMethod("mapImageDevice",String.class,int.class,mapped).invoke(image,NAME,10000,mapped.getConstructor().newInstance());owned=true;
                }
                String device=(String)api.getMethod("getMappedImageDevice",String.class).invoke(image,NAME);
                if(!device.startsWith("/dev/block/"))throw new IOException("Invalid image device");
                long start=android.os.SystemClock.elapsedRealtime();
                if(!HASH.equals(digest(device,image)))throw new IOException("Backing image differs");
                System.out.println("PINNED_IMAGE_SERVICE_FULL_VERIFY_PASS_MS="+(android.os.SystemClock.elapsedRealtime()-start));
            }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
            finally{if(owned&&image!=null)try{api.getMethod("unmapImageDevice",String.class).invoke(image,NAME);}catch(Exception error){throw new RuntimeException(error);}}
            return;
        }
        long dataBytes=536870912L;
        boolean persistent=false,unlimited=false,reuseIdle=false,wifiHandoff=false,radioHandoff=false,bluetoothHandoff=false,cameraHandoff=false,fingerprintProbe=false,gatekeeperHandoff=false;
        if(args.length==0)throw new IllegalArgumentException("Explicit mode required");
        for(int index=1;index<args.length;index++){if(args[index].equals("--gatekeeper-handoff")){gatekeeperHandoff=true;}else if(args[index].equals("--fingerprint-probe")){fingerprintProbe=true;}else if(args[index].equals("--camera-handoff")){cameraHandoff=true;}else if(args[index].equals("--bluetooth-handoff")){bluetoothHandoff=true;}else if(args[index].equals("--radio-handoff")){radioHandoff=true;}else if(args[index].equals("--wifi-handoff")){wifiHandoff=true;}else if(args[index].equals("--persistent-data")){persistent=true;}else if(args[index].equals("--reuse-idle-data")){reuseIdle=true;}else if(args[index].equals("--unlimited-session")){unlimited=true;}else if(args[index].equals("--data-mib")&&index+1<args.length){long mib=Long.parseLong(args[++index]);if(mib<512||mib>Integer.MAX_VALUE)throw new IllegalArgumentException("Guest data size out of bounds");dataBytes=mib*1048576L;}else throw new IllegalArgumentException("Unknown argument");}
        if(reuseIdle&&!persistent)throw new IllegalArgumentException("Idle mapping reuse requires persistent data");
        boolean hardwareLink=args[0].equals("--hardware-link-probe");
        if(hardwareLink&&args.length!=1)throw new IllegalArgumentException("Hardware link probe takes no data or display options");
        boolean display=args[0].equals("--isolated-display-lease");
        if(radioHandoff&&!wifiHandoff)throw new IllegalArgumentException("Radio requires guarded hardware profile");
        if(wifiHandoff&&!display)throw new IllegalArgumentException("Wi-Fi handoff requires the display guardian");
        if(unlimited&&!display)throw new IllegalArgumentException("Unlimited sessions require the external display guardian");
        boolean systemserver=display||args[0].equals("--isolated-systemserver");
        boolean isolated=args[0].equals("--isolated-services")||systemserver;
        if(!args[0].equals("--stage-and-probe-lineage")&&!isolated&&!hardwareLink){System.out.println("Explicit probe mode required");System.exit(2);}
        int result=1;boolean ownMapping=false;GuestDataVolume volume=null;
        try {
            api=Class.forName("android.gsi.IImageService");
            String source="/data/local/tmp/tempgsi-evox154/system.img";
            Object image=images();
            boolean exists=(Boolean)api.getMethod("backingImageExists",String.class).invoke(image,NAME);
            if(!exists){
                if(!HASH.equals(digest(source,null)))throw new IOException("Source image hash mismatch");
                Class<?> callback=Class.forName("android.gsi.IProgressCallback");
                api.getMethod("createBackingImage",String.class,long.class,int.class,callback).invoke(image,NAME,SIZE,0,null);
            }
            boolean mappedNow=(Boolean)api.getMethod("isImageMapped",String.class).invoke(image,NAME);
            if(!mappedNow){
                Class<?> mapped=Class.forName("android.gsi.MappedImage");
                api.getMethod("mapImageDevice",String.class,int.class,mapped).invoke(image,NAME,10000,mapped.getConstructor().newInstance());ownMapping=true;
            }
            String path=(String)api.getMethod("getMappedImageDevice",String.class).invoke(image,NAME);
            if(!path.startsWith("/dev/block/"))throw new IOException("Unexpected device path");
            if(!exists){
                byte[] buffer=new byte[4*1024*1024];long total=0,next=64*1024*1024;
                try(FileInputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(path)){
                    int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);total+=n;if(total>=next){api.getMethod("isImageMapped",String.class).invoke(image,NAME);next+=64*1024*1024;}}
                    out.getFD().sync();if(total!=SIZE)throw new IOException("Copy size mismatch");
                }
            }
            long verifyStart=android.os.SystemClock.elapsedRealtime();
            if(!HASH.equals(digest(path,image)))throw new IOException("Backing image differs; no execution");
            System.out.println("SYSTEM_IMAGE_VERIFY_MS="+(android.os.SystemClock.elapsedRealtime()-verifyStart)+"_EXISTING="+exists);
            System.out.println("LINEAGE_MAPPED_HASH_VERIFIED="+HASH);
            String dataPath=null;
            if(systemserver){
                volume=new GuestDataVolume(image,api,HASH,persistent,reuseIdle);dataPath=volume.prepare(dataBytes);
                if(!dataPath.startsWith("/dev/block/")||dataPath.equals(path))throw new IOException("Invalid own guest data device");
                System.out.println("PRIVATE_EXT4_DATA_SESSION_READY="+dataPath+"_BYTES="+volume.size()+"_PERSISTENT="+persistent);
            }
            ProcessBuilder process=systemserver?new ProcessBuilder("/data/local/tmp/tempgsi-gsionlocked186/loader186",path,"/data/local/tmp/tempgsi-evox154/runtime",display?"--isolated-display-lease":"--isolated-systemserver",dataPath):isolated?new ProcessBuilder("/data/local/tmp/tempgsi-gsionlocked186/loader186",path,"/data/local/tmp/tempgsi-evox154/runtime","--isolated-services"):new ProcessBuilder("/data/local/tmp/tempgsi-gsionlocked186/loader186",path,"/data/local/tmp/tempgsi-evox154/runtime");
            if(hardwareLink)process=new ProcessBuilder("/data/local/tmp/tempgsi-evox154/loader_probe121",path,"/data/local/tmp/tempgsi-evox154/runtime","--hardware-link-probe");
            if(unlimited)process.command().add("--unlimited-session");
            if(wifiHandoff){process.command().set(0,"/data/local/tmp/tempgsi-gsionlocked186/loader186");process.command().add("--wifi-handoff");}
            if(radioHandoff)process.command().add("--radio-handoff");
            if(bluetoothHandoff)process.command().add("--bluetooth-handoff");
            if(cameraHandoff)process.command().add("--camera-handoff");
            if(fingerprintProbe)process.command().add("--fingerprint-probe");
            if(gatekeeperHandoff)process.command().add("--gatekeeper-handoff");
            result=process.inheritIO().start().waitFor();
        }catch(Throwable t){t.printStackTrace(System.out);result=1;}
        finally {
            if(radioHandoff&&new File("/data/local/tmp/tempgsi-control/radio-guardian-started144").isFile())try{
                long deadline=android.os.SystemClock.elapsedRealtime()+45000;
                File released=new File("/data/local/tmp/tempgsi-control/radio-guardian-released144");
                while(!released.isFile()&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
                if(!released.isFile())throw new IOException("Radio guardian mount release not confirmed before cleanup");
                System.out.println("RADIO_GUARDIAN_RELEASE_CONFIRMED_BEFORE_DATA_UNMAP144");
            }catch(Throwable t){t.printStackTrace(System.out);result=1;}
            if(volume!=null)try{volume.close();}catch(Throwable t){t.printStackTrace(System.out);result=1;}
            File group=new File("/sys/fs/cgroup/tempgsi_session_"+android.os.Process.myPid());if(group.isDirectory()&&!group.delete())System.out.println("GUEST_CGROUP_NOT_EMPTY_OR_AWAITING_RELEASE="+group);
            if(ownMapping)try{
                Object image=images();
                for(int retry=0;retry<3;retry++){
                    if(!(Boolean)api.getMethod("isImageMapped",String.class).invoke(image,NAME))break;
                    try{api.getMethod("unmapImageDevice",String.class).invoke(image,NAME);}catch(Exception e){if(retry==2)throw e;Thread.sleep(1000);image=images();}
                }
                if((Boolean)api.getMethod("isImageMapped",String.class).invoke(image,NAME))throw new IOException("Still mapped");
                System.out.println("LINEAGE_PROBE_IMAGE_UNMAPPED");
            }catch(Throwable t){t.printStackTrace(System.out);result=1;}
        }
        System.exit(result);
    }
}
