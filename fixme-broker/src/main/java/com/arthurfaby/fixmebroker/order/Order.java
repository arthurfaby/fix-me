package com.arthurfaby.fixmebroker.order;

import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.math.BigDecimal;

public record Order(int clOrdId, int marketId, String instrument, Side side, int quantity, BigDecimal price) {

    public String sideLabel() {
        return side == Side.BUY ? "Buy" : "Sell";
    }
}
