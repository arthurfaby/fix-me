package com.arthurfaby.fixmecommon.protocol.enums;

import com.arthurfaby.fixmecommon.protocol.FixConstants;

import java.nio.charset.StandardCharsets;

public class FixDebug {
    public static String getReadableWire(byte[] wire) {
        return new String(wire, StandardCharsets.US_ASCII)
                .replace((char) FixConstants.SOH, '|');
    }
}
