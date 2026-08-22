package com.arthurfaby.fixmemarket.pipeline;

import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;

import java.math.BigDecimal;

public final class ExecutionHandler implements Handler<MarketCtx> {

    private final InstrumentBook book;

    public ExecutionHandler(InstrumentBook book) {
        this.book = book;
    }

    @Override
    public HandlerResult execute(MarketCtx ctx) {
        FixMessage order = ctx.message();
        String symbol = order.getString(FixTag.INSTRUMENT);
        int quantity = order.getInt(FixTag.QUANTITY);

        InstrumentBook.Result result = book.tryExecute(symbol, ctx.side(), quantity);
        if (result != InstrumentBook.Result.EXECUTED) {
            // safety net: earlier handlers already validated this case
            ctx.reject("Not enough quantity");
            return HandlerResult.STOP;
        }

        int marketId = ctx.routerConnection().attachment();
        int brokerId = order.getInt(FixTag.SENDER_ID);
        int clOrdId = order.getInt(FixTag.CLIENT_ORDER_ID);
        BigDecimal price = order.getPrice(FixTag.PRICE);

        FixMessage executed = MessageFactory.executed(marketId, brokerId, clOrdId, symbol, quantity, price);
        ctx.routerConnection().send(FixSerializer.serialize(executed));
        return HandlerResult.CONTINUE;
    }
}
