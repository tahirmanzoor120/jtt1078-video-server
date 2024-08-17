package com.tahir.jtt1078.server;

import com.tahir.jtt1078.util.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

public class Jtt1078MessageDecoder extends ByteToMessageDecoder
{
    byte[] block = new byte[1024];
    Jtt1078Decoder decoder = new Jtt1078Decoder();

    private final Logger LOGGER = LoggerFactory.getLogger(Jtt1078MessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        int length = in.readableBytes();
        String hexDump = ByteBufUtil.hexDump(in, in.readerIndex(), Math.min(length, 10));
        InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        LOGGER.info(String.format("%s : %s%s (%d bytes)",
                inetSocketAddress.getAddress().getHostAddress(),
                hexDump,
                length > 10 ? "..." : "",
                length));

        // calculates how many 512-byte chunks are needed to process the entire input.
        int chunks = (int)Math.ceil(length / 512f);

        for (int i = 0; i < chunks; i++) {
            // calculate length of block data.
            // It's 512 bytes for all blocks except possibly the last one, which may be smaller.
            int blockLength = i < chunks - 1 ? 512 : length - (i * 512);
            in.readBytes(block, 0, blockLength);
            decoder.write(block, 0, blockLength);

            while (true)
            {
                Packet packet = decoder.decode();

                if (packet != null) {
                    out.add(packet);
                } else {
                    break;
                }
            }
        }
    }
}
