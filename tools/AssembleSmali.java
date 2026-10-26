import java.io.*;
import java.nio.file.*;
import java.util.*;
import brut.androlib.smali.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;

/** Offline build helper for the pinned APKTool 3.0.3 library API. */
public final class AssembleSmali {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("smali-directory output.dex");
        Path input=Paths.get(args[0]);File output=new File(args[1]);if(output.exists())throw new IOException("Existing output is preserved");
        // APKTool 3.0.3's writer selects DEX041 at API35 but still emits a
        // 112-byte legacy header. Android requires 120 for DEX041 containers.
        // API34 retains the required opcode set and emits a legacy DEX header.
        DexBuilder dex=new DexBuilder(new Opcodes(34));SmaliBuilder assembler=new SmaliBuilder(34);int count=0;
        try(java.util.stream.Stream<Path> paths=Files.walk(input)){
            for(Path path:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".smali")).sorted()::iterator){
                if(!assembler.buildFile(path.toFile(),dex))throw new IOException("Smali assembly failed: "+path.getFileName());count++;
            }
        }
        if(count==0)throw new IOException("No smali sources");
        FileDataStore store=new FileDataStore(output);try{dex.writeTo(store);}finally{store.raf.close();}
        try(RandomAccessFile check=new RandomAccessFile(output,"r")){
            byte[] magic=new byte[8];check.readFully(magic);
            String version=new String(magic,java.nio.charset.StandardCharsets.US_ASCII);
            check.seek(32);int size=Integer.reverseBytes(check.readInt()),header=Integer.reverseBytes(check.readInt());
            if(!version.matches("dex\\n0(?:35|37|38|39|40)\\u0000")||header!=112||size!=check.length())throw new IOException("Assembler emitted an unsupported or inconsistent DEX header");
        }
        System.out.println("ASSEMBLED_CLASSES="+count);
    }
}
