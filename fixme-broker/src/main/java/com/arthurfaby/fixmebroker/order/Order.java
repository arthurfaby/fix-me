package com.arthurfaby.fixmebroker.order;

import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.math.BigDecimal;

/**
 * Un ordre emis par ce Broker, garde en attente jusqu'a reception de son
 * rapport d'execution. Le {@code clOrdId} correle l'ordre a son rapport.
 */
public record Order(int clOrdId, int marketId, String instrument, Side side, int quantity, BigDecimal price) {

    public String sideLabel() {
        return side == Side.BUY ? "Buy" : "Sell";
    }
}
