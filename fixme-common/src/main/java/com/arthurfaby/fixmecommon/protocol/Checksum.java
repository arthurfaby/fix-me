package com.arthurfaby.fixmecommon.protocol;

import java.nio.charset.StandardCharsets;

public final class Checksum {
    private Checksum() {}

    static int sum(byte[] data, int offset, int length) {
        int total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += (data[i] & 0xFF);
        }
        return total % 256;
    }

    public static String compute(byte[] data, int length) {
        return String.format("%03d", sum(data, 0, length));
    }

    public static boolean verify(byte[] message) {
        int trailerLen = 7; // "10=NNN" + SOH, excluded from the sum
        int coveredLength = message.length - trailerLen;
        if (coveredLength < 0) return false;
        if (message[coveredLength] != '1' || message[coveredLength+1] != '0'
                || message[coveredLength+2] != '=') {
            return false;
        }
        String expected = compute(message, coveredLength);
        String actual = new String(message, coveredLength + 3, 3, StandardCharsets.US_ASCII);
        return expected.equals(actual);
    }
}
