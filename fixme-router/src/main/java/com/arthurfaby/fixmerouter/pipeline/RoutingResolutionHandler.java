package com.arthurfaby.fixmerouter.pipeline;

import com.arthurfaby.fixmerouter.routing.RoutingTable;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.RejectReason;
import com.arthurfaby.fixmecommon.protocol.exception.FixParseException;

import java.util.Optional;

public final class RoutingResolutionHandler implements Handler<RouterCtx> {

    private static final int ROUTER_ID = 0;

    @Override
    public HandlerResult execute(RouterCtx ctx) {
        FixMessage message;
        try {
            message = FixParser.parse(ctx.rawFrame());
        } catch (FixParseException e) {
            return HandlerResult.STOP;
        }
        ctx.message(message);

        Integer targetId = message.getInt(FixTag.TARGET_ID);
        Optional<Connection> target = RoutingTable.find(targetId);

        if (target.isEmpty()) {
            reject(ctx, RejectReason.UNKNOWN_TARGET);
            return HandlerResult.STOP;
        }

        ctx.target(target.get());
        return HandlerResult.CONTINUE;
    }

    private void reject(RouterCtx ctx, RejectReason reason) {
        int senderFixId = (Integer) ctx.sender().attachment();
        int clOrdId = ctx.message().getInt(FixTag.CLIENT_ORDER_ID);
        FixMessage reject = MessageFactory.reject(ROUTER_ID, senderFixId, clOrdId, reason);
        ctx.sender().send(FixSerializer.serialize(reject));
    }
}
