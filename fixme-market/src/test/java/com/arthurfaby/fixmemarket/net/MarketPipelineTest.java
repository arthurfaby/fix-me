package com.arthurfaby.fixmemarket.net;

import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.enums.OrderStatus;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import com.arthurfaby.fixmemarket.book.Instrument;
import com.arthurfaby.fixmemarket.book.InstrumentBook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DoD phase 6 (PLAN.md), volet "bout en bout" : la chaine complete du Market
 * (Checksum -> OrderType -> InstrumentKnown -> QuantityAvailable -> Execution)
 * exercee a travers un vrai Reactor, face a un faux Router en socket
 * bloquante cote test - meme approche que RouterIntegrationTest.
 */
class MarketPipelineTest {

    private static final int MARKET_ID = 100_002;
    private static final int BROKER_ID = 100_001;

    private ServerSocket fakeRouterServer;
    private Socket routerSide;
    private ExecutorService workerPool;
    private Reactor reactor;
    private InstrumentBook book;

    private final FixFrameDecoder decoder = new FixFrameDecoder();
    private final Deque<byte[]> pending = new ArrayDeque<>();

    @BeforeEach
    void startMarket() throws IOException, InterruptedException {
        fakeRouterServer = new ServerSocket(0);
        book = InstrumentBook.of(new Instrument("AAPL", 1000), new Instrument("GOOG", 500));

        workerPool = Executors.newFixedThreadPool(4);
        MarketConnectionListener listener = new MarketConnectionListener(book);
        reactor = new Reactor(workerPool, FixFrameDecoder::new, listener);
        reactor.connect("127.0.0.1", fakeRouterServer.getLocalPort());
        reactor.start();

        routerSide = fakeRouterServer.accept();
        routerSide.setSoTimeout(2000);

        send(MessageFactory.logon(MARKET_ID));
        awaitAssignedId(listener);
    }

    @AfterEach
    void stopMarket() throws IOException, InterruptedException {
        reactor.stop();
        workerPool.shutdownNow();
        routerSide.close();
        fakeRouterServer.close();
    }

    @Test
    void validBuyIsExecutedAndDecrementsTheBook() throws IOException {
        send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, 1, "AAPL", Side.BUY, 100, price("150.50")));

        FixMessage report = readMessage();
        assertThat(report.getString(FixTag.MESSAGE_TYPE)).isEqualTo(MessageType.EXECUTION_REPORT.getValue());
        assertThat(report.getInt(FixTag.ORDER_STATUS)).isEqualTo(OrderStatus.EXECUTED.getValue());
        assertThat(report.getInt(FixTag.SENDER_ID)).isEqualTo(MARKET_ID);
        assertThat(report.getInt(FixTag.TARGET_ID)).isEqualTo(BROKER_ID);
        assertThat(report.getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(1);
        assertThat(report.getString(FixTag.INSTRUMENT)).isEqualTo("AAPL");
        assertThat(report.getInt(FixTag.QUANTITY)).isEqualTo(100);
        assertThat(report.getPrice(FixTag.PRICE)).isEqualByComparingTo("150.50");

        assertThat(book.quantityOf("AAPL")).isEqualTo(900);
    }

    @Test
    void buyBeyondStockIsRejectedAndTheBookIsUntouched() throws IOException {
        send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, 2, "AAPL", Side.BUY, 999_999, price("150.50")));

        FixMessage report = readMessage();
        assertThat(report.getString(FixTag.MESSAGE_TYPE)).isEqualTo(MessageType.EXECUTION_REPORT.getValue());
        assertThat(report.getInt(FixTag.ORDER_STATUS)).isEqualTo(OrderStatus.REJECTED.getValue());
        assertThat(report.getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(2);
        assertThat(report.getString(FixTag.REJECT_REASON)).isEqualTo("Not enough quantity");

        assertThat(book.quantityOf("AAPL")).isEqualTo(1000);
    }

    @Test
    void orderOnAnInstrumentNotTradedHereIsRejected() throws IOException {
        send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, 3, "TSLA", Side.BUY, 10, price("42.0")));

        FixMessage report = readMessage();
        assertThat(report.getInt(FixTag.ORDER_STATUS)).isEqualTo(OrderStatus.REJECTED.getValue());
        assertThat(report.getString(FixTag.REJECT_REASON)).isEqualTo("Unknown instrument");
        assertThat(book.isKnown("TSLA")).isFalse();
    }

    @Test
    void sellIsExecutedAndIncrementsTheBook() throws IOException {
        send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, 4, "AAPL", Side.SELL, 50, price("151.0")));

        FixMessage report = readMessage();
        assertThat(report.getInt(FixTag.ORDER_STATUS)).isEqualTo(OrderStatus.EXECUTED.getValue());
        assertThat(book.quantityOf("AAPL")).isEqualTo(1050);
    }

    @Test
    void sellOnAnUnknownInstrumentIsRejected() throws IOException {
        send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, 5, "TSLA", Side.SELL, 50, price("42.0")));

        FixMessage report = readMessage();
        assertThat(report.getInt(FixTag.ORDER_STATUS)).isEqualTo(OrderStatus.REJECTED.getValue());
        assertThat(report.getString(FixTag.REJECT_REASON)).isEqualTo("Unknown instrument");
    }

    @Test
    void corruptedChecksumIsDroppedWithoutTouchingTheBook() throws IOException {
        byte[] frame = FixSerializer.serialize(
                MessageFactory.newOrder(BROKER_ID, MARKET_ID, 6, "AAPL", Side.BUY, 100, price("150.50")));
        // trafique un chiffre du checksum terminal : ...|10=NNN|
        frame[frame.length - 2] = (byte) (frame[frame.length - 2] == '9' ? '8' : '9');
        sendRaw(frame);

        assertNothingComesBack();
        assertThat(book.quantityOf("AAPL")).isEqualTo(1000);
    }

    @Test
    void aMessageThatIsNotAnOrderIsIgnored() throws IOException {
        // un ExecutionReport arrive par erreur sur le Market : ce n'est pas a lui d'y repondre
        send(MessageFactory.executed(BROKER_ID, MARKET_ID, 7, "AAPL", 100, price("150.50")));

        assertNothingComesBack();
        assertThat(book.quantityOf("AAPL")).isEqualTo(1000);
    }

    @Test
    void anOrderWithAnUnknownSideIsIgnored() throws IOException {
        FixMessage weirdSide = FixMessage.builder()
                .set(FixTag.SENDER_ID, BROKER_ID)
                .set(FixTag.TARGET_ID, MARKET_ID)
                .set(FixTag.MESSAGE_TYPE, MessageType.ORDER)
                .set(FixTag.CLIENT_ORDER_ID, 8)
                .set(FixTag.INSTRUMENT, "AAPL")
                .set(FixTag.SIDE, 3) // ni Buy (1) ni Sell (2)
                .set(FixTag.QUANTITY, 100)
                .set(FixTag.PRICE, price("150.50"))
                .build();
        send(weirdSide);

        assertNothingComesBack();
        assertThat(book.quantityOf("AAPL")).isEqualTo(1000);
    }

    @Test
    void twoOrdersInASingleWriteAreBothProcessedInOrder() throws IOException {
        byte[] first = FixSerializer.serialize(
                MessageFactory.newOrder(BROKER_ID, MARKET_ID, 9, "AAPL", Side.BUY, 100, price("150.50")));
        byte[] second = FixSerializer.serialize(
                MessageFactory.newOrder(BROKER_ID, MARKET_ID, 10, "GOOG", Side.BUY, 500, price("99.0")));

        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        sendRaw(both);

        assertThat(readMessage().getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(9);
        assertThat(readMessage().getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(10);
        assertThat(book.quantityOf("AAPL")).isEqualTo(900);
        assertThat(book.quantityOf("GOOG")).isZero();
    }

    @Test
    void anOrderDeliveredByteByByteIsReassembledAndExecuted() throws IOException {
        byte[] frame = FixSerializer.serialize(
                MessageFactory.newOrder(BROKER_ID, MARKET_ID, 11, "AAPL", Side.BUY, 100, price("150.50")));
        for (byte b : frame) {
            routerSide.getOutputStream().write(new byte[]{b});
            routerSide.getOutputStream().flush();
        }

        assertThat(readMessage().getInt(FixTag.ORDER_STATUS)).isEqualTo(OrderStatus.EXECUTED.getValue());
        assertThat(book.quantityOf("AAPL")).isEqualTo(900);
    }

    @Test
    void aBurstOfOrdersDrainsTheBookExactlyOnce() throws IOException {
        for (int clOrdId = 1; clOrdId <= 100; clOrdId++) {
            send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, clOrdId, "GOOG", Side.BUY, 10, price("99.0")));
        }

        // 50 ordres de 10 sur un stock de 500 : les 50 suivants doivent etre rejetes
        int executed = 0;
        int rejected = 0;
        for (int i = 0; i < 100; i++) {
            if (readMessage().getInt(FixTag.ORDER_STATUS) == OrderStatus.EXECUTED.getValue()) {
                executed++;
            } else {
                rejected++;
            }
        }

        assertThat(executed).isEqualTo(50);
        assertThat(rejected).isEqualTo(50);
        assertThat(book.quantityOf("GOOG")).isZero();
    }

    // --- plomberie du faux Router -------------------------------------------------

    private static java.math.BigDecimal price(String value) {
        return new java.math.BigDecimal(value);
    }

    private void send(FixMessage message) throws IOException {
        sendRaw(FixSerializer.serialize(message));
    }

    private void sendRaw(byte[] frame) throws IOException {
        routerSide.getOutputStream().write(frame);
        routerSide.getOutputStream().flush();
    }

    private FixMessage readMessage() throws IOException {
        return FixParser.parse(readFrame());
    }

    private byte[] readFrame() throws IOException {
        if (!pending.isEmpty()) {
            return pending.poll();
        }
        byte[] buf = new byte[4096];
        while (true) {
            int n = routerSide.getInputStream().read(buf);
            if (n == -1) {
                throw new EOFException("Market closed the connection before a full frame arrived");
            }
            List<byte[]> frames = decoder.decode(Arrays.copyOf(buf, n));
            if (!frames.isEmpty()) {
                pending.addAll(frames);
                return pending.poll();
            }
        }
    }

    /** Assure qu'aucune reponse ne remonte : le Market a bien ignore le message. */
    private void assertNothingComesBack() throws IOException {
        routerSide.setSoTimeout(300);
        try {
            assertThatThrownBy(this::readFrame)
                    .isInstanceOfAny(SocketTimeoutException.class, EOFException.class);
        } finally {
            routerSide.setSoTimeout(2000);
        }
    }

    private void awaitAssignedId(MarketConnectionListener listener) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (listener.connection() != null
                    && Integer.valueOf(MARKET_ID).equals(listener.connection().attachment())) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Market never received its id from the router");
    }

    @Test
    void theMarketAdoptsTheIdAssignedByTheRouterLogon() throws IOException {
        // le handshake du @BeforeEach a deja attribue l'ID ; on verifie qu'il est
        // bien celui utilise comme 49= dans les messages sortants
        send(MessageFactory.newOrder(BROKER_ID, MARKET_ID, 42, "AAPL", Side.BUY, 1, price("150.50")));

        assertThat(readMessage().getInt(FixTag.SENDER_ID)).isEqualTo(MARKET_ID);
    }
}
