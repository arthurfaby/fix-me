package com.arthurfaby.fixmecommon.pipeline;

import com.arthurfaby.fixmecommon.protocol.Checksum;

public class ChecksumValidationHandler<T extends HasRawFrame> implements Handler<T> {
    @Override
    public HandlerResult execute(T ctx) {
        if (!Checksum.verify(ctx.rawFrame())) {
            return HandlerResult.STOP;
        }
        return HandlerResult.CONTINUE;
    }
}
