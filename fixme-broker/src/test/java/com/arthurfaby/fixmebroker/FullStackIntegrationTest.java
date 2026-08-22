package com.arthurfaby.fixmebroker;

import com.arthurfaby.fixmebroker.net.BrokerConnectionListener;
import com.arthurfaby.fixmebroker.order.OrderSender;
import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.report.RecordingReporter;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import com.arthurfaby.fixmemarket.book.Instrument;
import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmemarket.net.MarketConnectionListener;
import com.arthurfaby.fixmerouter.net.RouterConnectionListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class FullStackIntegrationTest {

    private final List<Reactor> reactors = new ArrayList<>();
    private final List<ExecutorService> pools = new ArrayList<>();

    private int brokerPort;
    private int marketPort;

    @BeforeEach
    void startRouter() throws IOException {
        Reactor router = newReactor(new RouterConnectionListener());
        brokerPort = router.registerServer(0, "BROKER");
        marketPort = router.registerServer(0, "MARKET");
        router.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (Reactor reactor : reactors) {
            reactor.stop();
        }
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    @Test
    void aBrokerAndAMarketGetDistinctSixDigitIds() throws Exception {
        Market market = startMarket(new Instrument("AAPL", 1000));
        Broker broker = startBroker();

        assertThat(market.id()).isBetween(100_000, 999_999);
        assertThat(broker.id()).isBetween(100_000, 999_999);
        assertThat(broker.id()).isNotEqualTo(market.id());
    }

    @Test
    void aValidBuyTravelsToTheMarketExecutesAndComesBackToTheBroker() throws Exception {
        Market market = startMarket(new Instrument("AAPL", 1000));
        Broker broker = startBroker();

        broker.sender.submit(market.id(), "AAPL", Side.BUY, 100, new BigDecimal("150.50"));

        RecordingReporter.Event event = broker.awaitReport();
        assertThat(event.kind()).isEqualTo("EXECUTED");
        assertThat(event.order().instrument()).isEqualTo("AAPL");
        assertThat(event.order().quantity()).isEqualTo(100);

        assertThat(market.book.quantityOf("AAPL")).isEqualTo(900);
    }

    @Test
    void aBuyBiggerThanTheStockComesBackRejectedWithAReadableReason() throws Exception {
        Market market = startMarket(new Instrument("AAPL", 100));
        Broker broker = startBroker();

        broker.sender.submit(market.id(), "AAPL", Side.BUY, 999_999, new BigDecimal("150.50"));

        RecordingReporter.Event event = broker.awaitReport();
        assertThat(event.kind()).isEqualTo("REJECTED");
        assertThat(event.detail()).isEqualTo("Not enough quantity");
        assertThat(market.book.quantityOf("AAPL")).isEqualTo(100);
    }

    @Test
    void anOrderOnAnInstrumentTheMarketDoesNotTradeIsRejected() throws Exception {
        Market market = startMarket(new Instrument("AAPL", 1000));
        Broker broker = startBroker();

        broker.sender.submit(market.id(), "TSLA", Side.BUY, 10, new BigDecimal("42.0"));

        RecordingReporter.Event event = broker.awaitReport();
        assertThat(event.kind()).isEqualTo("REJECTED");
        assertThat(event.detail()).isEqualTo("Unknown instrument");
    }

    @Test
    void anOrderToAMarketThatDoesNotExistIsRejectedByTheRouter() throws Exception {
        startMarket(new Instrument("AAPL", 1000));
        Broker broker = startBroker();

        broker.sender.submit(999_999, "AAPL", Side.BUY, 10, new BigDecimal("150.0"));

        RecordingReporter.Event event = broker.awaitReport();
        assertThat(event.kind()).isEqualTo("REJECTED");
        assertThat(event.detail()).isEqualTo("Unknown target");
    }

    @Test
    void aSellIncrementsTheRealMarketBook() throws Exception {
        Market market = startMarket(new Instrument("AAPL", 100));
        Broker broker = startBroker();

        broker.sender.submit(market.id(), "AAPL", Side.SELL, 50, new BigDecimal("151.0"));

        assertThat(broker.awaitReport().kind()).isEqualTo("EXECUTED");
        assertThat(market.book.quantityOf("AAPL")).isEqualTo(150);
    }

    @Test
    void severalBrokersHittingSeveralMarketsEachGetTheirOwnReports() throws Exception {
        Market marketA = startMarket(new Instrument("AAPL", 1000));
        Market marketB = startMarket(new Instrument("GOOG", 1000));
        Broker broker1 = startBroker();
        Broker broker2 = startBroker();

        broker1.sender.submit(marketA.id(), "AAPL", Side.BUY, 10, new BigDecimal("150.0"));
        broker1.sender.submit(marketB.id(), "GOOG", Side.BUY, 20, new BigDecimal("99.0"));
        broker2.sender.submit(marketA.id(), "AAPL", Side.BUY, 30, new BigDecimal("150.0"));

        broker1.awaitReports(2);
        broker2.awaitReports(1);

        assertThat(broker1.reports()).allMatch(e -> e.kind().equals("EXECUTED"));
        assertThat(broker1.reports()).extracting(e -> e.order().quantity())
                .containsExactlyInAnyOrder(10, 20);
        assertThat(broker2.reports()).singleElement()
                .satisfies(e -> assertThat(e.order().quantity()).isEqualTo(30));

        assertThat(marketA.book.quantityOf("AAPL")).isEqualTo(1000 - 10 - 30);
        assertThat(marketB.book.quantityOf("GOOG")).isEqualTo(1000 - 20);
    }

    private Reactor newReactor(com.arthurfaby.fixmecommon.net.ConnectionListener listener) {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        pools.add(pool);
        Reactor reactor = new Reactor(pool, FixFrameDecoder::new, listener);
        reactors.add(reactor);
        return reactor;
    }

    private Market startMarket(Instrument... instruments) throws IOException, InterruptedException {
        InstrumentBook book = InstrumentBook.of(instruments);
        MarketConnectionListener listener = new MarketConnectionListener(book);
        Reactor reactor = newReactor(listener);
        reactor.connect("127.0.0.1", marketPort);
        reactor.start();
        int id = awaitAttachment(listener::connection);
        return new Market(id, book);
    }

    private Broker startBroker() throws IOException, InterruptedException {
        PendingOrders pending = new PendingOrders();
        RecordingReporter reporter = new RecordingReporter();
        BrokerConnectionListener listener = new BrokerConnectionListener(pending, reporter);
        Reactor reactor = newReactor(listener);
        Connection connection = reactor.connect("127.0.0.1", brokerPort);
        reactor.start();
        int id = awaitAttachment(() -> connection);
        OrderSender sender = new OrderSender(pending, connection::attachment, connection::send, reporter);
        return new Broker(id, sender, reporter);
    }

    private static int awaitAttachment(java.util.function.Supplier<Connection> connection) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            Connection c = connection.get();
            if (c != null && c.attachment() != null) {
                return c.attachment();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("component never received its id from the router");
    }

    private record Market(int id, InstrumentBook book) {
    }

    private static final class Broker {
        final int id;
        final OrderSender sender;
        final RecordingReporter reporter;

        Broker(int id, OrderSender sender, RecordingReporter reporter) {
            this.id = id;
            this.sender = sender;
            this.reporter = reporter;
        }

        int id() {
            return id;
        }

        List<RecordingReporter.Event> reports() {
            return reporter.events.stream()
                    .filter(e -> !e.kind().equals("SENT")) // drop the [SENT] echo emitted on send
                    .toList();
        }

        RecordingReporter.Event awaitReport() {
            awaitReports(1);
            return reports().get(0);
        }

        void awaitReports(int count) {
            long deadline = System.currentTimeMillis() + 3000;
            while (reports().size() < count && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            assertThat(reports()).hasSizeGreaterThanOrEqualTo(count);
        }
    }
}
