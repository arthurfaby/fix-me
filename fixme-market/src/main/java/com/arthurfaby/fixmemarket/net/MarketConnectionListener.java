package com.arthurfaby.fixmemarket.net;

import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmemarket.pipeline.ExecutionHandler;
import com.arthurfaby.fixmemarket.pipeline.InstrumentKnownHandler;
import com.arthurfaby.fixmemarket.pipeline.MarketCtx;
import com.arthurfaby.fixmemarket.pipeline.OrderTypeHandler;
import com.arthurfaby.fixmemarket.pipeline.QuantityAvailableHandler;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.net.ConnectionListener;
import com.arthurfaby.fixmecommon.pipeline.ChecksumValidationHandler;
import com.arthurfaby.fixmecommon.pipeline.Pipeline;
import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.exception.FixParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Chef d'orchestre du Market : une seule connexion sortante vers le Router.
 * Le premier message recu sur cette connexion est toujours le Logon du
 * Router (35=A) qui attribue l'ID du Market - il est intercepte ici et ne
 * passe jamais par le pipeline de trading. Tout le reste est un ordre.
 */
public final class MarketConnectionListener implements ConnectionListener {

    private Connection connection;

    private static final Logger LOGGER = LogManager.getLogger(MarketConnectionListener.class);

    private final Pipeline<MarketCtx> pipeline;

    public MarketConnectionListener(InstrumentBook book) {
        this.pipeline = Pipeline.<MarketCtx>builder()
                .addHandler(new ChecksumValidationHandler<>())
                .addHandler(new OrderTypeHandler())
                .addHandler(new InstrumentKnownHandler(book))
                .addHandler(new QuantityAvailableHandler(book))
                .addHandler(new ExecutionHandler(book))
                .build();
    }

    @Override
    public void onConnected(Connection routerConnection) {
        this.connection = routerConnection;
    }

    @Override
    public void onMessage(Connection routerConnection, byte[] frame) {
        if (!Checksum.verify(frame)) {
            LOGGER.warn("Invalid checksum from router, dropping frame");
            return;
        }

        FixMessage message;
        try {
            message = FixParser.parse(frame);
        } catch (FixParseException e) {
            LOGGER.warn("Malformed frame from router, dropping", e);
            return;
        }

        if (MessageType.LOGON.getValue().equals(message.getString(FixTag.MESSAGE_TYPE))) {
            int assignedId = message.getInt(FixTag.TARGET_ID);
            routerConnection.attach(assignedId);
            LOGGER.info("Assigned id {}", assignedId);
            return;
        }

        pipeline.execute(new MarketCtx(routerConnection, frame));
    }

    @Override
    public void onDisconnected(Connection routerConnection) {
        LOGGER.warn("Disconnected from router");
    }

    public Connection connection() {
        return connection;
    }
}
