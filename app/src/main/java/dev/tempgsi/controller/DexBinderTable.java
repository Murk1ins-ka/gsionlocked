package dev.tempgsi.controller;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Reads Binder transaction IDs and method signatures without loading host code.
 * Format reference: https://source.android.com/docs/core/runtime/dex-format . */
public final class DexBinderTable {
    public static final class AbsentInterface extends IOException {AbsentInterface(){super("Binder interface is not defined in this JAR");}}
    public static final class Entry {
        public final int code;public final String signature;
        Entry(int code,String signature){this.code=code;this.signature=signature;}
        public String toString(){return code+" "+signature;}
    }
    private final byte[] data;
    private final int base,headerBytes,next;
    private final int stringCount,stringOffset,typeCount,typeOffset,protoCount,protoOffset,
        fieldCount,fieldOffset,methodCount,methodOffset,classCount,classOffset;
    private final Map<Integer,String> strings=new HashMap<>();
    private static final int LIMIT=256*1024*1024;
    private void check(long offset,long bytes)throws IOException {
        if(offset<0||bytes<0||offset>data.length||bytes>data.length-offset)throw new IOException("DEX offset outside file");
    }
    private int u1(int off)throws IOException{check(off,1);return data[off]&255;}
    private int u2(int off)throws IOException{check(off,2);return (data[off]&255)|((data[off+1]&255)<<8);}
    private long u4(int off)throws IOException{check(off,4);return (long)u2(off)|((long)u2(off+2)<<16);}
    private int offset(int off)throws IOException{long x=u4(off);if(x>data.length||(x!=0&&x<base+headerBytes))throw new IOException("DEX offset outside logical data");return (int)x;}
    private int count(int off)throws IOException{long x=u4(off);if(x>2000000)throw new IOException("DEX table too large");return (int)x;}
    private void table(int count,int off,int width)throws IOException{if(count>0&&off<base+headerBytes)throw new IOException("DEX table overlaps header");check(off,(long)count*width);}
    private DexBinderTable(byte[] bytes,int start)throws IOException {
        data=bytes;base=start;if(data.length<112||data.length>LIMIT)throw new IOException("Invalid DEX size");check(base,112);
        String magic=new String(data,base,8,"ISO-8859-1");
        if(!magic.matches("dex\\n0(?:35|37|38|39|40|41)\u0000"))throw new IOException("Unsupported DEX format");
        boolean container=magic.substring(4,7).equals("041");headerBytes=container?120:112;check(base,headerBytes);
        long size=u4(base+32);if(size<headerBytes||size>data.length-base||u4(base+36)!=headerBytes||u4(base+40)!=0x12345678L)throw new IOException("Invalid DEX header");next=base+(int)size;
        if(container){if(u4(base+112)!=data.length||u4(base+116)!=base)throw new IOException("Invalid DEX container bounds");}
        else if(base!=0||size!=data.length)throw new IOException("Invalid standalone DEX bounds");
        java.util.zip.Adler32 checksum=new java.util.zip.Adler32();checksum.update(data,base+12,(int)size-12);
        if(checksum.getValue()!=u4(base+8))throw new IOException("DEX checksum differs");
        try{java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-1");sha.update(data,base+32,(int)size-32);if(!Arrays.equals(sha.digest(),Arrays.copyOfRange(data,base+12,base+32)))throw new IOException("DEX signature differs");}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
        stringCount=count(base+56);stringOffset=offset(base+60);typeCount=count(base+64);typeOffset=offset(base+68);
        protoCount=count(base+72);protoOffset=offset(base+76);fieldCount=count(base+80);fieldOffset=offset(base+84);
        methodCount=count(base+88);methodOffset=offset(base+92);classCount=count(base+96);classOffset=offset(base+100);
        table(stringCount,stringOffset,4);table(typeCount,typeOffset,4);table(protoCount,protoOffset,12);
        table(fieldCount,fieldOffset,8);table(methodCount,methodOffset,8);table(classCount,classOffset,32);
    }
    private final class Cursor {
        int at;
        Cursor(int at)throws IOException{check(at,1);this.at=at;}
        int byteValue()throws IOException{return u1(at++);}
        int uleb()throws IOException{
            long value=0;for(int n=0;n<5;n++){int b=byteValue();value|=(long)(b&127)<<(7*n);if((b&128)==0){if(value>Integer.MAX_VALUE)throw new IOException("DEX ULEB overflow");return (int)value;}}
            throw new IOException("Invalid DEX ULEB");
        }
        Long encoded(int depth)throws IOException{
            if(depth>16)throw new IOException("Nested DEX value limit");
            int header=byteValue(),kind=header&31,arg=header>>>5;
            if(kind==0x1e){if(arg!=0)throw new IOException("Invalid DEX null");return null;}
            if(kind==0x1f){if(arg>1)throw new IOException("Invalid DEX boolean");return null;}
            if(kind==0x1c||kind==0x1d){
                if(arg!=0)throw new IOException("Invalid DEX container value");
                if(kind==0x1d)uleb();int length=uleb();if(length>100000)throw new IOException("DEX value array too large");
                for(int n=0;n<length;n++){if(kind==0x1d)uleb();encoded(depth+1);}return null;
            }
            int max;
            switch(kind){case 0:max=0;break;case 2:case 3:max=1;break;case 4:case 0x10:case 0x15:case 0x16:case 0x17:case 0x18:case 0x19:case 0x1a:case 0x1b:max=3;break;case 6:case 0x11:max=7;break;default:throw new IOException("Unknown DEX encoded value");}
            if(arg>max)throw new IOException("Invalid DEX value width");
            long value=0;for(int n=0;n<=arg;n++)value|=(long)byteValue()<<(8*n);
            if(kind==4){int bits=8*(arg+1);if(bits<64&&(value&(1L<<(bits-1)))!=0)value|=(-1L)<<bits;return value;}
            return null;
        }
    }
    private String string(int index)throws IOException {
        if(index<0||index>=stringCount)throw new IOException("Invalid DEX string index");
        String cached=strings.get(index);if(cached!=null)return cached;
        Cursor c=new Cursor(offset(stringOffset+index*4));int units=c.uleb(),start=c.at;
        while(c.byteValue()!=0)if(c.at-start>65535)throw new IOException("DEX string too large");
        int length=c.at-start-1;byte[] encoded=new byte[length+2];encoded[0]=(byte)(length>>>8);encoded[1]=(byte)length;
        System.arraycopy(data,start,encoded,2,length);
        String value=new DataInputStream(new ByteArrayInputStream(encoded)).readUTF();
        if(value.length()!=units)throw new IOException("DEX string length differs");strings.put(index,value);return value;
    }
    private String type(int index)throws IOException {
        if(index<0||index>=typeCount)throw new IOException("Invalid DEX type index");return string((int)u4(typeOffset+index*4));
    }
    private String signature(int index)throws IOException {
        if(index<0||index>=protoCount)throw new IOException("Invalid DEX prototype index");int p=protoOffset+index*12;
        StringBuilder out=new StringBuilder("(");int parameters=offset(p+8);
        if(parameters!=0){int n=count(parameters);table(n,parameters+4,2);for(int i=0;i<n;i++)out.append(type(u2(parameters+4+i*2)));}
        return out.append(')').append(type((int)u4(p+4))).toString();
    }
    private void read(String descriptor,SortedMap<String,Integer> transactions,SortedMap<String,String> methods,Set<String> definitions)throws IOException {
        String stub=descriptor.substring(0,descriptor.length()-1)+"$Stub;";
        for(int i=0;i<classCount;i++){
            int p=classOffset+i*32;String owner=type((int)u4(p));
            if(!owner.equals(stub)&&!owner.equals(descriptor))continue;
            if(!definitions.add(owner))throw new IOException("Duplicate Binder class definition");
            if(owner.equals(descriptor)){
                for(int j=0;j<methodCount;j++){int m=methodOffset+j*8;if(!type(u2(m)).equals(descriptor))continue;
                    String name=string((int)u4(m+4)),sig=signature(u2(m+2));if(name.startsWith("<"))continue;
                    String old=methods.put(name,sig);if(old!=null&&!old.equals(sig))throw new IOException("Ambiguous Binder method: "+name);
                }
            }else {
                int classData=offset(p+24),values=offset(p+28);if(classData==0)throw new IOException("Binder stub has no fields");
                Cursor fields=new Cursor(classData);int staticCount=fields.uleb();fields.uleb();fields.uleb();fields.uleb();
                if(staticCount>fieldCount)throw new IOException("Invalid DEX static field count");
                Cursor encoded=values==0?null:new Cursor(values);int valueCount=encoded==null?0:encoded.uleb();
                if(valueCount>staticCount)throw new IOException("Invalid DEX static value count");int fieldIndex=0;
                for(int j=0;j<staticCount;j++){
                    int delta=fields.uleb();if((j>0&&delta==0)||delta>fieldCount-fieldIndex-1)throw new IOException("Invalid DEX field delta");fieldIndex+=delta;
                    int access=fields.uleb(),f=fieldOffset+fieldIndex*8;String name=string((int)u4(f+4));
                    Long value=j<valueCount?encoded.encoded(0):Long.valueOf(0);
                    if(!name.startsWith("TRANSACTION_"))continue;
                    if(!type(u2(f)).equals(stub)||!type(u2(f+2)).equals("I")||(access&0x18)!=0x18||value==null||value<=0||value>0x00ffffff)
                        throw new IOException("Invalid Binder transaction constant: "+name);
                    if(transactions.put(name.substring(12),value.intValue())!=null)throw new IOException("Duplicate Binder transaction");
                }
            }
        }
    }
    public static SortedMap<String,Entry> read(File jar,String descriptor)throws IOException {
        if(!descriptor.matches("L[A-Za-z0-9_$/]+;"))throw new IOException("Invalid Binder descriptor");
        SortedMap<String,Integer> codes=new TreeMap<>();SortedMap<String,String> signatures=new TreeMap<>();Set<String> definitions=new HashSet<>();
        int dexCount=0;try(ZipFile zip=new ZipFile(jar)){
            Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> all=zip.entries();
            while(all.hasMoreElements()){
                ZipEntry e=all.nextElement();String name=e.getName();if(!name.matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex"))continue;
                if(!names.add(name)||++dexCount>256||e.getSize()<112||e.getSize()>LIMIT)throw new IOException("Invalid framework DEX entry");
                ByteArrayOutputStream out=new ByteArrayOutputStream((int)e.getSize());try(InputStream in=zip.getInputStream(e)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(out.size()>LIMIT-n)throw new IOException("Framework DEX exceeds size limit");out.write(b,0,n);}}
                if(out.size()!=e.getSize())throw new IOException("Truncated framework DEX");
                byte[] bytes=out.toByteArray();int start=0,logical=0;
                while(start<bytes.length){if(++logical>256)throw new IOException("Too many logical DEX headers");DexBinderTable dex=new DexBinderTable(bytes,start);dex.read(descriptor,codes,signatures,definitions);start=dex.next;}
            }
        }
        if(dexCount==0)throw new IOException("Framework JAR has no readable DEX; an OAT/VDEX reader would be required");
        if(definitions.isEmpty())throw new AbsentInterface();
        if(!definitions.contains(descriptor)||!definitions.contains(descriptor.substring(0,descriptor.length()-1)+"$Stub;")||codes.isEmpty())throw new IOException("Binder definitions absent from framework JAR");
        SortedMap<String,Entry> result=new TreeMap<>();Set<Integer> seen=new HashSet<>();
        for(Map.Entry<String,Integer> c:codes.entrySet()){
            String sig=signatures.get(c.getKey());if(sig==null||!seen.add(c.getValue()))throw new IOException("Binder transaction/signature mismatch: "+c.getKey());
            result.put(c.getKey(),new Entry(c.getValue(),sig));
        }
        return result;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Usage: DexBinderTable framework.jar Landroid/os/IInstalld;");
        for(Map.Entry<String,Entry> e:read(new File(args[0]),args[1]).entrySet())System.out.println(e.getKey()+" "+e.getValue());
    }
}
