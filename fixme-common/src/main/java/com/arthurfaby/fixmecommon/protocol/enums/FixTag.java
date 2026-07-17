package com.arthurfaby.fixmecommon.protocol.enums;

import java.util.HashMap;
import java.util.Map;

public enum FixTag {
    SENDER_ID(49),
    TARGET_ID(56),
    MESSAGE_TYPE(35),
    CLIENT_ORDER_ID(11),
    INSTRUMENT(55),
    SIDE(54),
    QUANTITY(38),
    PRICE(44),
    ORDER_STATUS(39),
    REJECT_REASON(58),
    CHECKSUM(10);

    private static final Map<Integer, FixTag> BY_KEY = new HashMap<>();
    static {
        for (FixTag tag : values()) {
            BY_KEY.put(tag.key, tag);
        }
    }

    private final Integer key;

    FixTag(Integer key) {
        this.key = key;
    }

    public int getKey() {
        return key;
    }

    public static FixTag of(Integer key) {
        return BY_KEY.get(key);
    }
}
