package com.arthurfaby.fixmebroker.net;

import com.arthurfaby.fixmebroker.order.Order;
import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.report.RecordingReporter;
import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.RejectReason;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerPipelineTest {

    private static final int BROKER_ID = 100_003;
    private static final int MARKET_ID = 100_002;

    private ServerSocket fakeRouterServer;
    private Socket routerSide;
    private ExecutorService workerPool;
    private Reactor reactor;
    private PendingOrders pending;
    private RecordingReporter reporter;
    private BrokerConnectionListener listener;

    @BeforeEach
    void startBroker() throws IOException, InterruptedException {
        fakeRouterServer = new ServerSocket(0);
        pending = new PendingOrders();
        reporter = new RecordingReporter();
        listener = new BrokerConnectionListener(pending, reporter);

        workerPool = Executors.newFixedThreadPool(4);
        reactor = new Reactor(workerPool, FixFrameDecoder::new, listener);
        reactor.connect("127.0.0.1", fakeRouterServer.getLocalPort());
        reactor.start();

        routerSide = fakeRouterServer.accept();

        send(MessageFactory.logon(BROKER_ID));
        awaitAssignedId();
    }

    @AfterEach
    void stopBroker() throws IOException, InterruptedException {
        reactor.stop();
        workerPool.shutdownNow();
        routerSide.close();
        fakeRouterServer.close();
    }

    @Test
    void theLogonAssignsTheBrokerItsId() {
        assertThat(listener.connection().attachment()).isEqualTo(BROKER_ID);
    }

    @Test
    void anExecutionReportMarksTheMatchingOrderExecuted() throws IOException {
        Order order = new Order(1, MARKET_ID, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));
        pending.remember(order);

        send(MessageFactory.executed(MARKET_ID, BROKER_ID, 1, "AAPL", 100, new BigDecimal("150.50")));

        RecordingReporter.Event event = awaitEvent();
        assertThat(event.kind()).isEqualTo("EXECUTED");
        assertThat(event.order()).isEqualTo(order);
        assertThat(pending.size()).isZero();
    }

    @Test
    void aRejectReportCarriesTheReasonBackToTheOrder() throws IOException {
        Order order = new Order(2, MARKET_ID, "AAPL", Side.BUY, 999_999, new BigDecimal("150.50"));
        pending.remember(order);

        send(MessageFactory.rejected(MARKET_ID, BROKER_ID, 2, "AAPL", 999_999, "Not enough quantity"));

        RecordingReporter.Event event = awaitEvent();
        assertThat(event.kind()).isEqualTo("REJECTED");
        assertThat(event.order()).isEqualTo(order);
        assertThat(event.detail()).isEqualTo("Not enough quantity");
        assertThat(pending.size()).isZero();
    }

    @Test
    void aRouterRejectForAnUnknownTargetIsShownAgainstTheOrder() throws IOException {
        Order order = new Order(5, 999_999, "AAPL", Side.BUY, 10, new BigDecimal("150.0"));
        pending.remember(order);

        send(MessageFactory.reject(0, BROKER_ID, 5, RejectReason.UNKNOWN_TARGET));

        RecordingReporter.Event event = awaitEvent();
        assertThat(event.kind()).isEqualTo("REJECTED");
        assertThat(event.order()).isEqualTo(order);
        assertThat(event.detail()).isEqualTo(RejectReason.UNKNOWN_TARGET.getMessage());
        assertThat(pending.size()).isZero();
    }

    @Test
    void aRouterChecksumRejectWithoutClOrdIdIsShownAsATransportReject() throws IOException {

        send(MessageFactory.reject(0, BROKER_ID, RejectReason.INVALID_CHECKSUM));

        RecordingReporter.Event event = awaitEvent();
        assertThat(event.kind()).isEqualTo("TRANSPORT_REJECT");
        assertThat(event.detail()).isEqualTo(RejectReason.INVALID_CHECKSUM.getMessage());
    }

    @Test
    void aReportForAnUnknownClOrdIdIsFlaggedAsOrphanWithoutCrashing() throws IOException {
        send(MessageFactory.executed(MARKET_ID, BROKER_ID, 99, "AAPL", 100, new BigDecimal("150.50")));

        RecordingReporter.Event event = awaitEvent();
        assertThat(event.kind()).isEqualTo("ORPHAN");
        assertThat(event.clOrdId()).isEqualTo(99);
    }

    @Test
    void aCorruptedReportIsDroppedAndLeavesTheOrderPending() throws IOException, InterruptedException {
        Order order = new Order(3, MARKET_ID, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));
        pending.remember(order);

        byte[] frame = FixSerializer.serialize(
                MessageFactory.executed(MARKET_ID, BROKER_ID, 3, "AAPL", 100, new BigDecimal("150.50")));
        frame[frame.length - 2] = (byte) (frame[frame.length - 2] == '9' ? '8' : '9');
        sendRaw(frame);

        Thread.sleep(300);
        assertThat(reporter.events).isEmpty();
        assertThat(pending.size()).isEqualTo(1);
    }

    @Test
    void aMessageThatIsNeitherLogonNorReportIsIgnored() throws IOException, InterruptedException {

        send(MessageFactory.newOrder(MARKET_ID, BROKER_ID, 4, "AAPL", Side.BUY, 100, new BigDecimal("150.50")));

        Thread.sleep(300);
        assertThat(reporter.events).isEmpty();
    }

    @Test
    void reportsAreCorrelatedToTheirOwnOrdersInABurst() throws IOException {
        for (int clOrdId = 1; clOrdId <= 20; clOrdId++) {
            pending.remember(new Order(clOrdId, MARKET_ID, "AAPL", Side.BUY, clOrdId, new BigDecimal("150.50")));
        }
        for (int clOrdId = 1; clOrdId <= 20; clOrdId++) {
            send(MessageFactory.executed(MARKET_ID, BROKER_ID, clOrdId, "AAPL", clOrdId, new BigDecimal("150.50")));
        }

        long deadline = System.currentTimeMillis() + 2000;
        while (reporter.events.size() < 20 && System.currentTimeMillis() < deadline) {
            sleep();
        }

        assertThat(reporter.events).hasSize(20);

        for (RecordingReporter.Event event : reporter.events) {
            assertThat(event.kind()).isEqualTo("EXECUTED");
            assertThat(event.order().quantity()).isEqualTo(event.clOrdId());
        }
        assertThat(pending.size()).isZero();
    }

    private void send(FixMessage message) throws IOException {
        sendRaw(FixSerializer.serialize(message));
    }

    private void sendRaw(byte[] frame) throws IOException {
        routerSide.getOutputStream().write(frame);
        routerSide.getOutputStream().flush();
    }

    private RecordingReporter.Event awaitEvent() {
        long deadline = System.currentTimeMillis() + 2000;
        while (reporter.events.isEmpty() && System.currentTimeMillis() < deadline) {
            sleep();
        }
        assertThat(reporter.events).as("expected a report from the pipeline").isNotEmpty();
        return reporter.events.get(0);
    }

    private void awaitAssignedId() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (listener.connection() != null
                    && Integer.valueOf(BROKER_ID).equals(listener.connection().attachment())) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Broker never received its id from the router");
    }

    private static void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
