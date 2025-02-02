package com.tahir.jtt1078.subscriber;

import com.tahir.jtt1078.codec.MP3Encoder;
import com.tahir.jtt1078.flv.AudioTag;
import com.tahir.jtt1078.flv.FlvAudioTagEncoder;
import com.tahir.jtt1078.flv.FlvEncoder;
import com.tahir.jtt1078.util.ByteBufUtils;
import com.tahir.jtt1078.util.FLVUtils;
import com.tahir.jtt1078.util.HttpChunk;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class VideoSubscriber extends Subscriber
{
    private long videoTimestamp = 0;
    private long audioTimestamp = 0;
    private long lastVideoFrameTimeOffset = 0;
    private long lastAudioFrameTimeOffset = 0;
    private boolean videoHeaderSent = false;

    public VideoSubscriber(String tag, ChannelHandlerContext ctx)
    {
        super(tag, ctx);
    }

    @Override
    public void onVideoData(long timeoffset, byte[] data, FlvEncoder flvEncoder)
    {
        if (lastVideoFrameTimeOffset == 0) lastVideoFrameTimeOffset = timeoffset;

        // Has it been sent before? If not, you need to reissue the FLV HEADER
        if (videoHeaderSent == false && flvEncoder.videoReady())
        {
            enqueue(HttpChunk.make(flvEncoder.getHeader().getBytes()));
            enqueue(HttpChunk.make(flvEncoder.getVideoHeader().getBytes()));

            // Directly deliver the first I frame
            byte[] iFrame = flvEncoder.getLastIFrame();
            if (iFrame != null)
            {
                FLVUtils.resetTimestamp(iFrame, (int) videoTimestamp);
                enqueue(HttpChunk.make(iFrame));
            }

            videoHeaderSent = true;
        }

        if (data == null) return;

        FLVUtils.resetTimestamp(data, (int) videoTimestamp);
        videoTimestamp += (int)(timeoffset - lastVideoFrameTimeOffset);
        lastVideoFrameTimeOffset = timeoffset;

        enqueue(HttpChunk.make(data));
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

        enqueue(HttpChunk.make(frameData));
    }

    @Override
    public void close()
    {
        super.close();
        mp3Encoder.close();
    }
}
