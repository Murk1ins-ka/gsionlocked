package dev.tempgsi.controller;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.security.MessageDigest;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

final class GsiStore {
    static final class Image {
        final String name,sha256;final long bytes;
        Image(String name,String sha256,long bytes){this.name=name;this.sha256=sha256;this.bytes=bytes;}
    }
    static synchronized Image read(Context context,Uri uri,Consumer<String> output,BooleanSupplier cancelled)throws Exception{
        String name="system.img";long declared=-1;
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){if(!cursor.isNull(0))name=cursor.getString(0);if(!cursor.isNull(1))declared=cursor.getLong(1);}
        }
        name=name.replaceAll("[\\p{Cntrl}]","");if(name.length()>100)name=name.substring(0,100);
        File folder=new File(context.getFilesDir(),"gsi");if(!folder.isDirectory()&&!folder.mkdir())throw new IOException("Cannot create image storage.");
        File[] abandoned=folder.listFiles((dir,file)->file.startsWith("import-")&&file.endsWith(".part"));
        if(abandoned!=null)for(File part:abandoned)if(part.isFile()&&!part.delete())throw new IOException("Cannot remove an incomplete previous import.");
        long free=new android.os.StatFs(folder.getPath()).getAvailableBytes(),reserve=1073741824L;
        if(declared>GsiFormat.MAX_BYTES||declared>=0&&declared>free-reserve)throw new IOException("Not enough space to import this image while keeping 1 GiB free.");
        File temporary=File.createTempFile("import-",".part",folder);
        long total=0,next=64L*1024*1024;byte[] first=new byte[4096];MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try{
            try(InputStream raw=context.getContentResolver().openInputStream(uri)){
                if(raw==null)throw new IOException("Cannot open selected file.");
                try(BufferedInputStream in=new BufferedInputStream(raw,1024*1024);FileOutputStream out=new FileOutputStream(temporary)){
                    int filled=0,n;while(filled<first.length&&(n=in.read(first,filled,first.length-filled))!=-1)filled+=n;
                    if(filled!=4096)throw new IOException("Image header is incomplete.");
                    GsiFormat.validate(first,declared>=0?declared:4096);
                    digest.update(first);out.write(first);total=first.length;byte[] block=new byte[1024*1024];
                    while((n=in.read(block))!=-1){
                        if(cancelled.getAsBoolean())throw new IOException("Image import cancelled.");
                        total+=n;if(total>GsiFormat.MAX_BYTES||total>free-reserve)throw new IOException("Image exceeds the import size or free-space limit.");
                        out.write(block,0,n);digest.update(block,0,n);
                        if(total>=next){output.accept("Import: "+total/1048576+" MiB");next+=64L*1024*1024;}
                    }
                    out.getFD().sync();
                }
            }
            if(declared>=0&&total!=declared)throw new IOException("File size changed during import.");GsiFormat.validate(first,total);
            if(cancelled.getAsBoolean())throw new IOException("Image import cancelled.");
            StringBuilder sha=new StringBuilder();for(byte b:digest.digest())sha.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            Files.move(temporary.toPath(),new File(folder,"selected.img").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            output.accept("SHA-256: "+sha);return new Image(name,sha.toString(),total);
        }finally{if(temporary.exists()&&!temporary.delete())temporary.deleteOnExit();}
    }
    private GsiStore(){}
}
