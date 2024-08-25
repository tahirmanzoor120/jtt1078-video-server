package com.tahir.jtt1078.publisher;

import com.tahir.jtt1078.codec.AudioCodec;
import com.tahir.jtt1078.entity.Media;
import com.tahir.jtt1078.entity.MediaEncoding;
import com.tahir.jtt1078.flv.FlvEncoder;
import com.tahir.jtt1078.subscriber.RTMPPublisher;
import com.tahir.jtt1078.subscriber.Subscriber;
import com.tahir.jtt1078.subscriber.VideoRecorder;
import com.tahir.jtt1078.util.ByteHolder;
import com.tahir.jtt1078.util.Configs;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class Channel
{
    static final Logger LOGGER = LoggerFactory.getLogger(Channel.class);

    ConcurrentLinkedQueue<Subscriber> subscribers;
    RTMPPublisher rtmpPublisher;

    String tag;
    boolean publishing;
    ByteHolder buffer;
    AudioCodec audioCodec;
    FlvEncoder flvEncoder;
    private long firstTimestamp = -1;
    private long size;
    protected Date startTime;

    public Channel(String tag)
    {
        this.tag = tag;
        this.subscribers = new ConcurrentLinkedQueue<>();

        Subscriber recorder = new VideoRecorder(this.tag, null);
        recorder.setName("Recording: " + tag + "-" + recorder.getId());
        recorder.start();
        this.subscribers.add(recorder);

        this.flvEncoder = new FlvEncoder(true, true);
        this.buffer = new ByteHolder(2048 * 100);

        if (!StringUtils.isEmpty(Configs.get("rtmp.url"))) {
            this.rtmpPublisher = new RTMPPublisher(tag);
            this.rtmpPublisher.start();
        }

        this.startTime = new Date();
    }

    public boolean isPublishing()
    {
        return publishing;
    }

    public Subscriber subscribe(ChannelHandlerContext ctx) {
        LOGGER.info("Channel: {} -> {}, Subscriber: {}", Long.toHexString(hashCode() & 0xffffffffL), tag, ctx.channel().remoteAddress().toString());
        Subscriber subscriber = new VideoRecorder(this.tag, null);
        this.subscribers.add(subscriber);
        return subscriber;
    }

    public void writeAudio(long timestamp, int pt, byte[] data)     {
        size += data.length;

        if (audioCodec == null) {
            audioCodec = AudioCodec.getCodec(pt);
            LOGGER.info("Audio Codec: {}", MediaEncoding.getEncoding(Media.Type.Audio, pt));
        }

        byte[] pcmData = audioCodec.toPCM(data);
        broadcastAudio(timestamp, pcmData);
    }

    public void writeVideo(long sequence, long timeoffset, int payloadType, byte[] h264) {
        size += h264.length;

        if (firstTimestamp == -1) firstTimestamp = timeoffset;
        this.publishing = true;
        this.buffer.write(h264);

        while (true) {
            byte[] nalu = readNalu();
            if (nalu == null) break;
            if (nalu.length < 4) continue;

            byte[] flvTag = this.flvEncoder.write(nalu, (int) (timeoffset - firstTimestamp));

            if (flvTag == null) continue;

            broadcastVideo(timeoffset, flvTag);
        }
    }

    public void broadcastVideo(long timeoffset, byte[] flvTag)
    {
        for (Subscriber subscriber : subscribers)
        {
            subscriber.onVideoData(timeoffset, flvTag, flvEncoder);
        }
    }

    public void broadcastAudio(long timeoffset, byte[] flvTag)
    {
        for (Subscriber subscriber : subscribers)
        {
            subscriber.onAudioData(timeoffset, flvTag, flvEncoder);
        }
    }

    public void unsubscribe(long watcherId)
    {
        for (Iterator<Subscriber> itr = subscribers.iterator(); itr.hasNext(); )
        {
            Subscriber subscriber = itr.next();
            if (subscriber.getId() == watcherId)
            {
                itr.remove();
                subscriber.close();
                return;
            }
        }
    }

    public void close()
    {
        LOGGER.info("{} received in {}", formatBytes(this.size), getTimeDifference());
        for (Iterator<Subscriber> itr = subscribers.iterator(); itr.hasNext(); )
        {
            Subscriber subscriber = itr.next();
            subscriber.close();
            itr.remove();
        }
        if (rtmpPublisher != null) rtmpPublisher.close();
    }

    public String getTimeDifference() {
        Date now = new Date();
        long diffInMillis = now.getTime() - startTime.getTime();

        long hours = TimeUnit.MILLISECONDS.toHours(diffInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) - TimeUnit.HOURS.toMinutes(hours);
        StringBuilder result = getStringBuilder(diffInMillis, hours, minutes);

        return result.toString().trim();
    }

    public long getTimeDifferenceInSeconds() {
        Date now = new Date();
        long diffInMillis = now.getTime() - startTime.getTime();
        return diffInMillis / 1000;
    }

    private static StringBuilder getStringBuilder(long diffInMillis, long hours, long minutes) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diffInMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diffInMillis));

        StringBuilder result = new StringBuilder();
        if (hours > 0) {
            result.append(String.format("%02d hour ", hours));
        }
        if (minutes > 0) {
            result.append(String.format("%02d minutes ", minutes));
        }
        if (seconds > 0) {
            result.append(String.format("%02d seconds", seconds));
        }
        return result;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        double result = bytes / Math.pow(1024, exp);
        return String.format("%.2f %sB", result, unit);
    }

    private byte[] readNalu()
    {
        for (int i = 0; i < buffer.size() - 3; i++)
        {
            int a = buffer.get(i + 0) & 0xff;
            int b = buffer.get(i + 1) & 0xff;
            int c = buffer.get(i + 2) & 0xff;
            int d = buffer.get(i + 3) & 0xff;
            if (a == 0x00 && b == 0x00 && c == 0x00 && d == 0x01)
            {
                if (i == 0) continue;
                byte[] nalu = new byte[i];
                buffer.sliceInto(nalu, i);
                return nalu;
            }
        }
        return null;
    }
}
