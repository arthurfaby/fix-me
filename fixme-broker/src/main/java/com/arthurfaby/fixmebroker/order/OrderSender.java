package com.arthurfaby.fixmebroker.order;

import com.arthurfaby.fixmebroker.report.OrderReporter;
import com.arthurfaby.fixmecommon.protocol.FixSerializer;
import com.arthurfaby.fixmecommon.protocol.MessageFactory;
import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    public Optional<Order> submit(int marketId, String instrument, Side side, int quantity, BigDecimal price) {
        Integer id = brokerId.get();
        if (id == null) {
            return Optional.empty();
        }

        int clOrdId = pending.nextClOrdId();
        Order order = new Order(clOrdId, marketId, instrument, side, quantity, price);

        pending.remember(order); // before sending: the report can come back before submit() returns
        sink.accept(FixSerializer.serialize(
                MessageFactory.newOrder(id, marketId, clOrdId, instrument, side, quantity, price)));
        reporter.sent(order);
        return Optional.of(order);
    }
}
