package com.arthurfaby.fixmebroker.pipeline;

import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.report.OrderReporter;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.pipeline.HasRawFrame;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;

public final class BrokerCtx implements HasRawFrame {

    private final Connection routerConnection;
    private final byte[] rawFrame;
    private final PendingOrders pending;
    private final OrderReporter reporter;

    private FixMessage message;

    public BrokerCtx(Connection routerConnection, byte[] rawFrame,
                     PendingOrders pending, OrderReporter reporter) {
        this.routerConnection = routerConnection;
        this.rawFrame = rawFrame;
        this.pending = pending;
        this.reporter = reporter;
    }

    @Override
    public byte[] rawFrame() {
        return rawFrame;
    }

    public Connection routerConnection() {
        return routerConnection;
    }

    public PendingOrders pending() {
        return pending;
    }

    public OrderReporter reporter() {
        return reporter;
    }

    // lazy parse, once; if it throws, the Pipeline catches it and stops the chain
    public FixMessage message() {
        if (message == null) {
            message = FixParser.parse(rawFrame);
        }
        return message;
    }
}
