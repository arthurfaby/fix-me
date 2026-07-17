package com.arthurfaby.fixmecommon.protocol;

import com.arthurfaby.fixmecommon.protocol.enums.FixTag;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class FixSerializer {

    public static byte[] serialize(FixMessage fixMessage) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<Integer, String> entry : fixMessage.entries()) {
            if (entry.getKey() == FixTag.CHECKSUM.getKey()) continue;
            body.writeBytes((entry.getKey() + "=" + entry.getValue()).getBytes(StandardCharsets.US_ASCII));
            body.write(FixConstants.SOH);
        }
        byte[] bodyBytes = body.toByteArray();
        String checksum = Checksum.compute(bodyBytes, bodyBytes.length);
        body.writeBytes(("10=" + checksum).getBytes(StandardCharsets.US_ASCII));
        body.write(FixConstants.SOH);
        return body.toByteArray();
    }
}
