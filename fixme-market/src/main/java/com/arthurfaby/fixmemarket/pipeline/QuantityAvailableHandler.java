package com.arthurfaby.fixmemarket.pipeline;

import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.Side;

/**
 * Troisieme maillon : pour un Buy, rejette si le stock est insuffisant.
 * Un Sell est toujours accepte a ce stade (le market rachete).
 */
public final class QuantityAvailableHandler implements Handler<MarketCtx> {

    private final InstrumentBook book;

    public QuantityAvailableHandler(InstrumentBook book) {
        this.book = book;
    }

    @Override
    public HandlerResult execute(MarketCtx ctx) {
        if (ctx.side() != Side.BUY) {
            return HandlerResult.CONTINUE;
        }

        String symbol = ctx.message().getString(FixTag.INSTRUMENT);
        int quantity = ctx.message().getInt(FixTag.QUANTITY);

        if (book.quantityOf(symbol) < quantity) {
            ctx.reject("Not enough quantity");
            return HandlerResult.STOP;
        }
        return HandlerResult.CONTINUE;
    }
}
