package com.arthurfaby.fixmemarket.pipeline;

import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;

import java.math.BigDecimal;

/**
 * Dernier maillon : execute atomiquement sur le book puis renvoie
 * l'ExecutionReport au Router (qui la fera suivre au broker d'origine
 * via 56=).
 */
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
            // Defense en profondeur : les maillons precedents ont deja valide ce
            // cas (instrument connu, quantite suffisante) ; on ne devrait jamais
            // arriver ici tant qu'une seule connexion relie ce Market au Router.
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
