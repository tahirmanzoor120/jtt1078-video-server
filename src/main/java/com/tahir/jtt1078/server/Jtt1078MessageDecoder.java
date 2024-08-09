package com.tahir.jtt1078.server;

import com.tahir.jtt1078.util.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Jtt1078MessageDecoder extends ByteToMessageDecoder
{
    byte[] block = new byte[4096];
    Jtt1078Decoder decoder = new Jtt1078Decoder();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        // find the number of readable bytes
        int length = in.readableBytes();

        // calculates how many 512-byte chunks are needed to process the entire input.
        int k = (int)Math.ceil(length / 512f);

        for (int i = 0; i < k; i++) {
            // calculate length of block data.
            // It's 512 bytes for all blocks except possibly the last one, which may be smaller.
            int l = i < k - 1 ? 512 : length - (i * 512);
            // read block
            in.readBytes(block, 0, l);
            // add block to the decoder's buffer
            decoder.write(block, 0, l);

            // continuously attempts to decode packets from the decoder until no more packets are available.
            while (true)
            {
                // try to decode a block
                Packet p = decoder.decode();
                // no packet, exit
                if (p == null) break;
                // add packet to output
                out.add(p);
            }
        }
    }
}
