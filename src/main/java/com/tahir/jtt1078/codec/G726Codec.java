package com.tahir.jtt1078.codec;

import com.tahir.jtt1078.codec.g726.*;
import com.tahir.jtt1078.codec.g726.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class G726Codec extends AudioCodec {

    // pcm sampling rate
    private static final int PCM_SAMPLE = 8000;

    // pcm sampling point
    private static final int PCM_POINT = 320;

    //Number of audio channels
    private static final int CHANNEL = 1;

    // code rate
    private static final int G726_BIT_RATE_16000 = 16000;
    private static final int G726_BIT_RATE_24000 = 24000;
    private static final int G726_BIT_RATE_32000 = 32000;
    private static final int G726_BIT_RATE_40000 = 40000;


    @Override
    public byte[] toPCM(byte[] data) {
        int pos = 0;

        // If the first four bytes are 00 01 52 00, it is a HiSilicon header and needs to be removed.
        if (data[0] == 0x00 && data[1] == 0x01 && (data[2] & 0xff) == (data.length - 4) / 2 && data[3] == 0x00) {
            pos = 4;
        }

        int length = data.length - pos;

        int point = PCM_POINT;

        // Calculate the code rate of G726
        int rateBit = length * 8 * PCM_SAMPLE/point;

        G726 g726 = null;

        // code rate
        if (rateBit == G726_BIT_RATE_40000) {
            g726 = new G726_40();
        }
        else if (rateBit == G726_BIT_RATE_32000) {
            g726 = new G726_32();
        }
        else if (rateBit == G726_BIT_RATE_24000) {
            g726 = new G726_24();
        }
        else if (rateBit == G726_BIT_RATE_16000) {
            g726 = new G726_16();
        }
        else {
            return null;
        }

        int pcmSize = point * CHANNEL * 2;
        byte[] pcm = new byte[pcmSize];

        int ret = g726.decode(data,pos,length,G726.AUDIO_ENCODING_LINEAR,pcm,0);
        if (ret < 0) {
            return null;
        }
        return pcm;
    }

    @Override
    public byte[] fromPCM(byte[] data) {
        // TODO:
        return new byte[0];
    }

    private static void readWrite(String in,String out,int size) throws Exception {
        FileInputStream f =  new FileInputStream(in);
        FileOutputStream o = new FileOutputStream(out);
        int len = -1;
        byte[] buff = new byte[size];
        G726Codec g726Codec = new G726Codec();
        int index = 0;
        while ((len = f.read(buff,index,buff.length)) > -1) {
            o.write(g726Codec.toPCM(buff));
        }
    }

    // Enter /Applications/VLC.app/Contents/MacOS/VLC in the terminal under mac --demux=rawaud --rawaud-channels 1 --rawaud-samplerate 8000 ${path}
    // Modify the value of ${path} to the pcm path to play the transcoded pcm file.
    public static void main(String[] args) throws Exception {

        readWrite(Thread.currentThread().getContextClassLoader().getResource("g726/in_40.g726").getPath(),
                "/Users/tmyam/Downloads/out_40.pcm",200);


        readWrite(Thread.currentThread().getContextClassLoader().getResource("g726/in_32.g726").getPath(),
                "/Users/tmyam/Downloads/out_32.pcm",160);


        readWrite(Thread.currentThread().getContextClassLoader().getResource("g726/in_24.g726").getPath(),
                "/Users/tmyam/Downloads/out_24.pcm",120);


        readWrite(Thread.currentThread().getContextClassLoader().getResource("g726/in_16.g726").getPath(),
                "/Users/tmyam/Downloads/out_16.pcm",80);
    }
}
