package com.tahir.jtt1078.util;

public final class FLVUtils {
    public static void resetTimestamp(byte[] packet, int timestamp)     {
        // 0 1 2 3
        // 4 5 6 7
        // Only modify the video tags
        if (packet[0] != 9 && packet[0] != 8) return;

        packet[4] = (byte)((timestamp >> 16) & 0xff);
        packet[5] = (byte)((timestamp >>  8) & 0xff);
        packet[6] = (byte)((timestamp >>  0) & 0xff);
        packet[7] = (byte)((timestamp >> 24) & 0xff);
    }
}

