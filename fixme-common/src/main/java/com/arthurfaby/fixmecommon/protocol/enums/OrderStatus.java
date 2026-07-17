package com.arthurfaby.fixmecommon.protocol.enums;

public enum OrderStatus {
    EXECUTED(2),
    REJECTED(8);

    private final int value;

    OrderStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
