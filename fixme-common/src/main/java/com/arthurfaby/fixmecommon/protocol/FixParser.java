package com.arthurfaby.fixmecommon.protocol;

import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.exception.FixParseException;

import java.nio.charset.StandardCharsets;

public final class FixParser {
    private FixParser() {}

    public static FixMessage parse(byte[] wire) {
        if (wire.length == 0) {
            throw new FixParseException("Empty message");
        }

        String raw = new String(wire, StandardCharsets.US_ASCII);
        String[] tokens = raw.split(String.valueOf((char) FixConstants.SOH), -1);

        if (!tokens[tokens.length - 1].isEmpty()) {
            throw new FixParseException("Message must end with SOH");
        }

        FixMessage.Builder builder = FixMessage.builder();
        int lastTagNumber = -1;

        for (int i = 0; i < tokens.length - 1; i++) {
            String token = tokens[i];
            String[] parts = token.split("=", 2);

            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new FixParseException("Malformed field: '" + token + "'");
            }

            int tagNumber;
            try {
                tagNumber = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                throw new FixParseException("Non-numeric tag in field: '" + token + "'");
            }

            FixTag tag = FixTag.of(tagNumber);
            if (tag == null) {
                throw new FixParseException("Unknown tag: " + tagNumber);
            }

            builder.set(tag, parts[1]);
            lastTagNumber = tagNumber;
        }

        if (lastTagNumber != FixTag.CHECKSUM.getKey()) {
            throw new FixParseException("Message must end with checksum field (10=)");
        }

        return builder.build();
    }
}
