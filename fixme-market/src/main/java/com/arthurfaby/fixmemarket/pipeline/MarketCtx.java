package com.arthurfaby.fixmemarket.pipeline;

import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.pipeline.HasRawFrame;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.Side;

public final class MarketCtx implements HasRawFrame {

    private final Connection routerConnection;
    private final byte[] rawFrame;

    private FixMessage message;
    private Side side;

    public MarketCtx(Connection routerConnection, byte[] rawFrame) {
        this.routerConnection = routerConnection;
        this.rawFrame = rawFrame;
    }

    @Override
    public byte[] rawFrame() {
        return rawFrame;
    }

    public Connection routerConnection() {
        return routerConnection;
    }

    public FixMessage message() {
        return message;
    }

    public void message(FixMessage message) {
        this.message = message;
    }

    public Side side() {
        return side;
    }

    public void side(Side side) {
        this.side = side;
    }

    public void reject(String reasonText) {
        int marketId = routerConnection.attachment();
        int brokerId = message.getInt(FixTag.SENDER_ID);
        int clOrdId = message.getInt(FixTag.CLIENT_ORDER_ID);
        String instrument = message.getString(FixTag.INSTRUMENT);
        int quantity = message.getInt(FixTag.QUANTITY);

        FixMessage rejected = MessageFactory.rejected(marketId, brokerId, clOrdId, instrument, quantity, reasonText);
        routerConnection.send(FixSerializer.serialize(rejected));
    }
}
