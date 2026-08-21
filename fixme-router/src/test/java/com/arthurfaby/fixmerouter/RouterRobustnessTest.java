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

/**
 * Phase 9 (PLAN.md §4.3) : ce que le correcteur va tenter contre le Router.
 * Chaque test est une attaque et son invariant attendu ; le Router doit
 * rester debout dans tous les cas.
 */
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

            byte[] flood = new byte[10 * 1024 * 1024]; // 10 Mo de 'A', jamais de 10=
            java.util.Arrays.fill(flood, (byte) 'A');
            try {
                attacker.send(flood);
            } catch (IOException writeRace) {
                // le Router peut fermer la socket en plein envoi : acceptable
            }

            attacker.setReadTimeout(2000);
            // le Router ferme la connexion : selon le timing OS, EOF propre (-1)
            // ou RST (SocketException) - dans les deux cas une IOException, jamais un OOM
            assertThatThrownBy(attacker::readFrame).isInstanceOf(IOException.class);
        }

        // le Router est toujours vivant : une connexion fraiche recoit son Logon
        try (FakeClient fresh = router.connectBroker()) {
            assertThat(fresh.readLogonId()).isBetween(100_000, 999_999);
        }
    }

    @Test
    void garbageThatIsOnlySohBytesDoesNotCrashTheRouterNorTheConnection() throws IOException {
        try (FakeClient broker = router.connectBroker()) {
            broker.readLogonId();

            broker.send(new byte[]{FixConstants.SOH, FixConstants.SOH, FixConstants.SOH});

            // La connexion survit et le Router repond. Les SOH parasites restent
            // dans le buffer d'accumulation et se prefixent a la frame suivante,
            // dont le checksum devient invalide : on recoit donc un reject (35=3).
            // L'invariant teste ici est "pas de crash, connexion vivante, reponse",
            // pas la raison precise du reject.
            broker.send(FakeClient.buyOrder(999_999, 999_999, 1, "AAPL", 100, "150.50"));
            assertThat(broker.readFrameAsString()).contains("35=3");
        }

        // et le Router accepte toujours de nouvelles connexions
        try (FakeClient fresh = router.connectBroker()) {
            assertThat(fresh.readLogonId()).isBetween(100_000, 999_999);
        }
    }

    @Test
    void anAbruptlyDisconnectedTargetIsPurgedAndFurtherOrdersAreRejected() throws Exception {
        int marketId;
        try (FakeClient market = router.connectMarket()) {
            marketId = market.readLogonId();
            market.closeAbruptly(); // RST, pas de FIN propre
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
            client.readLogonId(); // garantit que l'entree est bien enregistree avant de fermer
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

            // premier ordre : le market est la, il le recoit
            broker.send(FakeClient.buyOrder(999_999, marketId, 1, "AAPL", 100, "150.50"));
            assertThat(market.readFrame()).isNotEmpty();

            // le market disparait brutalement au milieu de la rafale
            market.closeAbruptly();
            awaitUnregistered(marketId);

            // les ordres suivants sont rejetes, le Router ne bloque pas
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
            buy[buy.length - 2] = (byte) (buy[buy.length - 2] == '9' ? '8' : '9'); // corromp le checksum
            broker.send(buy);

            assertThat(broker.readFrameAsString()).contains("35=3").contains("58=Invalid checksum");

            // le market ne doit rien recevoir
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

            // fan-in : trois brokers frappent le meme market
            brokerA.send(FakeClient.buyOrder(idA, marketId, 11, "AAPL", 1, "150.0"));
            brokerB.send(FakeClient.buyOrder(idB, marketId, 22, "AAPL", 2, "150.0"));
            brokerC.send(FakeClient.buyOrder(idC, marketId, 33, "AAPL", 3, "150.0"));

            // le market recoit trois ordres (ordre d'arrivee non garanti) et
            // renvoie a chacun un rapport adresse a son emetteur d'origine
            for (int i = 0; i < 3; i++) {
                FixMessage order = FixParser.parse(market.readFrame());
                int sender = order.getInt(FixTag.SENDER_ID);
                int clOrdId = order.getInt(FixTag.CLIENT_ORDER_ID);
                market.send(FixSerializer.serialize(
                        MessageFactory.executed(marketId, sender, clOrdId, "AAPL", order.getInt(FixTag.QUANTITY), price("150.0"))));
            }

            // fan-out : chaque broker ne recoit QUE son propre rapport
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

            // chaque broker i envoie un ordre a chaque market j
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int clOrdId = i * 100 + j;
                    brokers.get(i).send(FakeClient.buyOrder(brokerIds[i], marketIds[j], clOrdId, "AAPL", 1, "10.0"));
                }
            }

            // chaque market j recoit n ordres et repond a l'emetteur
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

            // chaque broker i recoit exactement n rapports, tous pour lui,
            // un par market, avec le bon ClOrdID
            for (int i = 0; i < n; i++) {
                boolean[] seenFromMarket = new boolean[n];
                for (int k = 0; k < n; k++) {
                    FixMessage report = FixParser.parse(brokers.get(i).readFrame());
                    assertThat(report.getInt(FixTag.TARGET_ID)).isEqualTo(brokerIds[i]);
                    int clOrdId = report.getInt(FixTag.CLIENT_ORDER_ID);
                    assertThat(clOrdId / 100).isEqualTo(i); // l'ordre appartenait bien au broker i
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

    // --- helpers -----------------------------------------------------------------

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
                // best effort
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
