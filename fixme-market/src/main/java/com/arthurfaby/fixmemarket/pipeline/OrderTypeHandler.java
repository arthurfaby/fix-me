package com.arthurfaby.fixmemarket.pipeline;

import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import com.arthurfaby.fixmecommon.protocol.exception.FixParseException;

public final class OrderTypeHandler implements Handler<MarketCtx> {

    @Override
    public HandlerResult execute(MarketCtx ctx) {
        FixMessage message;
        try {
            message = FixParser.parse(ctx.rawFrame());
        } catch (FixParseException e) {
            return HandlerResult.STOP;
        }
        ctx.message(message);

        // not an order: silently ignore, it's not the Market's job to answer
        if (!MessageType.ORDER.getValue().equals(message.getString(FixTag.MESSAGE_TYPE))) {
            return HandlerResult.STOP;
        }

        int sideValue = message.getInt(FixTag.SIDE);
        if (sideValue == Side.BUY.getValue()) {
            ctx.side(Side.BUY);
        } else if (sideValue == Side.SELL.getValue()) {
            ctx.side(Side.SELL);
        } else {
            return HandlerResult.STOP;
        }

        return HandlerResult.CONTINUE;
    }
}
