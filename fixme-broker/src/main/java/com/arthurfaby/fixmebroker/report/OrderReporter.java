package com.arthurfaby.fixmebroker.report;

import com.arthurfaby.fixmebroker.order.Order;

public interface OrderReporter {

    void sent(Order order);

    void executed(Order order);

    void rejected(Order order, String reason);

    void orphanReport(int clOrdId, String detail);

    void transportReject(String reason);
}
