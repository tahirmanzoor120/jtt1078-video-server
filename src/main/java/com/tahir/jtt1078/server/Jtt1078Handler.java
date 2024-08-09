package com.tahir.jtt1078.server;

import com.tahir.jtt1078.publisher.Channel;
import com.tahir.jtt1078.publisher.PublishManager;
import com.tahir.jtt1078.util.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class Jtt1078Handler extends SimpleChannelInboundHandler<Packet>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Jtt1078Handler.class);
    private final String VSEQ = "video-sequence";
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        io.netty.channel.Channel nettyChannel = ctx.channel();

        packet.seek(8);

        String deviceId = packet.nextBCD() + packet.nextBCD() + packet.nextBCD() + packet.nextBCD() + packet.nextBCD() + packet.nextBCD();
        int channel = packet.nextByte() & 0xff;
        String tag = deviceId + "-" + channel;

        if (SessionManager.contains(nettyChannel, "tag") == false) {
            Channel chl = PublishManager.getInstance().open(tag);
            SessionManager.set(nettyChannel, "tag", tag);
            LOGGER.info("Stream started: {} -> {}-{}", Long.toHexString(chl.hashCode() & 0xffffffffL), deviceId, channel);
        }

        Integer sequence = SessionManager.get(nettyChannel, VSEQ);
        if (sequence == null) sequence = 0;

        // 1. Make a serial number
        // 2. Audio needs to be transcoded before subscription is provided
        int lengthOffset = 28;
        int dataType = (packet.seek(15).nextByte() >> 4) & 0x0f;
        int pkType = packet.seek(15).nextByte() & 0x0f;

        // Transparent data transmission: 0100.
        // Without subsequent time, Last I Frame Interval and Last Frame Interval fields
        if (dataType == 0x04) lengthOffset = 28 - 8 - 2 - 2;
        // 0011：Audio Frame
        else if (dataType == 0x03) lengthOffset = 28 - 4;

        int payloadType = packet.seek(5).nextByte() & 0x7f;

        // 0000：Video I Frame, 0001：Video P Frame, 0010：Video B Frame
        if (dataType == 0x00 || dataType == 0x01 || dataType == 0x02) {
            // When the end mark is encountered, the sequence number + 1
            if (pkType == 0 || pkType == 2) {
                sequence += 1;
                SessionManager.set(nettyChannel, VSEQ, sequence);
            }
            byte[] data = packet.seek(lengthOffset + 2).nextBytes();
            long timestamp = packet.seek(16).nextLong();
            PublishManager.getInstance().publishVideo(tag, sequence, timestamp, payloadType, data);
        }
        // 0011：Audio Frame
        else if (dataType == 0x03) {
            long timestamp = packet.seek(16).nextLong();
            byte[] data = packet.seek(lengthOffset + 2).nextBytes();
            PublishManager.getInstance().publishAudio(tag, sequence, timestamp, payloadType, data);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        release(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        release(ctx.channel());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (IdleStateEvent.class.isAssignableFrom(evt.getClass())) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                String tag = SessionManager.get(ctx.channel(), "tag");
                LOGGER.info("Reading timeout: {}",tag);
                release(ctx.channel());
            }
        }
    }

    private void release(io.netty.channel.Channel channel) {
        String tag = SessionManager.get(channel, "tag");
        if (tag != null) {
            LOGGER.info("Stream stopped: {}", tag);
            PublishManager.getInstance().close(tag);
        }
    }
}
