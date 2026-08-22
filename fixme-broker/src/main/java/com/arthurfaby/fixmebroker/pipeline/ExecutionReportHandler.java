package com.arthurfaby.fixmebroker.pipeline;

import com.arthurfaby.fixmebroker.order.Order;
import com.arthurfaby.fixmecommon.pipeline.Handler;
import com.arthurfaby.fixmecommon.pipeline.HandlerResult;
import com.arthurfaby.fixmecommon.protocol.FixMessage;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.enums.OrderStatus;

import java.util.Optional;

public final class ExecutionReportHandler implements Handler<BrokerCtx> {

    @Override
    public HandlerResult execute(BrokerCtx ctx) {
        FixMessage message = ctx.message();
        String type = message.getString(FixTag.MESSAGE_TYPE);

        if (MessageType.EXECUTION_REPORT.getValue().equals(type)) {
            handleExecutionReport(ctx, message);
        } else if (MessageType.REJECT.getValue().equals(type)) {
            handleTransportReject(ctx, message);
        }

        return HandlerResult.STOP;
    }

    private static void handleExecutionReport(BrokerCtx ctx, FixMessage report) {
        int clOrdId = report.getInt(FixTag.CLIENT_ORDER_ID);
        Optional<Order> matched = ctx.pending().take(clOrdId);
        if (matched.isEmpty()) {
            ctx.reporter().orphanReport(clOrdId, statusLabel(report));
            return;
        }

        Order order = matched.get();
        if (report.getInt(FixTag.ORDER_STATUS) == OrderStatus.EXECUTED.getValue()) {
            ctx.reporter().executed(order);
        } else {
            ctx.reporter().rejected(order, report.getString(FixTag.REJECT_REASON));
        }
    }

    private static void handleTransportReject(BrokerCtx ctx, FixMessage reject) {
        String reason = reject.getString(FixTag.REJECT_REASON);

        // invalid checksum: the Router couldn't trust the ClOrdID, so it's absent
        if (!reject.has(FixTag.CLIENT_ORDER_ID)) {
            ctx.reporter().transportReject(reason);
            return;
        }

        int clOrdId = reject.getInt(FixTag.CLIENT_ORDER_ID);
        ctx.pending().take(clOrdId).ifPresentOrElse(
                order -> ctx.reporter().rejected(order, reason),
                () -> ctx.reporter().orphanReport(clOrdId, reason));
    }

    private static String statusLabel(FixMessage report) {
        return report.getInt(FixTag.ORDER_STATUS) == OrderStatus.EXECUTED.getValue() ? "executed" : "rejected";
    }
}
