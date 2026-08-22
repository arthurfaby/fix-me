package com.arthurfaby.fixmebroker.report;

import com.arthurfaby.fixmebroker.order.Order;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingReporter implements OrderReporter {

    public record Event(String kind, Order order, int clOrdId, String detail) {}

    public final List<Event> events = new CopyOnWriteArrayList<>();

    @Override
    public void sent(Order order) {
        events.add(new Event("SENT", order, order.clOrdId(), null));
    }

    @Override
    public void executed(Order order) {
        events.add(new Event("EXECUTED", order, order.clOrdId(), null));
    }

    @Override
    public void rejected(Order order, String reason) {
        events.add(new Event("REJECTED", order, order.clOrdId(), reason));
    }

    @Override
    public void orphanReport(int clOrdId, String detail) {
        events.add(new Event("ORPHAN", null, clOrdId, detail));
    }

    @Override
    public void transportReject(String reason) {
        events.add(new Event("TRANSPORT_REJECT", null, -1, reason));
    }

    public Event last() {
        return events.get(events.size() - 1);
    }
}
