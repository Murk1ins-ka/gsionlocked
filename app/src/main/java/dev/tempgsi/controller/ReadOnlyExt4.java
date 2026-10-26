package dev.tempgsi.controller;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.util.*;

/** Userspace, read-only image reader. No mounts, native executables or host path resolution.
 * Layout reference: https://www.kernel.org/doc/html/latest/filesystems/ext4/ . */
public final class ReadOnlyExt4 implements Closeable {
    private final FileChannel channel;
    private final long bytes,blocks,inodes,blocksPerGroup,inodesPerGroup,descriptorTable;
    private final int blockSize,inodeSize,descriptorSize;
    private final boolean bit64,fileTypes;
    private static int u16(byte[] b,int at){return (b[at]&255)|((b[at+1]&255)<<8);}
    private static long u32(byte[] b,int at){return (long)u16(b,at)|((long)u16(b,at+2)<<16);}
    private void bounds(long at,long length)throws IOException{
        if(at<0||length<0||at>bytes||length>bytes-at)throw new IOException("ext4 reference outside image");
    }
    private byte[] read(long at,int length)throws IOException {
        byte[] result=new byte[length];read(at,result,0,length);return result;
    }
    private void read(long at,byte[] out,int offset,int length)throws IOException {
        bounds(at,length);ByteBuffer b=ByteBuffer.wrap(out,offset,length);int retries=0;
        while(b.hasRemaining()){
            int n=channel.read(b,at);if(n<0)throw new EOFException("Truncated ext4 image");
            if(n==0){if(++retries>10)throw new IOException("Image read made no progress");continue;}
            retries=0;at+=n;
        }
    }
    private long block(long number)throws IOException{
        if(number<0||number>=blocks)throw new IOException("ext4 block outside filesystem");return number*blockSize;
    }
    public ReadOnlyExt4(File image)throws IOException {
        channel=new FileInputStream(image).getChannel();bytes=channel.size();boolean ready=false;
        try{
            if(bytes<4096)throw new IOException("Image is too small");byte[] sb=read(1024,1024);
            if(u16(sb,56)!=0xef53)throw new IOException("Expected a raw ext4 image");
            long log=u32(sb,24);if(log>6)throw new IOException("Unsupported ext4 block size");blockSize=1024<<(int)log;
            long incompatible=u32(sb,96),allowed=0x2|0x40|0x80|0x100|0x200|0x400|0x2000|0x4000|0x8000|0x10000|0x20000;
            if((incompatible&~allowed)!=0)throw new IOException("Unsupported ext4 feature or journal recovery required");
            bit64=(incompatible&0x80)!=0;fileTypes=(incompatible&2)!=0;
            blocks=u32(sb,4)+(bit64?u32(sb,336)<<32:0);inodes=u32(sb,0);
            blocksPerGroup=u32(sb,32);inodesPerGroup=u32(sb,40);
            inodeSize=u32(sb,76)==0?128:u16(sb,88);descriptorSize=bit64?u16(sb,254):32;
            if(blocks<=0||blocks>bytes/blockSize||inodes<2||blocksPerGroup==0||inodesPerGroup==0||inodeSize<128||inodeSize>blockSize||(inodeSize&3)!=0||descriptorSize<32||descriptorSize>blockSize||(bit64&&descriptorSize<64))throw new IOException("Invalid ext4 geometry");
            long first=u32(sb,20);if(first!=(blockSize==1024?1:0))throw new IOException("Unexpected ext4 first data block");
            descriptorTable=(first+1)*blockSize;
            long groups=(blocks-first+blocksPerGroup-1)/blocksPerGroup;
            bounds(descriptorTable,groups*descriptorSize);
            if((inodes+inodesPerGroup-1)/inodesPerGroup>groups)throw new IOException("Invalid ext4 inode groups");
            ready=true;
        }finally{if(!ready)channel.close();}
    }
    public final class Node {
        public final long number,size;
        public final int type;
        private final long flags;
        private final byte[] inode;
        private Node(long number,byte[] inode)throws IOException{
            this.number=number;this.inode=inode;type=u16(inode,0)&0xf000;
            size=u32(inode,4)+(u32(inode,108)<<32);flags=u32(inode,32);
            if(size<0||size>GsiFormat.MAX_BYTES)throw new IOException("Image inode is too large");
            if((flags&(0x4|0x800|0x10000000))!=0)throw new IOException("Compressed, encrypted or inline-data inode is unsupported");
        }
        public boolean directory(){return type==0x4000;}
        public boolean regular(){return type==0x8000;}
        public boolean symlink(){return type==0xa000;}
        public InputStream open()throws IOException {
            if(!regular()&&!directory()&&!symlink())throw new IOException("Refusing a special-device inode");
            if(size==0)return new ByteArrayInputStream(new byte[0]);
            if(symlink()&&size<60&&(flags&0x80000)==0)return new ByteArrayInputStream(Arrays.copyOfRange(inode,40,40+(int)size));
            if((flags&0x80000)==0)throw new IOException("Legacy indirect-block image inode is unsupported");
            ArrayList<Extent> extents=new ArrayList<>();readExtents(Arrays.copyOfRange(inode,40,100),-1,extents,new HashSet<Long>());
            long previous=0;for(Extent extent:extents){if(extent.logical<previous)throw new IOException("Overlapping/out-of-order ext4 extents");previous=extent.logical+extent.count;}
            return new ExtentStream(size,extents);
        }
    }
    private Node inode(long number)throws IOException {
        if(number<1||number>inodes)throw new IOException("Invalid image inode number");
        long group=(number-1)/inodesPerGroup,index=(number-1)%inodesPerGroup;
        byte[] descriptor=read(descriptorTable+group*descriptorSize,descriptorSize);
        long table=u32(descriptor,8)+(bit64?u32(descriptor,40)<<32:0);
        long at=block(table)+index*inodeSize;
        if(at>blocks*blockSize-inodeSize)throw new IOException("Inode table outside filesystem");
        return new Node(number,read(at,inodeSize));
    }
    private static final class Extent {
        final long logical,physical,count;final boolean zero;
        Extent(long l,long p,long c,boolean z){logical=l;physical=p;count=c;zero=z;}
    }
    private void readExtents(byte[] value,int parentDepth,List<Extent> output,Set<Long> visited)throws IOException {
        if(value.length<12||u16(value,0)!=0xf30a)throw new IOException("Invalid ext4 extent header");
        int entries=u16(value,2),capacity=u16(value,4),depth=u16(value,6);
        if(depth>5||(parentDepth!=-1&&depth!=parentDepth-1)||entries>capacity||capacity>(value.length-12)/12)throw new IOException("Invalid ext4 extent tree");
        long last=-1;
        for(int n=0;n<entries;n++){
            int at=12+n*12;long logical=u32(value,at);if(logical<=last)throw new IOException("Unsorted extent index");last=logical;
            if(depth==0){
                int encoded=u16(value,at+4);boolean zero=encoded>32768;long count=zero?encoded-32768:encoded;
                long physical=u32(value,at+8)+((long)u16(value,at+6)<<32);
                if(count==0||physical==0||count>blocks-physical||physical>=blocks)throw new IOException("Invalid ext4 extent range");
                if(output.size()>=100000)throw new IOException("Too many extents in one file");output.add(new Extent(logical,physical,count,zero));
            }else{
                long leaf=u32(value,at+4)+((long)u16(value,at+8)<<32);
                if(!visited.add(leaf)||visited.size()>100000)throw new IOException("Cyclic/oversized ext4 extent tree");
                int before=output.size();readExtents(read(block(leaf),blockSize),depth,output,visited);
                if(output.size()>before&&output.get(before).logical<logical)throw new IOException("Extent child precedes its index");
            }
        }
    }
    private final class ExtentStream extends InputStream {
        final long size;final List<Extent> extents;long position;int current;
        ExtentStream(long size,List<Extent> extents){this.size=size;this.extents=extents;}
        @Override public int read()throws IOException{byte[] one=new byte[1];return read(one,0,1)==-1?-1:one[0]&255;}
        @Override public int read(byte[] out,int off,int length)throws IOException {
            if(off<0||length<0||off>out.length-length)throw new IndexOutOfBoundsException();if(length==0)return 0;if(position>=size)return -1;
            while(current<extents.size()&&(extents.get(current).logical+extents.get(current).count)*blockSize<=position)current++;
            Extent extent=current<extents.size()?extents.get(current):null;
            boolean gap=extent==null||position<extent.logical*blockSize;
            long until=gap?(extent==null?size:extent.logical*blockSize):(extent.logical+extent.count)*blockSize;
            int n=(int)Math.min(Math.min((long)length,size-position),until-position);
            if(n<=0)throw new IOException("Invalid extent stream boundary");
            if(gap||extent.zero)Arrays.fill(out,off,off+n,(byte)0);
            else ReadOnlyExt4.this.read(extent.physical*blockSize+(position-extent.logical*blockSize),out,off,n);
            position+=n;return n;
        }
    }
    private static String utf8(byte[] bytes,int off,int size)throws IOException {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,off,size)).toString();}
        catch(CharacterCodingException e){throw new IOException("Invalid UTF-8 in image directory",e);}
    }
    private SortedMap<String,Long> entries(Node directory)throws IOException {
        if(!directory.directory()||directory.size>64L*1024*1024||directory.size%blockSize!=0)throw new IOException("Invalid ext4 directory");
        SortedMap<String,Long> names=new TreeMap<>();try(InputStream in=directory.open()){
            for(long position=0;position<directory.size;position+=blockSize){
                byte[] b=new byte[blockSize];int got=0,n;while(got<b.length&&(n=in.read(b,got,b.length-got))!=-1)got+=n;if(got!=b.length)throw new EOFException("Truncated ext4 directory");
                for(int at=0;at<blockSize;){
                    if(blockSize-at<8)throw new IOException("Truncated ext4 directory record");
                    long ino=u32(b,at);int record=u16(b,at+4),size=fileTypes?b[at+6]&255:u16(b,at+6);
                    if(blockSize==65536)record=record==0||record==65535?65536:(record&65532)|((record&3)<<16);
                    if(record<8||(record&3)!=0||record>blockSize-at||size>255||size>record-8)throw new IOException("Invalid ext4 directory record");
                    if(ino!=0){
                        String name=utf8(b,at+8,size);if(name.isEmpty()||name.indexOf('/')>=0||name.indexOf('\0')>=0||ino>inodes)throw new IOException("Invalid image directory name/inode");
                        if(!name.equals(".")&&!name.equals("..")&&names.put(name,ino)!=null)throw new IOException("Duplicate image directory entry");
                    }
                    at+=record;
                }
            }
        }return names;
    }
    private static List<String> normalize(String path)throws IOException {
        if(path.length()>4096||path.indexOf('\0')>=0)throw new IOException("Invalid image path");
        ArrayList<String> result=new ArrayList<>();for(String part:path.split("/")){
            if(part.isEmpty()||part.equals("."))continue;
            if(part.equals("..")){if(!result.isEmpty())result.remove(result.size()-1);}else result.add(part);
        }if(result.size()>256)throw new IOException("Image path is too deep");return result;
    }
    private Node lookup(List<String> parts,int links)throws IOException {
        if(links>40)throw new IOException("Too many image symlinks");Node node=inode(2);StringBuilder prefix=new StringBuilder();
        for(int i=0;i<parts.size();i++){
            Long child=entries(node).get(parts.get(i));if(child==null)throw new FileNotFoundException("Image path not found: /"+String.join("/",parts));
            node=inode(child);
            if(node.symlink()){
                if(node.size==0||node.size>4096)throw new IOException("Invalid image symlink");byte[] target=new byte[(int)node.size];
                try(InputStream in=node.open()){int at=0,n;while(at<target.length&&(n=in.read(target,at,target.length-at))!=-1)at+=n;if(at!=target.length)throw new EOFException("Truncated image symlink");}
                String link=utf8(target,0,target.length),rest=String.join("/",parts.subList(i+1,parts.size()));
                return lookup(normalize((link.startsWith("/")?"":prefix.toString())+link+"/"+rest),links+1);
            }
            prefix.append(parts.get(i)).append('/');
        }return node;
    }
    public Node lookup(String imagePath)throws IOException{return lookup(normalize(imagePath),0);}
    public SortedSet<String> list(String imagePath)throws IOException{return new TreeSet<>(entries(lookup(imagePath)).keySet());}
    public int blockSize(){return blockSize;}
    @Override public void close()throws IOException{channel.close();}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Usage: ReadOnlyExt4 image.img /image/directory");
        try(ReadOnlyExt4 image=new ReadOnlyExt4(new File(args[0]))){for(String name:image.list(args[1]))System.out.println(name);}
    }
}
