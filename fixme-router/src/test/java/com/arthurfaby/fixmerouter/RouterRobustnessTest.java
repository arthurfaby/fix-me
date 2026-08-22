package com.arthurfaby.fixmerouter;

import com.arthurfaby.fixmerouter.routing.RoutingTable;
import com.arthurfaby.fixmerouter.support.FakeClient;
import com.arthurfaby.fixmerouter.support.RouterHarness;
import com.arthurfaby.fixmecommon.protocol.FixConstants;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouterRobustnessTest {

    private RouterHarness router;

    @BeforeEach
    void startRouter() throws IOException {
        router = new RouterHarness();
    }

    @AfterEach
    void stopRouter() throws InterruptedException {
        router.close();
    }

    @Test
    void aMessageDeliveredByteByByteIsReassembledAndForwardedIntact() throws IOException {
        try (FakeClient broker = router.connectBroker();
             FakeClient market = router.connectMarket()) {
            broker.readLogonId();
            int marketId = market.readLogonId();

            byte[] buy = FakeClient.buyOrder(999_999, marketId, 1, "AAPL", 100, "150.50");
            broker.sendBytesOneByOne(buy, 1);

            assertThat(market.readFrame()).isEqualTo(buy);
        }
    }

    @Test
    void twoMessagesInASingleWriteAreBothForwardedInOrder() throws IOException {
        try (FakeClient broker = router.connectBroker();
             FakeClient market = router.connectMarket()) {
            broker.readLogonId();
            int marketId = market.readLogonId();

            byte[] first = FakeClient.buyOrder(999_999, marketId, 1, "AAPL", 100, "150.50");
            byte[] second = FakeClient.buyOrder(999_999, marketId, 2, "GOOG", 50, "99.0");
            broker.send(concat(first, second));

            assertThat(market.readFrame()).isEqualTo(first);
            assertThat(market.readFrame()).isEqualTo(second);
        }
    }

    @Test
    void aGiantMessageWithoutTerminatorClosesTheConnectionWithoutTakingTheRouterDown() throws IOException {
        try (FakeClient attacker = router.connectBroker()) {
            attacker.readLogonId();

            byte[] flood = new byte[10 * 1024 * 1024];
            java.util.Arrays.fill(flood, (byte) 'A');
            try {
                attacker.send(flood);
            } catch (IOException writeRace) {
                // the Router may close the socket mid-send, that's fine
            }

            attacker.setReadTimeout(2000);
            // closed by the Router: clean EOF or RST depending on timing, always an IOException
            assertThatThrownBy(attacker::readFrame).isInstanceOf(IOException.class);
        }

        try (FakeClient fresh = router.connectBroker()) {
            assertThat(fresh.readLogonId()).isBetween(100_000, 999_999);
        }
    }

    @Test
    void garbageThatIsOnlySohBytesDoesNotCrashTheRouterNorTheConnection() throws IOException {
        try (FakeClient broker = router.connectBroker()) {
            broker.readLogonId();

            broker.send(new byte[]{FixConstants.SOH, FixConstants.SOH, FixConstants.SOH});

            // the stray SOH bytes prepend the next frame and break its checksum, so we
            // get a reject (35=3) whatever the reason. The point: no crash.
            broker.send(FakeClient.buyOrder(999_999, 999_999, 1, "AAPL", 100, "150.50"));
            assertThat(broker.readFrameAsString()).contains("35=3");
        }

        try (FakeClient fresh = router.connectBroker()) {
            assertThat(fresh.readLogonId()).isBetween(100_000, 999_999);
        }
    }

    @Test
    void anAbruptlyDisconnectedTargetIsPurgedAndFurtherOrdersAreRejected() throws Exception {
        int marketId;
        try (FakeClient market = router.connectMarket()) {
            marketId = market.readLogonId();
            market.closeAbruptly();
        }

        awaitUnregistered(marketId);

        try (FakeClient broker = router.connectBroker()) {
            broker.readLogonId();
            broker.send(FakeClient.buyOrder(999_999, marketId, 1, "AAPL", 100, "150.50"));
            assertThat(broker.readFrameAsString()).contains("58=Unknown target");
        }
    }

    @Test
    void aThousandConnectDisconnectCyclesLeaveNoDeadEntriesBehind() throws IOException {
        int baseline = RoutingTable.size();

        for (int i = 0; i < 1000; i++) {
            FakeClient client = router.connectBroker();
            client.readLogonId();
            client.close();
        }

        long deadline = System.currentTimeMillis() + 5000;
        while (RoutingTable.size() > baseline && System.currentTimeMillis() < deadline) {
            sleep();
        }
        assertThat(RoutingTable.size()).isEqualTo(baseline);
    }

    @Test
    void anOrderToAMarketThatVanishesMidBurstIsRejectedWithoutDeadlock() throws Exception {
        int marketId;
        try (FakeClient broker = router.connectBroker()) {
            broker.readLogonId();

            FakeClient market = router.connectMarket();
            marketId = market.readLogonId();

            broker.send(FakeClient.buyOrder(999_999, marketId, 1, "AAPL", 100, "150.50"));
            assertThat(market.readFrame()).isNotEmpty();

            market.closeAbruptly();
            awaitUnregistered(marketId);

            broker.send(FakeClient.buyOrder(999_999, marketId, 2, "AAPL", 100, "150.50"));
            assertThat(broker.readFrameAsString()).contains("58=Unknown target").contains("11=2");
        }
    }

    @Test
    void invalidChecksumIsRejectedToSenderAndNeverForwarded() throws IOException {
        try (FakeClient broker = router.connectBroker();
             FakeClient market = router.connectMarket()) {
            broker.readLogonId();
            int marketId = market.readLogonId();

            byte[] buy = FakeClient.buyOrder(999_999, marketId, 1, "AAPL", 100, "150.50");
            buy[buy.length - 2] = (byte) (buy[buy.length - 2] == '9' ? '8' : '9');
            broker.send(buy);

            assertThat(broker.readFrameAsString()).contains("35=3").contains("58=Invalid checksum");

            market.setReadTimeout(300);
            assertThatThrownBy(market::readFrame)
                    .isInstanceOfAny(SocketTimeoutException.class, EOFException.class);
        }
    }

    @Test
    void reportsAreRoutedBackToTheRightBrokerWhenManyShareOneMarket() throws IOException {
        try (FakeClient market = router.connectMarket();
             FakeClient brokerA = router.connectBroker();
             FakeClient brokerB = router.connectBroker();
             FakeClient brokerC = router.connectBroker()) {

            int marketId = market.readLogonId();
            int idA = brokerA.readLogonId();
            int idB = brokerB.readLogonId();
            int idC = brokerC.readLogonId();

            brokerA.send(FakeClient.buyOrder(idA, marketId, 11, "AAPL", 1, "150.0"));
            brokerB.send(FakeClient.buyOrder(idB, marketId, 22, "AAPL", 2, "150.0"));
            brokerC.send(FakeClient.buyOrder(idC, marketId, 33, "AAPL", 3, "150.0"));

            for (int i = 0; i < 3; i++) {
                FixMessage order = FixParser.parse(market.readFrame());
                int sender = order.getInt(FixTag.SENDER_ID);
                int clOrdId = order.getInt(FixTag.CLIENT_ORDER_ID);
                market.send(FixSerializer.serialize(
                        MessageFactory.executed(marketId, sender, clOrdId, "AAPL", order.getInt(FixTag.QUANTITY), price("150.0"))));
            }

            assertReportBelongsTo(brokerA, marketId, idA, 11);
            assertReportBelongsTo(brokerB, marketId, idB, 22);
            assertReportBelongsTo(brokerC, marketId, idC, 33);
        }
    }

    @Test
    void manyBrokersAndManyMarketsKeepTheirReportsIsolated() throws IOException {
        int n = 4;
        List<FakeClient> markets = new ArrayList<>();
        List<FakeClient> brokers = new ArrayList<>();
        int[] marketIds = new int[n];
        int[] brokerIds = new int[n];
        try {
            for (int i = 0; i < n; i++) {
                FakeClient m = router.connectMarket();
                marketIds[i] = m.readLogonId();
                markets.add(m);
            }
            for (int i = 0; i < n; i++) {
                FakeClient b = router.connectBroker();
                brokerIds[i] = b.readLogonId();
                brokers.add(b);
            }

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int clOrdId = i * 100 + j;
                    brokers.get(i).send(FakeClient.buyOrder(brokerIds[i], marketIds[j], clOrdId, "AAPL", 1, "10.0"));
                }
            }

            for (int j = 0; j < n; j++) {
                FakeClient m = markets.get(j);
                for (int k = 0; k < n; k++) {
                    FixMessage order = FixParser.parse(m.readFrame());
                    assertThat(order.getInt(FixTag.TARGET_ID)).isEqualTo(marketIds[j]);
                    m.send(FixSerializer.serialize(MessageFactory.executed(
                            marketIds[j], order.getInt(FixTag.SENDER_ID), order.getInt(FixTag.CLIENT_ORDER_ID),
                            "AAPL", 1, price("10.0"))));
                }
            }

            for (int i = 0; i < n; i++) {
                boolean[] seenFromMarket = new boolean[n];
                for (int k = 0; k < n; k++) {
                    FixMessage report = FixParser.parse(brokers.get(i).readFrame());
                    assertThat(report.getInt(FixTag.TARGET_ID)).isEqualTo(brokerIds[i]);
                    int clOrdId = report.getInt(FixTag.CLIENT_ORDER_ID);
                    assertThat(clOrdId / 100).isEqualTo(i);
                    int j = clOrdId % 100;
                    assertThat(report.getInt(FixTag.SENDER_ID)).isEqualTo(marketIds[j]);
                    assertThat(seenFromMarket[j]).isFalse();
                    seenFromMarket[j] = true;
                }
            }
        } finally {
            closeAll(markets);
            closeAll(brokers);
        }
    }

    private static void assertReportBelongsTo(FakeClient broker, int marketId, int brokerId, int clOrdId) throws IOException {
        FixMessage report = FixParser.parse(broker.readFrame());
        assertThat(report.getInt(FixTag.SENDER_ID)).isEqualTo(marketId);
        assertThat(report.getInt(FixTag.TARGET_ID)).isEqualTo(brokerId);
        assertThat(report.getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(clOrdId);
    }

    private static void awaitUnregistered(int fixId) {
        long deadline = System.currentTimeMillis() + 2000;
        while (RoutingTable.find(fixId).isPresent() && System.currentTimeMillis() < deadline) {
            sleep();
        }
        assertThat(RoutingTable.find(fixId)).isEmpty();
    }

    private static java.math.BigDecimal price(String value) {
        return new java.math.BigDecimal(value);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void closeAll(List<FakeClient> clients) {
        for (FakeClient client : clients) {
            try {
                client.close();
            } catch (IOException ignored) {

            }
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
