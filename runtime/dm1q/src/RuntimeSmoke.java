public final class RuntimeSmoke {
    public static void main(String[] args) {
        long sum=0;for(int i=1;i<=1000;i++)sum+=i;
        if(sum!=500500)throw new AssertionError("Computation failed");
        System.out.println("LINEAGE_ART_JAVA_EXECUTED vm="+System.getProperty("java.vm.version")+" sum="+sum);
        if(args.length>0&&args[0].equals("--android-runtime"))try {
            Class.forName("android.os.Looper").getMethod("prepare").invoke(null);
            Object uptime=Class.forName("android.os.SystemClock").getMethod("uptimeMillis").invoke(null);
            System.out.println("LINEAGE_ANDROID_RUNTIME_JNI_EXECUTED uptime="+uptime);
            try(java.io.FileOutputStream output=new java.io.FileOutputStream("/data/local/tmp/android-runtime.ok")){output.write("ANDROID_RUNTIME_JNI_OK\n".getBytes("UTF-8"));}
            if(args.length>1&&args[1].equals("--egl")){
                Class<?> egl=Class.forName("android.opengl.EGL14"),displayType=Class.forName("android.opengl.EGLDisplay");
                Object display=egl.getMethod("eglGetDisplay",int.class).invoke(null,0);
                int[] version=new int[2];
                boolean initialized=(Boolean)egl.getMethod("eglInitialize",displayType,int[].class,int.class,int[].class,int.class).invoke(null,display,version,0,version,1);
                if(!initialized)throw new IllegalStateException("EGL initialize failed: "+egl.getMethod("eglGetError").invoke(null));
                Object vendor=egl.getMethod("eglQueryString",displayType,int.class).invoke(null,display,0x3053);
                System.out.println("LINEAGE_VENDOR_EGL_INITIALIZED vendor="+vendor+" version="+version[0]+"."+version[1]);
                egl.getMethod("eglTerminate",displayType).invoke(null,display);
                Class.forName("EglSmoke").getMethod("run").invoke(null);
                try(java.io.FileOutputStream output=new java.io.FileOutputStream("/data/local/tmp/vendor-egl.ok")){output.write("EGL_INITIALIZED\n".getBytes("UTF-8"));}
            }
        }catch(Exception error){throw new RuntimeException(error);}
    }
}
