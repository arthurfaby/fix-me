package com.arthurfaby.fixmerouter.pipeline;

import com.arthurfaby.fixmerouter.routing.RoutingTable;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.RejectReason;

public final class ForwardingHandler implements Handler<RouterCtx> {

    private static final int ROUTER_ID = 0;

    @Override
    public HandlerResult execute(RouterCtx ctx) {
        Connection target = ctx.target();

        if (!target.isOpen()) {
            // socket died between resolution and write: purge and reject
            RoutingTable.unregister(ctx.message().getInt(FixTag.TARGET_ID));
            reject(ctx);
            return HandlerResult.STOP;
        }

        target.send(ctx.rawFrame());
        return HandlerResult.CONTINUE;
    }

    private void reject(RouterCtx ctx) {
        int senderFixId = (Integer) ctx.sender().attachment();
        int clOrdId = ctx.message().getInt(FixTag.CLIENT_ORDER_ID);
        FixMessage reject = MessageFactory.reject(ROUTER_ID, senderFixId, clOrdId, RejectReason.TARGET_UNREACHABLE);
        ctx.sender().send(FixSerializer.serialize(reject));
    }
}
