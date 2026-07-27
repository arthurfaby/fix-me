package com.arthurfaby.fixmebroker.order;

import com.arthurfaby.fixmebroker.report.RecordingReporter;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coeur de l'emission d'ordres, teste sans socket : le sink d'octets capture
 * le message FIX serialise, le fournisseur d'ID simule l'etat de connexion.
 */
class OrderSenderTest {

    private final List<byte[]> sink = new ArrayList<>();
    private final RecordingReporter reporter = new RecordingReporter();

    private OrderSender senderWithId(Integer brokerId) {
        return new OrderSender(new PendingOrders(), () -> brokerId, sink::add, reporter);
    }

    @Test
    void submitBuildsAValidFixOrderAndReportsItAsSent() {
        PendingOrders pending = new PendingOrders();
        OrderSender sender = new OrderSender(pending, () -> 100_001, sink::add, reporter);

        Optional<Order> created = sender.submit(100_002, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));

        assertThat(created).isPresent();
        assertThat(sink).hasSize(1);

        FixMessage sent = FixParser.parse(sink.get(0));
        assertThat(sent.getString(FixTag.MESSAGE_TYPE)).isEqualTo(MessageType.ORDER.getValue());
        assertThat(sent.getInt(FixTag.SENDER_ID)).isEqualTo(100_001);
        assertThat(sent.getInt(FixTag.TARGET_ID)).isEqualTo(100_002);
        assertThat(sent.getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(1);
        assertThat(sent.getString(FixTag.INSTRUMENT)).isEqualTo("AAPL");
        assertThat(sent.getInt(FixTag.SIDE)).isEqualTo(Side.BUY.getValue());
        assertThat(sent.getInt(FixTag.QUANTITY)).isEqualTo(100);
        assertThat(sent.getPrice(FixTag.PRICE)).isEqualByComparingTo("150.50");

        assertThat(reporter.last().kind()).isEqualTo("SENT");
        assertThat(pending.take(1)).isPresent();
    }

    @Test
    void submitRemembersTheOrderBeforeHandingBytesToTheNetwork() {
        // le rapport peut revenir avant que submit() ne rende la main : l'ordre
        // doit deja etre memorise au moment ou le sink est appele
        AtomicReference<PendingOrders> ref = new AtomicReference<>();
        PendingOrders pending = new PendingOrders();
        ref.set(pending);

        OrderSender sender = new OrderSender(pending, () -> 100_001, bytes -> {
            int clOrdId = FixParser.parse(bytes).getInt(FixTag.CLIENT_ORDER_ID);
            assertThat(ref.get().take(clOrdId)).as("order remembered before send").isPresent();
            ref.get().remember(new Order(clOrdId, 0, "AAPL", Side.BUY, 1, BigDecimal.ONE)); // remets pour le reste du test
        }, reporter);

        sender.submit(100_002, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));
    }

    @Test
    void submitIsRefusedWhenTheBrokerHasNoIdYet() {
        OrderSender sender = senderWithId(null);

        Optional<Order> created = sender.submit(100_002, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));

        assertThat(created).isEmpty();
        assertThat(sink).isEmpty();
        assertThat(reporter.events).isEmpty();
    }

    @Test
    void consecutiveSubmitsGetIncrementingClOrdIds() {
        OrderSender sender = senderWithId(100_001);

        sender.submit(100_002, "AAPL", Side.BUY, 1, BigDecimal.TEN);
        sender.submit(100_002, "GOOG", Side.SELL, 2, BigDecimal.TEN);

        assertThat(FixParser.parse(sink.get(0)).getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(1);
        assertThat(FixParser.parse(sink.get(1)).getInt(FixTag.CLIENT_ORDER_ID)).isEqualTo(2);
        assertThat(FixParser.parse(sink.get(1)).getInt(FixTag.SIDE)).isEqualTo(Side.SELL.getValue());
    }
}
