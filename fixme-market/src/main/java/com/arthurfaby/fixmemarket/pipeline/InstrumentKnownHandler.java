package com.arthurfaby.fixmemarket.pipeline;

import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.RejectReason;

/** Deuxieme maillon : rejette si l'instrument n'est pas tenu par ce Market. */
public final class InstrumentKnownHandler implements Handler<MarketCtx> {

    private final InstrumentBook book;

    public InstrumentKnownHandler(InstrumentBook book) {
        this.book = book;
    }

    @Override
    public HandlerResult execute(MarketCtx ctx) {
        String symbol = ctx.message().getString(FixTag.INSTRUMENT);
        if (book.isKnown(symbol)) {
            return HandlerResult.CONTINUE;
        }
        ctx.reject(RejectReason.UNKNOWN_INSTRUMENT.getMessage());
        return HandlerResult.STOP;
    }
}
