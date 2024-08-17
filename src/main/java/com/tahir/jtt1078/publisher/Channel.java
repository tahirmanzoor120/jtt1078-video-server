package com.tahir.jtt1078.publisher;

import com.tahir.jtt1078.codec.AudioCodec;
import com.tahir.jtt1078.entity.Media;
import com.tahir.jtt1078.entity.MediaEncoding;
import com.tahir.jtt1078.flv.FlvEncoder;
import com.tahir.jtt1078.subscriber.RTMPPublisher;
import com.tahir.jtt1078.subscriber.Subscriber;
import com.tahir.jtt1078.subscriber.VideoSubscriber;
import com.tahir.jtt1078.util.ByteHolder;
import com.tahir.jtt1078.util.Configs;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private FileOutputStream videoOutputStream;
    private FileOutputStream audioOutputStream;

    private String videoPath;
    private String audioPath;

    private Date startTime;
    private long size;
    private String recordingDir;
    private String subDirectory;
    private int recordingClipDuration; // seconds

    public Channel(String tag)
    {
        this.tag = tag;
        this.subscribers = new ConcurrentLinkedQueue<Subscriber>();
        this.flvEncoder = new FlvEncoder(true, true);
        this.buffer = new ByteHolder(2048 * 100);

        if (!StringUtils.isEmpty(Configs.get("rtmp.url"))) {
            this.rtmpPublisher = new RTMPPublisher(tag);
            this.rtmpPublisher.start();
        }

        this.recordingDir = Configs.get("recording.path");
        this.recordingClipDuration = Configs.getInt("recording.clip.duration", 60);
        prepareRecordingDir();
        prepareRecording();
    }

    public void prepareRecordingDir() {
        if (!StringUtils.isEmpty(recordingDir)) {
            this.subDirectory = (recordingDir + "/" +  tag).replace("/", "\\");
            LOGGER.info("Recording Directory: {}", subDirectory );
            File directory = new File(subDirectory);
            if (!directory.exists()) {
                if (directory.mkdirs()) {
                    System.out.println("Directory created: " + subDirectory);
                } else {
                    System.out.println("Failed to create directory: " + subDirectory);
                }
            }
        }
    }

    private void prepareRecording() {
        this.startTime = new Date();
        String formattedDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.size = 0L;
        this.videoPath = (subDirectory + "\\video_" + formattedDateTime + ".h264");
        this.audioPath = (subDirectory + "\\audio_" + formattedDateTime + ".pcm");

        try {
            this.videoOutputStream = new FileOutputStream(this.videoPath);
            this.audioOutputStream = new FileOutputStream(this.audioPath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveRecording() {
        try {
            if (videoOutputStream != null) videoOutputStream.close();
            if (audioOutputStream != null) audioOutputStream.close();
        } catch (IOException e) {
            LOGGER.error("Error closing file output streams", e);
        }

        if (videoOutputStream != null && audioOutputStream != null) {
            String command = "ffmpeg -f s16le -ar 8000 -ac 1 -i " + this.audioPath + " -r 25 -i " + this.videoPath + " -c:v copy -c:a aac -strict experimental -vsync vfr " + this.videoPath.substring(0, this.videoPath.length() - 4) + "mp4";
            try {
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isPublishing()
    {
        return publishing;
    }

    public Subscriber subscribe(ChannelHandlerContext ctx) {
        LOGGER.info("Channel: {} -> {}, Subscriber: {}", Long.toHexString(hashCode() & 0xffffffffL), tag, ctx.channel().remoteAddress().toString());
        Subscriber subscriber = new VideoSubscriber(this.tag, ctx);
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

        if (audioOutputStream != null) {
            try {
                audioOutputStream.write(pcmData);
                audioOutputStream.flush();
            } catch (IOException e) {
                LOGGER.error("Error writing audio data to file", e);
            }
        }

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

            if (videoOutputStream != null) {
                try {
                    videoOutputStream.write(nalu);
                    videoOutputStream.flush();
                } catch (IOException e) {
                    LOGGER.error("Error writing video data to file", e);
                }
            }

            byte[] flvTag = this.flvEncoder.write(nalu, (int) (timeoffset - firstTimestamp));

            if (flvTag == null) continue;

            // Broadcast to all viewers
            broadcastVideo(timeoffset, flvTag);
        }

        long elapsedTime = getTimeDifferenceInSeconds();
        System.out.println("Elapsed Time: " + elapsedTime);
        if (elapsedTime  > recordingClipDuration) {
            saveRecording();
            prepareRecording();
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
        saveRecording();
    }

    public String getTimeDifference() {
        Date now = new Date();
        long diffInMillis = now.getTime() - this.startTime.getTime();

        long hours = TimeUnit.MILLISECONDS.toHours(diffInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) - TimeUnit.HOURS.toMinutes(hours);
        StringBuilder result = getStringBuilder(diffInMillis, hours, minutes);

        return result.toString().trim();
    }

    public long getTimeDifferenceInSeconds() {
        Date now = new Date();
        long diffInMillis = now.getTime() - this.startTime.getTime();
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
