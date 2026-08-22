package com.arthurfaby.fixmecommon.pipeline;

import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumValidationHandlerTest {

    private record FrameContext(byte[] rawFrame) implements HasRawFrame {}

    private final ChecksumValidationHandler<FrameContext> handler = new ChecksumValidationHandler<>();

    @Test
    void continuesOnAValidChecksum() {
        byte[] wire = wireWithValidChecksum("49=100001");

        assertThat(handler.execute(new FrameContext(wire))).isEqualTo(HandlerResult.CONTINUE);
    }

    @Test
    void stopsOnATamperedChecksum() {
        byte[] wire = wireWithValidChecksum("49=100001");
        wire[wire.length - 2] = (byte) (wire[wire.length - 2] == '9' ? '8' : '9');

        assertThat(handler.execute(new FrameContext(wire))).isEqualTo(HandlerResult.STOP);
    }

    private static byte[] wireWithValidChecksum(String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.US_ASCII);
        String checksum = Checksum.compute(bodyBytes, bodyBytes.length);
        byte[] trailer = ("10=" + checksum).getBytes(StandardCharsets.US_ASCII);

        byte[] wire = new byte[bodyBytes.length + trailer.length + 1];
        System.arraycopy(bodyBytes, 0, wire, 0, bodyBytes.length);
        System.arraycopy(trailer, 0, wire, bodyBytes.length, trailer.length);
        wire[wire.length - 1] = FixConstants.SOH;
        return wire;
    }
}
