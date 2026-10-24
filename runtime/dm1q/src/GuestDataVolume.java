import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Own ImageService volumes only; growth copies to a new generation before commit. */
public final class GuestDataVolume implements AutoCloseable {
    private final Object images;
    private final Class<?> api;
    private final String hash;
    private final boolean persistent;
    private final boolean reuseIdleMapped;
    private final Set<String> borrowed=new LinkedHashSet<>();
    private final RandomAccessFile lockFile;
    private final java.nio.channels.FileLock lock;
    private final File metadata=new File("/data/local/tmp/tempgsi-evox154/guest-data-v1.properties");
    private final Set<String> mapped=new LinkedHashSet<>(),created=new LinkedHashSet<>();
    private String committedName,path;
    private long size;
    public GuestDataVolume(Object images,Class<?> api,String hash,boolean persistent)throws IOException{this(images,api,hash,persistent,false);}
    public GuestDataVolume(Object images,Class<?> api,String hash,boolean persistent,boolean reuseIdleMapped)throws IOException{
        this.images=images;this.api=api;this.hash=hash;this.persistent=persistent;this.reuseIdleMapped=reuseIdleMapped;
        lockFile=new RandomAccessFile(new File(metadata.getParentFile(),"guest-data-v1.lock"),"rw");
        java.nio.channels.FileLock acquired;
        try{acquired=lockFile.getChannel().tryLock();if(acquired==null)throw new IOException("Another guest data operation is active");}
        catch(IOException|RuntimeException error){lockFile.close();throw error;}
        lock=acquired;
    }
    private boolean exists(String name)throws Exception{return (Boolean)api.getMethod("backingImageExists",String.class).invoke(images,name);}
    private String map(String name)throws Exception{
        if((Boolean)api.getMethod("isImageMapped",String.class).invoke(images,name)){
            if(!reuseIdleMapped||!persistent||!name.matches("lineage_userdata_v1_[a-f0-9]{12}")||created.contains(name))throw new IOException("Guest data is already mapped: "+name);
            String existing=(String)api.getMethod("getMappedImageDevice",String.class).invoke(images,name);
            if(!existing.startsWith("/dev/block/"))throw new IOException("Invalid existing guest block path");
            command("/data/local/tmp/tempgsi-guest-mount-audit","/dev/block/mapper/"+name);
            borrowed.add(name);System.out.println("PERSISTENT_GUEST_DATA_IDLE_MAPPING_REOPENED_NO_FORMAT="+name);return existing;
        }
        Class<?> type=Class.forName("android.gsi.MappedImage");api.getMethod("mapImageDevice",String.class,int.class,type).invoke(images,name,10000,type.getConstructor().newInstance());mapped.add(name);
        String result=(String)api.getMethod("getMappedImageDevice",String.class).invoke(images,name);if(!result.startsWith("/dev/block/"))throw new IOException("Invalid guest block path");return result;
    }
    private String create(String name,long bytes)throws Exception{
        if(exists(name))throw new IOException("Refusing overwrite of existing data image "+name);
        if(new android.os.StatFs("/data").getAvailableBytes()<bytes+1073741824L)throw new IOException("Insufficient storage plus 1 GiB reserve");
        api.getMethod("createBackingImage",String.class,long.class,int.class,Class.forName("android.gsi.IProgressCallback")).invoke(images,name,bytes,0,null);created.add(name);return map(name);
    }
    private static String generation(){return "lineage_userdata_v1_"+UUID.randomUUID().toString().replace("-","").substring(0,12);}
    private static void command(String binary,String... args)throws Exception{List<String> command=new ArrayList<>();command.add(binary);Collections.addAll(command,args);int code=new ProcessBuilder(command).inheritIO().start().waitFor();if(code!=0&&!(binary.endsWith("e2fsck")&&code==1))throw new IOException(binary+" failed "+code);}
    private static long little(byte[] bytes,int offset){return ((long)bytes[offset]&255)|(((long)bytes[offset+1]&255)<<8)|(((long)bytes[offset+2]&255)<<16)|(((long)bytes[offset+3]&255)<<24);}
    private static Properties inspect(String path)throws Exception{
        byte[] bytes=new byte[4096];try(RandomAccessFile file=new RandomAccessFile(path,"r")){file.readFully(bytes);}
        if((bytes[1080]&255)!=0x53||(bytes[1081]&255)!=0xef)throw new IOException("Guest volume is not ext4");
        long log=little(bytes,1048);if(log>6)throw new IOException("Invalid ext4 block size");
        long blocks=little(bytes,1028);if((little(bytes,1120)&0x80)!=0)blocks|=little(bytes,1360)<<32;
        if(blocks<=0||blocks>Long.MAX_VALUE/(1024L<<log))throw new IOException("Invalid ext4 size");
        StringBuilder uuid=new StringBuilder();for(int i=1128;i<1144;i++)uuid.append(String.format("%02x",bytes[i]&255));
        Properties result=new Properties();result.setProperty("uuid",uuid.toString());result.setProperty("bytes",Long.toString(blocks*(1024L<<log)));return result;
    }
    private void commit(String name,long bytes,String uuid,String previous)throws Exception{
        Properties value=new Properties();value.setProperty("version","1");value.setProperty("gsi_sha256",hash);value.setProperty("name",name);value.setProperty("bytes",Long.toString(bytes));value.setProperty("uuid",uuid);if(previous!=null)value.setProperty("previous_generation",previous);
        File staging=new File(metadata.getParentFile(),"guest-data-v1.properties.new");try(FileOutputStream output=new FileOutputStream(staging)){value.store(output,"Private GSI data; never host userdata");output.getFD().sync();}android.system.Os.chmod(staging.getAbsolutePath(),0600);
        Files.move(staging.toPath(),metadata.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);committedName=name;
    }
    private void unmap(String name)throws Exception{for(int retry=0;retry<5;retry++){try{if((Boolean)api.getMethod("isImageMapped",String.class).invoke(images,name))api.getMethod("unmapImageDevice",String.class).invoke(images,name);mapped.remove(name);return;}catch(Exception error){if(retry==4)throw error;Thread.sleep(1000);}}}
    public String prepare(long requested)throws Exception{
        if(requested<536870912L||requested%1048576L!=0)throw new IOException("Invalid requested guest size");
        if(!persistent){String name="lineage_userdata_session";path=create(name,requested);command("/system/bin/mke2fs","-t","ext4","-b","4096","-I","256","-L","tempgsi_guest","-F",path);size=requested;return path;}
        if(!metadata.exists()){
            String name=generation();path=create(name,requested);command("/system/bin/mke2fs","-t","ext4","-b","4096","-I","256","-L","tempgsi_guest","-F",path);Properties info=inspect(path);size=Long.parseLong(info.getProperty("bytes"));if(size!=requested)throw new IOException("New volume size mismatch");commit(name,size,info.getProperty("uuid"),null);System.out.println("PERSISTENT_GUEST_DATA_CREATED="+name+"_BYTES="+size);return path;
        }
        Properties previous=new Properties();try(FileInputStream input=new FileInputStream(metadata)){previous.load(input);}
        String old=previous.getProperty("name","");if(!"1".equals(previous.getProperty("version"))||!hash.equals(previous.getProperty("gsi_sha256"))||!old.matches("lineage_userdata_v1_[a-f0-9]{12}")||!exists(old))throw new IOException("Guest data identity cannot be verified; refusing format");
        path=map(old);Properties info=inspect(path);size=Long.parseLong(info.getProperty("bytes"));if(!info.getProperty("uuid").equals(previous.getProperty("uuid"))||size!=Long.parseLong(previous.getProperty("bytes")))throw new IOException("Guest data metadata differs from ext4");committedName=old;
        if(requested<=size){System.out.println("PERSISTENT_GUEST_DATA_REUSED="+old+"_BYTES="+size+"_NO_SHRINK");return path;}
        if(borrowed.contains(old))throw new IOException("Idle retained mapping can be reopened, but must be released before resizing");
        String next=generation(),destination=create(next,requested);byte[] buffer=new byte[4*1024*1024];long copied=0;
        long nextKeepalive=67108864L;
        try(FileInputStream input=new FileInputStream(path);FileOutputStream output=new FileOutputStream(destination)){while(copied<size){int count=input.read(buffer,0,(int)Math.min(buffer.length,size-copied));if(count<0)throw new EOFException("Incomplete guest filesystem copy");output.write(buffer,0,count);copied+=count;if(copied>=nextKeepalive){api.getMethod("isImageMapped",String.class).invoke(images,old);System.out.println("GUEST_DATA_COPY_BYTES="+copied);nextKeepalive+=67108864L;}}output.getFD().sync();}
        System.out.println("GUEST_DATA_COPY_SYNCED_BEGIN_FSCK_RESIZE");
        command("/system/bin/e2fsck","-f","-p",destination);command("/system/bin/resize2fs",destination);Properties grown=inspect(destination);if(!info.getProperty("uuid").equals(grown.getProperty("uuid"))||Long.parseLong(grown.getProperty("bytes"))!=requested)throw new IOException("Grown guest filesystem verification failed");
        System.out.println("GUEST_DATA_RESIZE_VERIFIED_BEGIN_OLD_UNMAP");unmap(old);System.out.println("GUEST_DATA_OLD_UNMAPPED_BEGIN_COMMIT");commit(next,requested,grown.getProperty("uuid"),old);path=destination;size=requested;System.out.println("PERSISTENT_GUEST_DATA_GROWN="+next+"_BYTES="+size+"_PREVIOUS_RETAINED="+old);return path;
    }
    public long size(){return size;}
    @Override public void close()throws Exception{
        try{
        Exception failure=null;for(String name:new ArrayList<>(mapped))try{unmap(name);}catch(Exception error){failure=error;}
        for(String name:created)if(!name.equals(committedName)&&!mapped.contains(name))try{api.getMethod("deleteBackingImage",String.class).invoke(images,name);}catch(Exception error){failure=error;}
        if(failure!=null)throw failure;System.out.println(!borrowed.isEmpty()?"PERSISTENT_GUEST_DATA_BORROWED_MAPPING_LEFT_UNCHANGED":persistent?"PERSISTENT_GUEST_DATA_UNMAPPED_PRESERVED":"PRIVATE_EXT4_DATA_SESSION_UNMAPPED_AND_DELETED");
        }finally{try{if(lock.isValid())lock.release();}finally{lockFile.close();}}
    }
}
