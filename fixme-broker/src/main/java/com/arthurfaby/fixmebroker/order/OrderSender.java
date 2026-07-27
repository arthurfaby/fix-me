package com.arthurfaby.fixmebroker.order;

import com.arthurfaby.fixmebroker.report.OrderReporter;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coeur testable de l'emission d'ordres : attribue un ClOrdID, memorise
 * l'ordre en attente, serialise le message FIX et le remet au reseau, puis
 * notifie le reporter. Decouple de la {@link com.arthurfaby.fixmecommon.net.Connection}
 * via un sink d'octets et un fournisseur d'ID, pour se tester sans socket.
 */
public final class OrderSender {

    private final PendingOrders pending;
    private final Supplier<Integer> brokerId;
    private final Consumer<byte[]> sink;
    private final OrderReporter reporter;

    public OrderSender(PendingOrders pending, Supplier<Integer> brokerId,
                       Consumer<byte[]> sink, OrderReporter reporter) {
        this.pending = pending;
        this.brokerId = brokerId;
        this.sink = sink;
        this.reporter = reporter;
    }

    /**
     * Soumet un ordre. Rend l'ordre cree, ou {@link Optional#empty()} si le
     * Broker n'a pas encore recu son ID du Router (donc ne peut pas se
     * declarer emetteur d'un message).
     */
    public Optional<Order> submit(int marketId, String instrument, Side side, int quantity, BigDecimal price) {
        Integer id = brokerId.get();
        if (id == null) {
            return Optional.empty();
        }

        int clOrdId = pending.nextClOrdId();
        Order order = new Order(clOrdId, marketId, instrument, side, quantity, price);
        // memorise AVANT l'envoi : le rapport peut revenir avant que submit() ne rende la main
        pending.remember(order);
        sink.accept(FixSerializer.serialize(
                MessageFactory.newOrder(id, marketId, clOrdId, instrument, side, quantity, price)));
        reporter.sent(order);
        return Optional.of(order);
    }
}
