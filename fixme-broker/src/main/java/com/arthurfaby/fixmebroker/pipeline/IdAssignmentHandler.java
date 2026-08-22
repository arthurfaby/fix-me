package com.arthurfaby.fixmebroker.pipeline;

import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class IdAssignmentHandler implements Handler<BrokerCtx> {

    private static final Logger LOGGER = LogManager.getLogger(IdAssignmentHandler.class);

    @Override
    public HandlerResult execute(BrokerCtx ctx) {
        FixMessage message = ctx.message();
        if (!MessageType.LOGON.getValue().equals(message.getString(FixTag.MESSAGE_TYPE))) {
            return HandlerResult.CONTINUE;
        }

        int assignedId = message.getInt(FixTag.TARGET_ID);
        ctx.routerConnection().attach(assignedId);
        LOGGER.info("Connected, assigned id {}", assignedId);
        return HandlerResult.STOP;
    }
}
