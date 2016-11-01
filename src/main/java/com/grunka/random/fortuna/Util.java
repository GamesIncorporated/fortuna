package com.grunka.random.fortuna;

public class Util {
    public static byte[] twoLeastSignificantBytes(long value) {
        byte[] result = new byte[2];
        result[0] = (byte) (value & 0xff);
        result[1] = (byte) ((value & 0xff00) >> 8);
        return result;
    }

    static int ceil(int value, int divisor) {
        return (value / divisor) + (value % divisor == 0 ? 0 : 1);
    }
}
