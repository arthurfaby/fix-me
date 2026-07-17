package com.arthurfaby.fixmecommon.protocol;

import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.enums.OrderStatus;
import com.arthurfaby.fixmecommon.protocol.enums.RejectReason;
import java.math.BigDecimal;

public final class MessageFactory {
    private MessageFactory() {}

    private static final int ROUTER_ID = 0; // reserved id

    public static FixMessage newOrder(int senderId, int targetId, int clOrdId,
                                      String instrument, Side side, int quantity, BigDecimal price) {
        return FixMessage.builder()
                .set(FixTag.SENDER_ID, senderId)
                .set(FixTag.TARGET_ID, targetId)
                .set(FixTag.MESSAGE_TYPE, MessageType.ORDER)
                .set(FixTag.CLIENT_ORDER_ID, clOrdId)
                .set(FixTag.INSTRUMENT, instrument)
                .set(FixTag.SIDE, side)
                .set(FixTag.QUANTITY, quantity)
                .set(FixTag.PRICE, price)
                .build();
    }

    public static FixMessage executed(int senderId, int targetId, int clOrdId,
                                      String instrument, int quantity, BigDecimal price) {
        return FixMessage.builder()
                .set(FixTag.SENDER_ID, senderId)
                .set(FixTag.TARGET_ID, targetId)
                .set(FixTag.MESSAGE_TYPE, MessageType.EXECUTION_REPORT)
                .set(FixTag.CLIENT_ORDER_ID, clOrdId)
                .set(FixTag.INSTRUMENT, instrument)
                .set(FixTag.QUANTITY, quantity)
                .set(FixTag.PRICE, price)
                .set(FixTag.ORDER_STATUS, OrderStatus.EXECUTED)
                .build();
    }

    public static FixMessage rejected(int senderId, int targetId, int clOrdId,
                                      String instrument, int quantity, String reasonText) {
        return FixMessage.builder()
                .set(FixTag.SENDER_ID, senderId)
                .set(FixTag.TARGET_ID, targetId)
                .set(FixTag.MESSAGE_TYPE, MessageType.EXECUTION_REPORT)
                .set(FixTag.CLIENT_ORDER_ID, clOrdId)
                .set(FixTag.INSTRUMENT, instrument)
                .set(FixTag.QUANTITY, quantity)
                .set(FixTag.ORDER_STATUS, OrderStatus.REJECTED)
                .set(FixTag.REJECT_REASON, reasonText)
                .build();
    }

    public static FixMessage logon(int assignedId) {
        return FixMessage.builder()
                .set(FixTag.SENDER_ID, ROUTER_ID)
                .set(FixTag.TARGET_ID, assignedId)
                .set(FixTag.MESSAGE_TYPE, MessageType.LOGON)
                .build();
    }

    // Reject transport (Router) — checksum invalide : rien dans le message d'origine
    // n'est fiable, donc pas de ClOrdID.
    public static FixMessage reject(int senderId, int targetId, RejectReason reason) {
        return FixMessage.builder()
                .set(FixTag.SENDER_ID, senderId)
                .set(FixTag.TARGET_ID, targetId)
                .set(FixTag.MESSAGE_TYPE, MessageType.REJECT)
                .set(FixTag.REJECT_REASON, reason.getMessage())
                .build();
    }

    // Reject transport avec corrélation — le checksum était valide, seul le
    // routage a échoué (UNKNOWN_TARGET / TARGET_UNREACHABLE) : ClOrdID est fiable.
    public static FixMessage reject(int senderId, int targetId, int clOrdId, RejectReason reason) {
        return FixMessage.builder()
                .set(FixTag.SENDER_ID, senderId)
                .set(FixTag.TARGET_ID, targetId)
                .set(FixTag.MESSAGE_TYPE, MessageType.REJECT)
                .set(FixTag.CLIENT_ORDER_ID, clOrdId)
                .set(FixTag.REJECT_REASON, reason.getMessage())
                .build();
    }
}