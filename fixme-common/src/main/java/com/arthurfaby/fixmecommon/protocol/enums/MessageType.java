package com.arthurfaby.fixmecommon.protocol.enums;

public enum MessageType {
    ORDER("D"),
    EXECUTION_REPORT("8"),
    REJECT("3"),
    LOGON("A");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
