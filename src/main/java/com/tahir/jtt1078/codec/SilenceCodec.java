package com.tahir.jtt1078.codec;

/**
 * Created by houcheng on 2019-12-11.
 */
public class SilenceCodec extends AudioCodec
{
    static final byte[] BLANK = new byte[0];

    @Override
    public byte[] toPCM(byte[] data)
    {
        return BLANK;
    }

    @Override
    public byte[] fromPCM(byte[] data)
    {
        return BLANK;
    }
}
