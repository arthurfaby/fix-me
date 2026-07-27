package com.arthurfaby.fixmebroker.net;

import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.pipeline.BrokerCtx;
import com.arthurfaby.fixmebroker.pipeline.ExecutionReportHandler;
import com.arthurfaby.fixmebroker.pipeline.IdAssignmentHandler;
import com.arthurfaby.fixmebroker.report.OrderReporter;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.net.ConnectionListener;
import com.arthurfaby.fixmecommon.pipeline.ChecksumValidationHandler;
import com.arthurfaby.fixmecommon.pipeline.Pipeline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Chef d'orchestre du Broker : une seule connexion sortante vers le Router.
 * Chaque frame recue traverse la chaine
 * Checksum -> IdAssignment (Logon) -> ExecutionReport. Le SerialExecutor de
 * la connexion garantit l'ordre des rapports ; ce listener ne partage aucun
 * etat mutable au-dela de la Connection et de PendingOrders (thread-safe).
 */
public final class BrokerConnectionListener implements ConnectionListener {

    private static final Logger LOGGER = LogManager.getLogger(BrokerConnectionListener.class);

    private final Pipeline<BrokerCtx> pipeline;
    private final PendingOrders pending;
    private final OrderReporter reporter;

    private volatile Connection connection;

    public BrokerConnectionListener(PendingOrders pending, OrderReporter reporter) {
        this.pending = pending;
        this.reporter = reporter;
        this.pipeline = Pipeline.<BrokerCtx>builder()
                .addHandler(new ChecksumValidationHandler<>())
                .addHandler(new IdAssignmentHandler())
                .addHandler(new ExecutionReportHandler())
                .build();
    }

    @Override
    public void onConnected(Connection routerConnection) {
        this.connection = routerConnection;
    }

    @Override
    public void onMessage(Connection routerConnection, byte[] frame) {
        pipeline.execute(new BrokerCtx(routerConnection, frame, pending, reporter));
    }

    @Override
    public void onDisconnected(Connection routerConnection) {
        LOGGER.warn("Disconnected from router");
    }

    public Connection connection() {
        return connection;
    }
}
