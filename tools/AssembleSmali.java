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
        DexBuilder dex=new DexBuilder(new Opcodes(35));SmaliBuilder assembler=new SmaliBuilder(35);int count=0;
        try(java.util.stream.Stream<Path> paths=Files.walk(input)){
            for(Path path:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".smali")).sorted()::iterator){
                if(!assembler.buildFile(path.toFile(),dex))throw new IOException("Smali assembly failed: "+path.getFileName());count++;
            }
        }
        if(count==0)throw new IOException("No smali sources");
        FileDataStore store=new FileDataStore(output);try{dex.writeTo(store);}finally{store.raf.close();}
        System.out.println("ASSEMBLED_CLASSES="+count);
    }
}
