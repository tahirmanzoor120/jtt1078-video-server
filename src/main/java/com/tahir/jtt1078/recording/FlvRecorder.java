package com.tahir.jtt1078.recording;

import com.tahir.jtt1078.codec.MP3Encoder;
import com.tahir.jtt1078.flv.AudioTag;
import com.tahir.jtt1078.flv.FlvAudioTagEncoder;
import com.tahir.jtt1078.flv.FlvEncoder;
import com.tahir.jtt1078.util.ByteBufUtils;
import com.tahir.jtt1078.util.FLVUtils;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public class FlvRecorder extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlvRecorder.class);
    private static final AtomicLong SEQUENCE = new AtomicLong(0L);

    private final String outputPath;
    private final Object lock = new Object();
    private final LinkedList<byte[]> dataQueue = new LinkedList<>();

    private long maxRecordingTimeMillis;
    private Instant startTime;
    private volatile boolean running = true;
    private FileOutputStream fileOutputStream;
    private long id;
    private FlvEncoder flvEncoder;

    private long videoTimestamp = 0;
    private long audioTimestamp = 0;
    private long lastVideoFrameTimeOffset = 0;
    private long lastAudioFrameTimeOffset = 0;

    private FlvAudioTagEncoder audioEncoder = new FlvAudioTagEncoder();
    MP3Encoder mp3Encoder = new MP3Encoder();

    public FlvRecorder(String initialOutputPath, FlvEncoder flvEncoder, int clipDuration) throws IOException {
        this.flvEncoder = flvEncoder;
        this.outputPath = initialOutputPath;
        this.fileOutputStream = createNewOutputStream();
        this.startTime = Instant.now();
        this.id = SEQUENCE.getAndAdd(1L);
        this.maxRecordingTimeMillis = clipDuration * 1000;
    }

    private FileOutputStream createNewOutputStream() throws IOException {
        String filePath = generateFileName();
        FileOutputStream out =  new FileOutputStream(filePath);
        out.write(flvEncoder.getHeader().getBytes());
        return  out;
    }

    private String generateFileName() {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);

        return outputPath.replace(".flv", "") + "_" + timestamp + ".flv";
    }

    public void enqueue(byte[] data) {
        if (data == null) return;
        synchronized (lock) {
            dataQueue.addLast(data);
            lock.notify();
        }
    }

    public void recordVideoData(long timeoffset, byte[] data, FlvEncoder flvEncoder)
    {
        if (lastVideoFrameTimeOffset == 0) lastVideoFrameTimeOffset = timeoffset;

        if (data == null) return;

        FLVUtils.resetTimestamp(data, (int) videoTimestamp);
        videoTimestamp += (int)(timeoffset - lastVideoFrameTimeOffset);
        lastVideoFrameTimeOffset = timeoffset;

        enqueue(data);
    }

    public void recordAudioData(long timeoffset, byte[] data, FlvEncoder flvEncoder)
    {

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
    public void run() {
        while (running || !dataQueue.isEmpty()) {
            try {
                byte[] data = take();
                if (data != null) {
                    writeToFile(data);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    break;
                }
                LOGGER.error("Recording failed", e);
            }
        }
        closeOutputStream();
    }


    private void writeToFile(byte[] data) throws IOException {
        synchronized (lock) {
//            if (Instant.now().isAfter(startTime.plusMillis(maxRecordingTimeMillis))) {
//                fileOutputStream.close();
//                fileOutputStream = createNewOutputStream();
//                startTime = Instant.now();
//            }
            fileOutputStream.write(data);
        }
    }

    private void closeOutputStream() {
        synchronized (lock) {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to close output stream", e);
            }
        }
    }

    public void stopRecording() {
        running = false;
        this.interrupt(); // Interrupt the thread if it's waiting
    }
}
