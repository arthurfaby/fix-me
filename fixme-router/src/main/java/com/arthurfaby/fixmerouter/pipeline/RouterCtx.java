package com.arthurfaby.fixmerouter.pipeline;

import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.pipeline.HasRawFrame;
import com.arthurfaby.fixmecommon.protocol.FixMessage;

public final class RouterCtx implements HasRawFrame {

    private final Connection sender;
    private final byte[] rawFrame;

    private FixMessage message;
    private Connection target;

    public RouterCtx(Connection sender, byte[] rawFrame) {
        this.sender = sender;
        this.rawFrame = rawFrame;
    }

    @Override
    public byte[] rawFrame() {
        return rawFrame;
    }

    public Connection sender() {
        return sender;
    }

    public FixMessage message() {
        return message;
    }

    public void message(FixMessage message) {
        this.message = message;
    }

    public Connection target() {
        return target;
    }

    public void target(Connection target) {
        this.target = target;
    }
}
