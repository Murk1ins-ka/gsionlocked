import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.IBinder;
import android.system.Os;
import android.system.StructStat;
import android.view.Surface;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/** Standalone diagnostic layer on the PRIVATE SurfaceFlinger; no WindowManager needed. */
public final class BootLogOverlay {
    static final int WIDTH=1080, HEIGHT=2340, ROWS=94;
    static final ArrayDeque<String> lines=new ArrayDeque<>();
    static volatile Set<Integer> privatePids=Collections.emptySet();
    static volatile boolean fatal=false;
    static PrintWriter journal;
    static final Pattern PID=Pattern.compile("^\\d\\d-\\d\\d\\s+\\S+\\s+(\\d+)\\s+.*");
    static final Class<?> SC;
    static final Class<?> BUILDER;
    static final Class<?> TRANSACTION;
    static final Class<?> BLAST;
    static Constructor<?> blastConstructor;
    static Method blastUpdate;
    static {try {
        ClassLoader loader=BootLogOverlay.class.getClassLoader();
        SC=Class.forName("android.view.SurfaceControl",false,loader);
        BUILDER=Class.forName("android.view.SurfaceControl$Builder",false,loader);
        TRANSACTION=Class.forName("android.view.SurfaceControl$Transaction",false,loader);
        BLAST=Class.forName("android.graphics.BLASTBufferQueue",false,loader);
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static void checkApi()throws Exception {
        BUILDER.getConstructor();BUILDER.getMethod("setName",String.class);
        BUILDER.getMethod("setBLASTLayer");BUILDER.getMethod("build");
        TRANSACTION.getConstructor();TRANSACTION.getMethod("setLayer",SC,int.class);
        TRANSACTION.getMethod("setLayerStack",SC,int.class);TRANSACTION.getMethod("show",SC);
        TRANSACTION.getMethod("hide",SC);TRANSACTION.getMethod("apply");
        try {
            blastConstructor=BLAST.getConstructor(String.class,boolean.class);
            blastUpdate=BLAST.getMethod("update",SC,int.class,int.class,int.class);
        }catch(NoSuchMethodException missing){
            blastUpdate=null;
            try{blastConstructor=BLAST.getConstructor(String.class,SC,int.class,int.class,int.class);}
            catch(NoSuchMethodException fiveMissing){blastConstructor=BLAST.getConstructor(String.class,SC,int.class,int.class,int.class,boolean.class);}
        }
        BLAST.getMethod("createSurface");BLAST.getMethod("destroy");SC.getMethod("release");
    }
    static synchronized void append(String line){
        if(line.length()>2000)line=line.substring(0,2000);
        journal.println(line);journal.flush();
        fatal|=line.contains("FATAL EXCEPTION")||line.contains("Fatal signal")||line.contains("BOOT FAILURE");
        // Wrap instead of losing the part of a message containing the error.
        for(int start=0;start<line.length();start+=96){
            lines.addLast(line.substring(start,Math.min(start+96,line.length())));
            while(lines.size()>ROWS)lines.removeFirst();
        }
    }
    static Set<Integer> scanPrivatePids()throws Exception {
        StructStat root=Os.stat("/");Set<Integer> pids=new HashSet<>();
        File[] entries=new File("/proc").listFiles();if(entries==null)return pids;
        for(File entry:entries)try {
            int pid=Integer.parseInt(entry.getName());StructStat candidate=Os.stat(entry.getPath()+"/root");
            if(candidate.st_dev==root.st_dev&&candidate.st_ino==root.st_ino)pids.add(pid);
        }catch(Exception ignored){}
        return pids;
    }
    public static void main(String[] args){
        try{run(args);}catch(Throwable error){
            error.printStackTrace();
            for(Constructor<?> constructor:BLAST.getDeclaredConstructors())System.err.println("AVAILABLE_BLAST_CONSTRUCTOR="+constructor);
            System.exit(1);
        }
    }
    static void run(String[] args)throws Exception {
        checkApi();
        // app_process does not perform Zygote's normal font preload.
        Method fontPreload=Typeface.class.getDeclaredMethod("loadPreinstalledSystemFontMap");
        fontPreload.setAccessible(true);fontPreload.invoke(null);
        Paint fontCheck=new Paint();fontCheck.setTypeface(Typeface.MONOSPACE);
        if(args.length==1&&args[0].equals("--check-api")){
            System.out.println("PRIVATE_PHONE_LOG_LAYER_API_AND_FONTS_AVAILABLE");return;
        }
        if(args.length!=0)throw new IllegalArgumentException("Unexpected arguments");
        journal=new PrintWriter(new OutputStreamWriter(new FileOutputStream("/data/local/tmp/phone-boot.log",true),"UTF-8"));
        append("gsionlocked // запуск EvoX");
        append("Private Binder / temporary data; waiting for GSI SurfaceFlinger");
        Class<?> manager=Class.forName("android.os.ServiceManager");
        Method check=manager.getMethod("checkService",String.class);
        IBinder sf=null;
        for(int tries=0;tries<100&&sf==null;tries++){sf=(IBinder)check.invoke(null,"SurfaceFlinger");if(sf==null)Thread.sleep(250);}
        if(sf==null)throw new IllegalStateException("Private SurfaceFlinger not ready");
        privatePids=scanPrivatePids();
        Process capture=new ProcessBuilder("/system/bin/logcat","-b","main","-b","system","-b","crash","-v","threadtime","-T","1").redirectErrorStream(true).start();
        Thread reader=new Thread(()->{
            try(BufferedReader input=new BufferedReader(new InputStreamReader(capture.getInputStream(),"UTF-8"))){
                String line;while((line=input.readLine())!=null){Matcher match=PID.matcher(line);
                    if(match.matches()&&privatePids.contains(Integer.valueOf(match.group(1))))append(line);
                }
            }catch(Exception error){append("Log reader: "+error);}
        },"GsiBootLogReader");reader.setDaemon(true);reader.start();
        Object layer=null,queue=null,transaction=null;Surface surface=null;
        try {
            Object builder=BUILDER.getConstructor().newInstance();
            BUILDER.getMethod("setName",String.class).invoke(builder,"gsionlocked startup");
            BUILDER.getMethod("setBLASTLayer").invoke(builder);layer=BUILDER.getMethod("build").invoke(builder);
            if(blastUpdate!=null){queue=blastConstructor.newInstance("gsionlocked startup",false);blastUpdate.invoke(queue,layer,WIDTH,HEIGHT,1);}
            else if(blastConstructor.getParameterTypes().length==6)queue=blastConstructor.newInstance("gsionlocked startup",layer,WIDTH,HEIGHT,1,true);
            else queue=blastConstructor.newInstance("gsionlocked startup",layer,WIDTH,HEIGHT,1);
            surface=(Surface)BLAST.getMethod("createSurface").invoke(queue);
            transaction=TRANSACTION.getConstructor().newInstance();
            TRANSACTION.getMethod("setLayerStack",SC,int.class).invoke(transaction,layer,0);
            TRANSACTION.getMethod("setLayer",SC,int.class).invoke(transaction,layer,2000000);
            TRANSACTION.getMethod("show",SC).invoke(transaction,layer);TRANSACTION.getMethod("apply").invoke(transaction);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(21);
            append("Graphics buffer, allocator and private display connected");
            Bitmap wallpaper=BitmapFactory.decodeFile("/data/local/tmp/gsionlocked-wallpaper.png");
            RectF wallpaperBounds=new RectF(0,0,WIDTH,HEIGHT);
            if(wallpaper!=null){float scale=Math.max((float)WIDTH/wallpaper.getWidth(),(float)HEIGHT/wallpaper.getHeight());float w=wallpaper.getWidth()*scale,h=wallpaper.getHeight()*scale;wallpaperBounds.set((WIDTH-w)/2,(HEIGHT-h)/2,(WIDTH+w)/2,(HEIGHT+h)/2);}
            boolean visible=true,bootRecorded=false;
            for(int frame=0;frame<320;frame++){
                if(frame%4==0)privatePids=scanPrivatePids();
                if(visible){Canvas canvas=surface.lockCanvas(null);
                try {
                    canvas.drawColor(Color.BLACK);
                    if(wallpaper!=null)canvas.drawBitmap(wallpaper,null,wallpaperBounds,null);
                    canvas.drawColor(Color.argb(172,0,0,0));
                    paint.setTextSize(18);paint.setColor(Color.WHITE);
                    synchronized(BootLogOverlay.class){
                        int row=0,total=lines.size();
                        for(String line:lines){paint.setAlpha(110+(total<=1?145:145*row/(total-1)));canvas.drawText(line,20,30+row++*24,paint);}
                    }
                    paint.setAlpha(255);
                }finally{surface.unlockCanvasAndPost(canvas);}}
                if(frame==0)System.out.println("PRIVATE_PHONE_BOOT_LOG_FIRST_FRAME_POSTED");
                // Give SystemUI the whole display once the actual guest reports boot completion.
                String completed=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"sys.boot_completed");
                if("1".equals(completed)&&!bootRecorded){
                    bootRecorded=true;append("Guest boot completed; stopping startup log collector");
                    if(visible){TRANSACTION.getMethod("hide",SC).invoke(transaction,layer);TRANSACTION.getMethod("apply").invoke(transaction);visible=false;}
                }
                if(bootRecorded)break;
                Thread.sleep(250);
            }
        }finally{
            capture.destroy();
            if(transaction!=null&&layer!=null){TRANSACTION.getMethod("hide",SC).invoke(transaction,layer);TRANSACTION.getMethod("apply").invoke(transaction);}
            if(surface!=null)surface.release();if(queue!=null)BLAST.getMethod("destroy").invoke(queue);if(layer!=null)SC.getMethod("release").invoke(layer);
            if(transaction instanceof AutoCloseable)((AutoCloseable)transaction).close();journal.close();
        }
    }
}
