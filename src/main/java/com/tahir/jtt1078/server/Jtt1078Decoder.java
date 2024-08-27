package com.tahir.jtt1078.server;

import com.tahir.jtt1078.util.ByteHolder;
import com.tahir.jtt1078.util.ByteUtils;
import com.tahir.jtt1078.util.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jtt1078Decoder
{
    ByteHolder buffer = new ByteHolder(4096);

    final Logger LOGGER = LoggerFactory.getLogger(Jtt1078Decoder.class);

    public void write(byte[] block, int startIndex, int length)
    {
        byte[] copy = new byte[length];
        System.arraycopy(block, startIndex, copy, 0, length);
        buffer.write(copy);
    }

    public Packet decode()
    {
        if (this.buffer.size() < 30) {
            return null;
        }

        if ((buffer.getInt(0) & 0x7fffffff) != 0x30316364) {
            // String dump = ByteUtils.toString(buffer.array(30));
            // LOGGER.info("Unknown Data: " + dump);
            return null;
        }

        int lengthOffset = 28;
        int dataType = (this.buffer.get(15) >> 4) & 0x0f;

        if (dataType == 0x04) {
            lengthOffset = 28 - 8 - 2 - 2;
        } else if (dataType == 0x03) {
            lengthOffset = 28 - 4;
        }

        int bodyLength = this.buffer.getShort(lengthOffset);
        int packetLength = bodyLength + lengthOffset + 2;

        if (this.buffer.size() < packetLength) {
            return null;
        }

        byte[] block = new byte[packetLength];
        this.buffer.sliceInto(block, packetLength);
        return Packet.create(block);
    }
}
