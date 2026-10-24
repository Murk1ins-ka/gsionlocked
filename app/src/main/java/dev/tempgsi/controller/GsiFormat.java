package dev.tempgsi.controller;

import java.io.IOException;

/** Pure Java format checks, also exercised by the desktop tests. */
public final class GsiFormat {
    public static final long MAX_BYTES=16L*1024*1024*1024;
    public static void validate(byte[] header,long bytes)throws IOException{
        if(bytes<4096||bytes>MAX_BYTES||bytes%4096!=0)throw new IOException("Invalid image size. Select a raw ext4 .img (up to 16 GiB).");
        if(header.length<1082)throw new IOException("Image header is incomplete.");
        if((header[0]&255)==0x3a&&(header[1]&255)==0xff&&(header[2]&255)==0x26&&(header[3]&255)==0xed)throw new IOException("Sparse image. Convert it with simg2img before importing.");
        if((header[1080]&255)!=0x53||(header[1081]&255)!=0xef)throw new IOException("Select a raw ext4 .img. Extract .xz or .zip archives first.");
    }
    private GsiFormat(){}
}
