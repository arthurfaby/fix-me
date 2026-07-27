package com.arthurfaby.fixmebroker.order;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Les ordres emis en attente de leur rapport d'execution, indexes par
 * ClOrdID. Le compteur de ClOrdID et la table sont thread-safe : les
 * ordres sont soumis depuis le thread CLI, les rapports consommes depuis
 * le thread de l'executor (SerialExecutor de la connexion).
 */
public final class PendingOrders {

    private final ConcurrentHashMap<Integer, Order> byClOrdId = new ConcurrentHashMap<>();
    private final AtomicInteger nextClOrdId = new AtomicInteger(1);

    public int nextClOrdId() {
        return nextClOrdId.getAndIncrement();
    }

    /** Enregistre un ordre juste avant son envoi. */
    public void remember(Order order) {
        byClOrdId.put(order.clOrdId(), order);
    }

    /** Retire et rend l'ordre correle a un rapport, s'il existe encore. */
    public Optional<Order> take(int clOrdId) {
        return Optional.ofNullable(byClOrdId.remove(clOrdId));
    }

    public int size() {
        return byClOrdId.size();
    }
}
