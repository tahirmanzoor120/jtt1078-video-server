package com.tahir.jtt1078.entity;

/**
 * Created by houcheng on 2019-12-11.
 * Data stream, which may be video or audio. Video is encapsulated in FLV format and audio is a PCM-encoded segment.
 */
public class Media
{
    public enum Type { Video, Audio };
    public Type type;
    public MediaEncoding.Encoding encoding;
    public long sequence;
    public byte[] data;

    public Media(long seq, MediaEncoding.Encoding encoding, byte[] data)
    {
        this.data = data;
        this.encoding = encoding;
        this.sequence = seq;
    }

    public Media(long seq, MediaEncoding.Encoding encoding, byte[] data, Type type)
    {
        this.type = type;
        this.data = data;
        this.encoding = encoding;
        this.sequence = seq;
    }
}
