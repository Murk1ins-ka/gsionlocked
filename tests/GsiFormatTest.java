import dev.tempgsi.controller.GsiFormat;
import java.io.IOException;

public class GsiFormatTest {
    static void rejected(byte[] header,long size)throws Exception{
        try{GsiFormat.validate(header,size);throw new AssertionError("Invalid image accepted");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception{
        byte[] ext4=new byte[4096];ext4[1080]=0x53;ext4[1081]=(byte)0xef;
        GsiFormat.validate(ext4,3863474176L);
        rejected(new byte[4096],3863474176L);
        rejected(new byte[100],4096);
        rejected(ext4,4097);
        rejected(ext4,GsiFormat.MAX_BYTES+4096);
        byte[] sparse=ext4.clone();sparse[0]=0x3a;sparse[1]=(byte)0xff;sparse[2]=0x26;sparse[3]=(byte)0xed;
        rejected(sparse,4096);
        System.out.println("PASS: ext4, sparse, truncated, unaligned and oversized images");
    }
}
