package com.arthurfaby.fixmecommon.net;

import com.arthurfaby.fixmecommon.protocol.FixConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// The message always ends with the checksum field, so we split frames by scanning
// the terminal pattern \x0110=NNN\x01. Handles TCP fragmentation.
public final class FixFrameDecoder implements FrameDecoder {

    private static final int MAX_FRAME_SIZE = 4096;
    private static final byte[] CHECKSUM_TAG_PREFIX = {FixConstants.SOH, '1', '0', '='};
    private static final int TERMINATOR_LENGTH = CHECKSUM_TAG_PREFIX.length + 3 + 1;

    private byte[] pending = new byte[0];

    @Override
    public List<byte[]> decode(byte[] chunk) {
        byte[] buffer = concat(pending, chunk);

        List<byte[]> frames = new ArrayList<>();
        int frameStart = 0;
        int searchFrom = 0;

        while (true) {
            int terminatorStart = indexOfChecksumTerminator(buffer, searchFrom);
            if (terminatorStart == -1) {
                break;
            }
            int frameEnd = terminatorStart + TERMINATOR_LENGTH;
            if (frameEnd > buffer.length) {
                break;
            }
            frames.add(Arrays.copyOfRange(buffer, frameStart, frameEnd));
            frameStart = frameEnd;
            searchFrom = frameEnd;
        }

        pending = Arrays.copyOfRange(buffer, frameStart, buffer.length);
        // OOM guard: a client that never sends a 10= must not grow the buffer forever
        if (pending.length > MAX_FRAME_SIZE) {
            throw new IllegalStateException(
                    "Frame exceeds " + MAX_FRAME_SIZE + " bytes without a checksum terminator");
        }

        return frames;
    }

    private static int indexOfChecksumTerminator(byte[] buffer, int from) {
        startOfLoop:
        for (int i = from; i <= buffer.length - CHECKSUM_TAG_PREFIX.length; i++) {
            for (int j = 0; j < CHECKSUM_TAG_PREFIX.length; j++) {
                if (buffer[i + j] != CHECKSUM_TAG_PREFIX[j]) {
                    continue startOfLoop;
                }
            }

            int digitsStart = i + CHECKSUM_TAG_PREFIX.length;
            if (digitsStart + 4 > buffer.length) {
                return -1;
            }
            if (isThreeDigits(buffer, digitsStart) && buffer[digitsStart + 3] == FixConstants.SOH) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isThreeDigits(byte[] buffer, int offset) {
        for (int k = 0; k < 3; k++) {
            byte b = buffer[offset + k];
            if (b < '0' || b > '9') {
                return false;
            }
        }
        return true;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
