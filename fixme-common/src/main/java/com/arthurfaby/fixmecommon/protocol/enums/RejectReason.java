package com.arthurfaby.fixmecommon.protocol.enums;

public enum RejectReason {
    UNKNOWN_INSTRUMENT("Unknown instrument");

    private final String message;

    RejectReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
