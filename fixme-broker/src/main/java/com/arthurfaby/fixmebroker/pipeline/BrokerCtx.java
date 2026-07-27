package com.arthurfaby.fixmebroker.pipeline;

import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.report.OrderReporter;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.pipeline.HasRawFrame;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;

/**
 * Contexte du pipeline Broker : la connexion (unique) vers le Router, la
 * frame brute, et le FixMessage parse une seule fois de facon paresseuse.
 * Le parsing est cache ici plutot que refait dans chaque maillon ; s'il
 * echoue, l'exception remonte et le Pipeline arrete proprement la chaine.
 */
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

    /** Parse a la demande, une seule fois. Peut lever FixParseException. */
    public FixMessage message() {
        if (message == null) {
            message = FixParser.parse(rawFrame);
        }
        return message;
    }
}
