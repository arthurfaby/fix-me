package com.arthurfaby.fixmebroker.order;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PendingOrders {

    private final ConcurrentHashMap<Integer, Order> byClOrdId = new ConcurrentHashMap<>();
    private final AtomicInteger nextClOrdId = new AtomicInteger(1);

    public int nextClOrdId() {
        return nextClOrdId.getAndIncrement();
    }

    public void remember(Order order) {
        byClOrdId.put(order.clOrdId(), order);
    }

    public Optional<Order> take(int clOrdId) {
        return Optional.ofNullable(byClOrdId.remove(clOrdId));
    }

    public int size() {
        return byClOrdId.size();
    }
}
