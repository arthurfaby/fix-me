package com.arthurfaby.fixmecommon.protocol.enums;

public enum RejectReason {
    UNKNOWN_INSTRUMENT("Unknown instrument"),
    UNKNOWN_TARGET("Unknown target"),
    INVALID_CHECKSUM("Invalid checksum"),
    TARGET_UNREACHABLE("Target unreachable");

    private final String message;

    RejectReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
