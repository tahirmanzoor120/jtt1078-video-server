package com.tahir.jtt1078.codec;

/**
 * 核心转换，PCM转G711
 * Created by onlygx
 */
public class G711Codec extends AudioCodec
{
    private final static int SIGN_BIT = 0x80;
    private final static int QUANT_MASK = 0xf;
    private final static int SEG_SHIFT = 4;
    private final static int SEG_MASK = 0x70;
    static short[] seg_end = {0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF};
    private final static int cClip = 32635;
    private static final byte[] aLawCompressTable = new byte[]{1, 1, 2, 2, 3, 3, 3,
            3, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};

    private static byte linearToALawSample(short sample) {
        int sign;
        int exponent;
        int mantissa;
        int s;

        sign = ((~sample) >> 8) & 0x80;
        if (!(sign == 0x80)) {
            sample = (short) -sample;
        }
        if (sample > cClip) {
            sample = cClip;
        }
        if (sample >= 256) {
            exponent = aLawCompressTable[(sample >> 8) & 0x7F];
            mantissa = (sample >> (exponent + 3)) & 0x0F;
            s = (exponent << 4) | mantissa;
        } else {
            s = sample >> 4;
        }
        s ^= (sign ^ 0x55);
        return (byte) s;
    }

    /**
     * PCM转G711
     *
     * @param src 编码前的数据
     * @return 编码后res数组长度应为编码前src数组长度的一半
     */
    public static byte[] encode(byte[] src) {
        int j = 0;
        int len = src.length;
        int count = len / 2;
        byte[] res = new byte[count];
        short sample = 0;
        for (int i = 0; i < count; i++) {
            sample = (short) (((src[j++] & 0xff) | (src[j++]) << 8));
            res[i] = linearToALawSample(sample);
        }
        return res;
    }

    static short search(short val, short[] table, short size) {

        for (short i = 0; i < size; i++) {
            if (val <= table[i]) {
                return i;
            }
        }
        return size;
    }

    public static byte linear2alaw(short pcm_val) {
        short mask;
        short seg;
        char aval;
        if (pcm_val >= 0) {
            mask = 0xD5;
        } else {
            mask = 0x55;
            pcm_val = (short) (-pcm_val - 1);
        }

        /* Convert the scaled magnitude to segment number. */
        seg = search(pcm_val, seg_end, (short) 8);

        /* Combine the sign, segment, and quantization bits. */

        if (seg >= 8)       /* out of range, return maximum value. */ return (byte) (0x7F ^ mask);
        else {
            aval = (char) (seg << SEG_SHIFT);
            if (seg < 2) aval |= (pcm_val >> 4) & QUANT_MASK;
            else aval |= (pcm_val >> (seg + 3)) & QUANT_MASK;
            return (byte) (aval ^ mask);
        }
    }


    public static short alaw2linear(byte a_val) {
        short t;
        short seg;

        a_val ^= 0x55;

        t = (short) ((a_val & QUANT_MASK) << 4);
        seg = (short) ((a_val & SEG_MASK) >> SEG_SHIFT);
        switch (seg) {
            case 0:
                t += 8;
                break;
            case 1:
                t += 0x108;
                break;
            default:
                t += 0x108;
                t <<= seg - 1;
        }
        return (a_val & SIGN_BIT) != 0 ? t : (short) -t;
    }

    // 由G.711转至PCM
    public static byte[] _toPCM(byte[] g711data) {
        byte[] pcmdata = new byte[g711data.length * 2];
        for (int i = 0, k = 0; i < g711data.length; i++) {
            short v = alaw2linear(g711data[i]);
            pcmdata[k++] = (byte) (v & 0xff);
            pcmdata[k++] = (byte) ((v >> 8) & 0xff);
        }
        return pcmdata;
    }

    // 由PCM转至G.711
    public static byte[] _fromPCM(byte[] pcmData) {
        return encode(pcmData);
    }

    @Override
    public byte[] toPCM(byte[] data) {
        byte[] temp;
        // 如果前四字节是00 01 52 00，则是海思头，需要去掉
        if (data[0] == 0x00 && data[1] == 0x01 && (data[2] & 0xff) == (data.length - 4) / 2 && data[3] == 0x00) {
            temp = new byte[data.length - 4];
            System.arraycopy(data, 4, temp, 0, temp.length);
        } else temp = data;

        return _toPCM(temp);
    }

    @Override
    public byte[] fromPCM(byte[] data) {
        return encode(data);
    }
}