import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaPlayer;
import android.os.SystemClock;
import java.io.File;
import java.io.RandomAccessFile;

/** Explicit playback diagnostics. --state is silent and does not change settings. */
public final class MediaAudioProbe {
    private static final int RATE=48000, FRAMES=RATE;
    private static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private static short[] signal(){
        short[] pcm=new short[FRAMES];
        for(int i=0;i<pcm.length;i++){
            double envelope=Math.min(1.0,Math.min(i/(RATE*.02),(FRAMES-1-i)/(RATE*.02)));
            pcm[i]=(short)(Math.sin(2*Math.PI*440*i/RATE)*4096*envelope);
        }
        return pcm;
    }
    private static void pcm()throws Exception{
        AudioTrack track=new AudioTrack.Builder().setAudioAttributes(attributes())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(FRAMES*2).build();
        try{
            if(track.getState()==AudioTrack.STATE_UNINITIALIZED)throw new IllegalStateException("AudioTrack not initialized");
            short[] data=signal();int count=track.write(data,0,data.length);
            if(count!=data.length)throw new IllegalStateException("PCM short write "+count);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Static PCM data not initialized after write");
            track.play();Thread.sleep(1200);
            AudioDeviceInfo routed=track.getRoutedDevice();
            System.out.println("PCM_HEAD_FRAMES="+track.getPlaybackHeadPosition()+" UNDERRUNS="+track.getUnderrunCount()+" ROUTE="+(routed==null?"none":routed.getType()));
            if(track.getPlaybackHeadPosition()<FRAMES)throw new IllegalStateException("PCM playback stalled");
            System.out.println("PCM_FRAMES_CONSUMED_AUDIBILITY_REQUIRES_USER_CONFIRMATION");
        }finally{track.release();}
    }
    private static void little(RandomAccessFile out,int value,int bytes)throws Exception{for(int i=0;i<bytes;i++)out.write((value>>>(i*8))&255);}
    private static void wav()throws Exception{
        File directory=new File("/data/system/media-probe135");
        if(!directory.isDirectory()&&!directory.mkdir())throw new java.io.IOException("Cannot create own media probe directory");
        File file=File.createTempFile("media-probe-", ".wav",directory);
        MediaPlayer player=new MediaPlayer();
        try{
            try(RandomAccessFile out=new RandomAccessFile(file,"rw")){
                out.writeBytes("RIFF");little(out,36+FRAMES*2,4);out.writeBytes("WAVEfmt ");little(out,16,4);little(out,1,2);little(out,1,2);
                little(out,RATE,4);little(out,RATE*2,4);little(out,2,2);little(out,16,2);out.writeBytes("data");little(out,FRAMES*2,4);
                for(short sample:signal())little(out,sample&65535,2);
            }
            player.setAudioAttributes(attributes());player.setDataSource(file.getAbsolutePath());player.prepare();
            System.out.println("WAV_PREPARED_DURATION_MS="+player.getDuration());player.start();
            long end=SystemClock.elapsedRealtime()+3000;
            do{Thread.sleep(100);}while(player.isPlaying()&&SystemClock.elapsedRealtime()<end);
            System.out.println("WAV_POSITION_MS="+player.getCurrentPosition());
            if(player.isPlaying()||player.getCurrentPosition()<800)throw new IllegalStateException("WAV playback stalled");
            System.out.println("WAV_PLAYBACK_COMPLETE_AUDIBILITY_REQUIRES_USER_CONFIRMATION");
        }finally{player.release();if(!file.delete())System.err.println("Could not remove own WAV probe file");}
    }
    @SuppressWarnings("deprecation")
    public static void main(String[] args){try{
        if(args.length!=1||!(args[0].equals("--state")||args[0].equals("--codecs")||args[0].equals("--pcm")||args[0].equals("--wav")))throw new IllegalArgumentException("Use --state, --codecs, --pcm or --wav; playback must be explicit");
        if(args[0].equals("--codecs")){
            for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
                if(!info.isEncoder())System.out.println("DECODER="+info.getName()+" TYPES="+java.util.Arrays.toString(info.getSupportedTypes()));
            System.exit(0);return;
        }
        android.os.Looper.prepareMainLooper();
        Class<?> activity=Class.forName("android.app.ActivityThread");Object thread=activity.getMethod("systemMain").invoke(null);
        Context context=(Context)activity.getMethod("getSystemContext").invoke(thread);AudioManager audio=context.getSystemService(AudioManager.class);
        System.out.println("AUDIO_MODE="+audio.getMode()+" RINGER_MODE="+audio.getRingerMode());
        for(int stream:new int[]{AudioManager.STREAM_MUSIC,AudioManager.STREAM_RING,AudioManager.STREAM_NOTIFICATION,AudioManager.STREAM_ALARM})
            System.out.println("STREAM="+stream+" INDEX="+audio.getStreamVolume(stream)+" MAX="+audio.getStreamMaxVolume(stream)+" MUTED="+audio.isStreamMute(stream));
        for(AudioDeviceInfo device:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))System.out.println("OUTPUT_ID="+device.getId()+" TYPE="+device.getType());
        if(args[0].equals("--pcm"))pcm();else if(args[0].equals("--wav"))wav();
        // ActivityThread may start Binder/handler threads; this probe is bounded.
        System.exit(0);
    }catch(Throwable error){error.printStackTrace();System.exit(1);}}
}
