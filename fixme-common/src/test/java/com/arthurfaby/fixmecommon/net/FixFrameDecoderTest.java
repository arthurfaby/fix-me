package com.arthurfaby.fixmecommon.net;

import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixFrameDecoderTest {

    private final FixFrameDecoder decoder = new FixFrameDecoder();

    @Test
    void oneFrameInOneChunkYieldsOneFrame() {
        byte[] frame = frame("49=100001", "56=100002", "35=D");

        List<byte[]> result = decoder.decode(frame);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(frame);
    }

    @Test
    void oneFrameDeliveredByteByByteYieldsFrameOnlyOnLastByte() {
        byte[] frame = frame("49=100001", "56=100002", "35=D");

        for (int i = 0; i < frame.length - 1; i++) {
            List<byte[]> result = decoder.decode(new byte[] {frame[i]});
            assertThat(result).isEmpty();
        }

        List<byte[]> last = decoder.decode(new byte[] {frame[frame.length - 1]});
        assertThat(last).hasSize(1);
        assertThat(last.get(0)).isEqualTo(frame);
    }

    @Test
    void twoFramesInOneChunkYieldsTwoFramesInOrder() {
        byte[] first = frame("49=100001", "56=100002", "35=D");
        byte[] second = frame("49=100002", "56=100001", "35=8");

        List<byte[]> result = decoder.decode(concat(first, second));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(first);
        assertThat(result.get(1)).isEqualTo(second);
    }

    @Test
    void partialSecondFrameIsKeptForTheNextDecodeCall() {
        byte[] first = frame("49=100001", "56=100002", "35=D");
        byte[] second = frame("49=100002", "56=100001", "35=8");
        int half = second.length / 2;

        List<byte[]> firstBatch = decoder.decode(
                concat(first, Arrays.copyOfRange(second, 0, half)));

        assertThat(firstBatch).hasSize(1);
        assertThat(firstBatch.get(0)).isEqualTo(first);

        List<byte[]> secondBatch = decoder.decode(
                Arrays.copyOfRange(second, half, second.length));

        assertThat(secondBatch).hasSize(1);
        assertThat(secondBatch.get(0)).isEqualTo(second);
    }

    @Test
    void garbageExceedingMaxSizeWithoutTerminatorThrows() {
        byte[] garbage = new byte[5000];
        Arrays.fill(garbage, (byte) 'A');

        assertThatThrownBy(() -> decoder.decode(garbage))
                .isInstanceOf(IllegalStateException.class);
    }

    private static byte[] frame(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            sb.append(field).append((char) FixConstants.SOH);
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.US_ASCII);
        String checksum = Checksum.compute(body, body.length);
        byte[] trailer = ("10=" + checksum).getBytes(StandardCharsets.US_ASCII);

        byte[] wire = new byte[body.length + trailer.length + 1];
        System.arraycopy(body, 0, wire, 0, body.length);
        System.arraycopy(trailer, 0, wire, body.length, trailer.length);
        wire[wire.length - 1] = FixConstants.SOH;
        return wire;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
