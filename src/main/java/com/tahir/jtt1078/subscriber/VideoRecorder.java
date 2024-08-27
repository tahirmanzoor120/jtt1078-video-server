package com.tahir.jtt1078.subscriber;

import com.tahir.jtt1078.codec.MP3Encoder;
import com.tahir.jtt1078.flv.AudioTag;
import com.tahir.jtt1078.flv.FlvAudioTagEncoder;
import com.tahir.jtt1078.flv.FlvEncoder;
import com.tahir.jtt1078.util.ByteBufUtils;
import com.tahir.jtt1078.util.Configs;
import com.tahir.jtt1078.util.FLVUtils;
import com.tahir.jtt1078.util.HttpChunk;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class VideoRecorder extends Subscriber
{
    private long videoTimestamp = 0;
    private long audioTimestamp = 0;
    private long lastVideoFrameTimeOffset = 0;
    private long lastAudioFrameTimeOffset = 0;
    private boolean videoHeaderSent = false;
    private Logger LOGGER = LoggerFactory.getLogger(VideoRecorder.class);

    // ---- RECORDER ------ //

    private FileOutputStream outputStream;
    private String directory;
    private String path;
    private int recordingClipDuration; // seconds
    private boolean isRecordingStarted;

    public VideoRecorder(String tag, ChannelHandlerContext ctx)
    {
        super(tag, ctx);
        recordingClipDuration = Configs.getInt("recording.clip.duration", 60);
        String path = Configs.get("recording.path");
        prepareRecordingDir(path, tag); // creates subDirectory
    }

    public void prepareRecordingDir(String dir, String tag) {
        if (!StringUtils.isEmpty(dir)) {
            directory = (dir + "/" +  tag).replace("/", "\\");
            LOGGER.info("Recording Directory: {}", directory);
            File directory = new File(this.directory);
            if (!directory.exists()) {
                if (directory.mkdirs()) {
                    LOGGER.info("Directory created: {}", this.directory);
                } else {
                    LOGGER.info("Failed to create directory: {}", this.directory);
                }
            }
        } else {
            LOGGER.info("Incorrect directory path.");
        }
    }

    private void prepareRecording(Date timestamp, FlvEncoder flvEncoder) {
        LocalDateTime localDateTime = timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        String formattedDateTime = localDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // String formattedDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.path = (directory + "\\video_" + formattedDateTime + ".flv");

        try {
            this.outputStream = new FileOutputStream(this.path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveRecording() {
        LOGGER.info("Saving recording...");
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            LOGGER.error("Error closing file output streams", e);
        }
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            try {
                byte[] data = take();
                if (data != null) writeData(data);
            } catch (Exception ex) {
                // When destroying the thread, if there is a lock wait,
                // the thread will not be destroyed and InterruptedException will be thrown.
                if (ex instanceof InterruptedException) {
                    saveRecording();
                    break;
                }
                LOGGER.error("Recording failed", ex);
            }
        }
    }

    @Override
    public void onVideoData(long timeoffset, byte[] data, FlvEncoder flvEncoder)
    {
        if (lastVideoFrameTimeOffset == 0) lastVideoFrameTimeOffset = timeoffset;

        // Has it been sent before? If not, you need to reissue the FLV HEADER
        if (!videoHeaderSent && flvEncoder.videoReady())
        {
            prepareRecording(new Date(), flvEncoder);
            enqueue(flvEncoder.getHeader().getBytes());
            enqueue(flvEncoder.getVideoHeader().getBytes());

            // Directly deliver the first I frame
            byte[] iFrame = flvEncoder.getLastIFrame();
            if (iFrame != null)
            {
                FLVUtils.resetTimestamp(iFrame, (int) videoTimestamp);
                enqueue(iFrame);
            }

            videoHeaderSent = true;
        }

        if (data == null) return;

        FLVUtils.resetTimestamp(data, (int) videoTimestamp);
        videoTimestamp += (int)(timeoffset - lastVideoFrameTimeOffset);
        lastVideoFrameTimeOffset = timeoffset;

        enqueue(data);
    }

    private FlvAudioTagEncoder audioEncoder = new FlvAudioTagEncoder();
    MP3Encoder mp3Encoder = new MP3Encoder();

    @Override
    public void onAudioData(long timeoffset, byte[] data, FlvEncoder flvEncoder)
    {
        if (!videoHeaderSent) return;

        byte[] mp3Data = mp3Encoder.encode(data);

        if (mp3Data == null || mp3Data.length == 0) return;

        AudioTag audioTag = new AudioTag(0, mp3Data.length + 1, AudioTag.MP3, (byte) 0, (byte)1, (byte) 0, mp3Data);
        byte[] frameData = null;

        try {
            ByteBuf audioBuf = audioEncoder.encode(audioTag);
            frameData = ByteBufUtils.readReadableBytes(audioBuf);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (lastAudioFrameTimeOffset == 0) lastAudioFrameTimeOffset = timeoffset;

        if (data == null) return;

        FLVUtils.resetTimestamp(frameData, (int) audioTimestamp);
        audioTimestamp += (int)(timeoffset - lastAudioFrameTimeOffset);
        lastAudioFrameTimeOffset = timeoffset;

        enqueue(frameData);
    }

    @Override
    public void close()
    {
        saveRecording();
        super.close();
        mp3Encoder.close();
    }

    public void writeData(byte[] message)
    {
        try {
            outputStream.write(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
