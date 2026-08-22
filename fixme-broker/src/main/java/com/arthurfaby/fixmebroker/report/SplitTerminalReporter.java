package com.arthurfaby.fixmebroker.report;

import com.arthurfaby.fixmebroker.cli.SplitTerminal;
import com.arthurfaby.fixmebroker.order.Order;

public final class SplitTerminalReporter implements OrderReporter {

    @Override
    public void sent(Order order) {
        SplitTerminal.writeCommand(String.format("%-10s %s -> market %d", "[SENT]", describe(order), order.marketId()));
    }

    @Override
    public void executed(Order order) {
        SplitTerminal.writeCommand(String.format("%-10s %s", "[EXECUTED]", describe(order)));
    }

    @Override
    public void rejected(Order order, String reason) {
        SplitTerminal.writeCommand(String.format("%-10s #%d : %s", "[REJECTED]", order.clOrdId(), reason));
    }

    @Override
    public void orphanReport(int clOrdId, String detail) {
        SplitTerminal.writeCommand(String.format("%-10s #%d (no matching order): %s", "[REPORT]", clOrdId, detail));
    }

    @Override
    public void transportReject(String reason) {
        SplitTerminal.writeCommand(String.format("%-10s : %s", "[REJECTED]", reason));
    }

    private static String describe(Order order) {
        return String.format("#%d %s %d %s @ %s",
                order.clOrdId(), order.sideLabel(), order.quantity(), order.instrument(), order.price().toPlainString());
    }
}
