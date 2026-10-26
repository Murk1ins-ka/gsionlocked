package dev.tempgsi.controller;

import java.io.*;
import java.util.*;

/** Read-only discovery from init declarations, never executes init commands.
 * Reference: platform/system/core/init/README.md (services and interface options).
 * Deliberately refuses ambiguous definitions rather than guessing override order. */
public final class HostComposer {
    public interface State { String service(String name)throws IOException; }
    public static final class Service {
        public final String name,executable,source,sourceSha;
        private final SortedSet<String> interfaces=new TreeSet<>();
        private boolean unsafe;
        Service(String name,String executable,String source,String sha){this.name=name;this.executable=executable;this.source=source;this.sourceSha=sha;}
        public String composerInterface(){
            if(interfaces.contains("android.hardware.graphics.composer3.IComposer/default"))return "android.hardware.graphics.composer3.IComposer/default";
            for(String version:new String[]{"2.4","2.3","2.2","2.1"}){
                String value="android.hardware.graphics.composer@"+version+"::IComposer/default";
                if(interfaces.contains(value))return value;
            }return "";
        }
    }
    private static List<String> tokens(String line)throws IOException {
        ArrayList<String> result=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quote=false,escape=false,active=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(escape){value.append(c=='n'?'\n':c=='r'?'\r':c=='t'?'\t':c);escape=false;active=true;}
            else if(c=='\\'){escape=true;active=true;}
            else if(c=='"'){quote=!quote;active=true;}
            else if(!quote&&(c==' '||c=='\t')){if(active){result.add(value.toString());value.setLength(0);active=false;}}
            else{value.append(c);active=true;}
        }
        if(escape||quote)throw new IOException("Unsupported unfinished init token");
        if(active)result.add(value.toString());return result;
    }
    public static List<Service> parse(String source,String text)throws IOException {
        if(text.length()>2*1024*1024||text.indexOf('\0')>=0)throw new IOException("Invalid init input");
        String digest=RuntimeBundle.hash(new ByteArrayInputStream(text.getBytes("UTF-8")));
        ArrayList<Service> result=new ArrayList<>();Service current=null;StringBuilder logical=new StringBuilder();
        for(String physical:text.split("\n",-1)){
            if(physical.endsWith("\r"))physical=physical.substring(0,physical.length()-1);
            // Init comments occupy whole lines. A '#' inside a token is literal.
            if(logical.length()==0&&physical.trim().startsWith("#"))continue;
            if(physical.endsWith("\\")){logical.append(physical,0,physical.length()-1).append(' ');if(logical.length()>65536)throw new IOException("Init logical line too long");continue;}
            logical.append(physical);if(logical.length()>65536)throw new IOException("Init logical line too long");
            List<String> words=tokens(logical.toString());logical.setLength(0);if(words.isEmpty())continue;
            String op=words.get(0);
            if(op.equals("service")){
                current=null;
                if(words.size()<3)throw new IOException("Malformed init service");
                String name=words.get(1),binary=words.get(2);
                if(!name.matches("[A-Za-z0-9_.-]{1,92}")||!binary.matches("/(?:vendor|odm|system|system_ext)/bin/(?:hw/)?[A-Za-z0-9_.@+-]+"))continue;
                current=new Service(name,binary,source,digest);result.add(current);
            }else if(op.equals("on")||op.equals("import")){current=null;}
            else if(current!=null){
                if(op.equals("interface")&&words.size()==3){
                    String iface=words.get(1).equals("aidl")?words.get(2):words.get(1)+"/"+words.get(2);
                    if(iface.matches("android\\.hardware\\.graphics\\.composer3\\.IComposer/default|android\\.hardware\\.graphics\\.composer@2\\.[1-4]::IComposer/default"))current.interfaces.add(iface);
                }
                if(op.equals("critical")||op.equals("reboot_on_failure")||op.equals("override"))current.unsafe=true;
            }
        }
        if(logical.length()!=0)throw new IOException("Unterminated init continuation");
        return result;
    }
    public static Service select(List<Service> services,State state)throws IOException {
        Map<String,Integer> definitions=new HashMap<>();for(Service item:services)definitions.put(item.name,definitions.getOrDefault(item.name,0)+1);
        Service selected=null;
        for(Service item:services){
            if(item.composerInterface().isEmpty())continue;
            if(!"running".equals(state.service(item.name)))continue;
            if(item.unsafe||definitions.get(item.name)!=1)throw new IOException("Ambiguous or critical active display composer: "+item.name);
            if(selected!=null)throw new IOException("Multiple active default display composers");selected=item;
        }
        if(selected==null)throw new IOException("No uniquely declared running display composer");return selected;
    }
    private HostComposer(){}
}
