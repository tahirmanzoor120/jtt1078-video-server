package com.tahir.jtt1078.util;

public final class HttpChunk
{
    public static byte[] make(byte[] data)
    {
        Packet p = Packet.create(data.length + 64);
        p.addBytes(String.format("%x\r\n", data.length).getBytes());
        p.addBytes(data);
        p.addByte((byte)'\r');
        p.addByte((byte)'\n');
        return p.getBytes();
    }
}
