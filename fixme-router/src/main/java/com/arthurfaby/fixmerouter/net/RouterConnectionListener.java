package com.arthurfaby.fixmerouter.net;

import com.arthurfaby.fixmerouter.pipeline.ForwardingHandler;
import com.arthurfaby.fixmerouter.pipeline.RouterCtx;
import com.arthurfaby.fixmerouter.pipeline.RoutingResolutionHandler;
import com.arthurfaby.fixmerouter.routing.IdGenerator;
import com.arthurfaby.fixmerouter.routing.RoutingTable;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.net.ConnectionListener;
import com.arthurfaby.fixmecommon.pipeline.ChecksumValidationHandler;
import com.arthurfaby.fixmecommon.pipeline.Pipeline;
import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixDebug;
import com.arthurfaby.fixmecommon.protocol.enums.RejectReason;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RouterConnectionListener implements ConnectionListener {

    private static final int ROUTER_ID = 0;
    private static final Logger logger = LogManager.getLogger(RouterConnectionListener.class);

    private final Pipeline<RouterCtx> pipeline = Pipeline.<RouterCtx>builder()
            .addHandler(new ChecksumValidationHandler<>())
            .addHandler(new RoutingResolutionHandler())
            .addHandler(new ForwardingHandler())
            .build();

    @Override
    public void onConnected(Connection connection) {
        Integer id = IdGenerator.generate();
        connection.attach(id);
        RoutingTable.register(id, connection);

        FixMessage logon = MessageFactory.logon(id);
        connection.send(FixSerializer.serialize(logon));
        logger.debug("[{}] Connection established", id);
    }

    @Override
    public void onMessage(Connection connection, byte[] frame) {
        logger.debug("[{}] Message received: {}", connection.attachment(), FixDebug.getReadableWire(frame));
        if (!Checksum.verify(frame)) {
            rejectInvalidChecksum(connection);
            return;
        }
        logger.debug("[{}] Executing pipeline", connection.attachment());
        pipeline.execute(new RouterCtx(connection, frame));
    }

    @Override
    public void onDisconnected(Connection connection) {
        Integer id = (Integer) connection.attachment();
        RoutingTable.unregister(id);
        logger.debug("[{}] Connection closed", connection.attachment());
    }

    private void rejectInvalidChecksum(Connection connection) {
        if (!(connection.attachment() instanceof Integer senderId)) {
            return;
        }
        FixMessage reject = MessageFactory.reject(ROUTER_ID, senderId, RejectReason.INVALID_CHECKSUM);
        connection.send(FixSerializer.serialize(reject));
    }
}
