package com.arthurfaby.fixmecommon.protocol.enums;

public enum Side {
    BUY(1),
    SELL(2);

    private final int value;

    Side(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
