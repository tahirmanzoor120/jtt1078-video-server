package com.tahir.jtt1078.util;

import java.util.Arrays;

public class Packet {
    int size = 0;
    int offset = 0;
    int maxSize = 0;
    public byte[] data;

    protected Packet()
    {
        // do nothing here
    }

    public int size()
    {
        return size;
    }

    public void resizeTo(int size)
    {
        if (this.maxSize >= size) return;
        byte[] old = Arrays.copyOf(this.data, this.offset);
        this.data = new byte[size];
        System.arraycopy(old, 0, this.data, 0, old.length);
    }

    /**
     * Initialize a data packet according to the specified size.
     * Generally the size is 2 + 2 + 8 + 4 + DATA_LENGTH
     *
     * @param packetLength maximum number of bytes of data packet
     * @param bytes
     * @return
     */
    public static Packet create(int packetLength, byte[] bytes)
    {
        Packet p = new Packet();
        p.data = new byte[packetLength];
        p.maxSize = packetLength;
        return p;
    }

    public static Packet create(int length)
    {
        Packet p = new Packet();
        p.data = new byte[length];
        p.maxSize = length;
        p.size = 0;
        p.offset = 0;
        return p;
    }

    public static Packet create(byte[] data)
    {
        Packet p = new Packet();
        p.data = data;
        p.maxSize = data.length;
        p.size = data.length;
        p.offset = 0;
        return p;
    }

    public Packet addByte(byte b)
    {
        this.data[size++] = b;
        return this;
    }

    public Packet add3Bytes(int v)
    {
        this.data[size++] = (byte)((v >> 16) & 0xff);
        this.data[size++] = (byte)((v >>  8) & 0xff);
        this.data[size++] = (byte)((v >>  0) & 0xff);
        return this;
    }

    // If the value of b is 0x7e, it will be automatically escaped
    public Packet addByteAutoQuote(byte b)
    {
        if (b == 0x7e) {
            addByte((byte)0x7d);
            addByte((byte)0x02);
        }

        else if (b == 0x7d) {
            addByte((byte)0x7d);
            addByte((byte)0x01);
        }

        else {
            addByte(b);
        }

        return this;
    }

    public Packet putByte(byte b) {
        this.data[offset++] = b;
        return this;
    }

    public Packet addShort(short s) {
        this.data[size++] = (byte) ((s >> 8) & 0xff);
        this.data[size++] = (byte) (s & 0xff);
        return this;
    }

    public Packet putShort(short s) {
        this.data[offset++] = (byte) ((s >> 8) & 0xff);
        this.data[offset++] = (byte) (s & 0xff);
        return this;
    }

    public Packet addInt(int i) {
        this.data[size++] = (byte) ((i >> 24) & 0xff);
        this.data[size++] = (byte) ((i >> 16) & 0xff);
        this.data[size++] = (byte) ((i >> 8) & 0xff);
        this.data[size++] = (byte) (i & 0xff);
        return this;
    }

    public Packet putInt(int i) {
        this.data[offset++] = (byte) ((i >> 24) & 0xff);
        this.data[offset++] = (byte) ((i >> 16) & 0xff);
        this.data[offset++] = (byte) ((i >> 8) & 0xff);
        this.data[offset++] = (byte) (i & 0xff);
        return this;
    }

    public Packet addLong(long l) {
        this.data[size++] = (byte) ((l >> 56) & 0xff);
        this.data[size++] = (byte) ((l >> 48) & 0xff);
        this.data[size++] = (byte) ((l >> 40) & 0xff);
        this.data[size++] = (byte) ((l >> 32) & 0xff);
        this.data[size++] = (byte) ((l >> 24) & 0xff);
        this.data[size++] = (byte) ((l >> 16) & 0xff);
        this.data[size++] = (byte) ((l >> 8) & 0xff);
        this.data[size++] = (byte) (l & 0xff);
        return this;
    }

    public Packet putLong(long l) {
        this.data[offset++] = (byte) ((l >> 56) & 0xff);
        this.data[offset++] = (byte) ((l >> 48) & 0xff);
        this.data[offset++] = (byte) ((l >> 40) & 0xff);
        this.data[offset++] = (byte) ((l >> 32) & 0xff);
        this.data[offset++] = (byte) ((l >> 24) & 0xff);
        this.data[offset++] = (byte) ((l >> 16) & 0xff);
        this.data[offset++] = (byte) ((l >> 8) & 0xff);
        this.data[offset++] = (byte) (l & 0xff);
        return this;
    }

    public Packet addBytes(byte[] b)
    {
        return addBytes(b, b.length);
    }

    public Packet addBytes(byte[] b, int len) {
        System.arraycopy(b, 0, this.data, size, len);
        size += len;
        return this;
    }

    public Packet putBytes(byte[] b) {
        System.arraycopy(b, 0, this.data, offset, b.length);
        offset += b.length;
        return this;
    }

    public Packet rewind() {
        this.offset = 0;
        return this;
    }

    public byte nextByte()
    {
        return this.data[offset++];
    }

    public short nextShort()
    {
        return (short) (((this.data[offset++] & 0xff) << 8) | (this.data[offset++] & 0xff));
    }

    public short nextWord()
    {
        return nextShort();
    }

    public int nextDWord()
    {
        return nextInt();
    }

    public int nextInt()
    {
        return (this.data[offset++] & 0xff) << 24 | (this.data[offset++] & 0xff) << 16 | (this.data[offset++] & 0xff) << 8 | (this.data[offset++] & 0xff);
    }

    public String nextBCD() {
        byte val = this.data[offset++];
        int ch1 = (val >> 4) & 0x0f;
        int ch2 = (val & 0x0f);
        return ch1 + "" + ch2;
    }

    public Packet addBCD(String num) {
        for (int i = 0, l = num.length(); i < l; i+=2)
        {
            char a = (char)(num.charAt(i) - '0');
            char b = (char)(num.charAt(i + 1) - '0');
            addByte((byte)(a << 4 | b));
        }
        return this;
    }

    public long nextLong() {
        return ((long) this.data[offset++] & 0xff) << 56 | ((long) this.data[offset++] & 0xff) << 48 | ((long) this.data[offset++] & 0xff) << 40 | ((long) this.data[offset++] & 0xff) << 32 | ((long) this.data[offset++] & 0xff) << 24 | ((long) this.data[offset++] & 0xff) << 16 | ((long) this.data[offset++] & 0xff) << 8 | ((long) this.data[offset++] & 0xff);
    }

    public byte[] nextBytes(int length) {
        byte[] buf = new byte[length];
        System.arraycopy(this.data, offset, buf, 0, length);
        offset += length;
        return buf;
    }

    public byte[] nextBytes()
    {
        return nextBytes(size - offset);
    }

    public Packet skip(int offset) {
        this.offset += offset;
        return this;
    }

    public Packet seek(int index) {
        this.offset = index;
        return this;
    }

    public Packet reset() {
        this.offset = 0;
        this.size = 0;

        return this;
    }

    public byte[] getBytes() {
        if (size == maxSize) return this.data;
        else
        {
            byte[] buff = new byte[size];
            System.arraycopy(this.data, 0, buff, 0, size);
            return buff;
        }
    }

    public boolean hasMoreBytes()
    {
        return size - offset > 0;
    }

    public static void main(String[] args) throws Exception {
        ByteUtils.dump(Packet.create(32).addBCD("013800138000").getBytes());
    }
}
