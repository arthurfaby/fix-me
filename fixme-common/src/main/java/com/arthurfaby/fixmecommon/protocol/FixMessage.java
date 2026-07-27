package com.arthurfaby.fixmecommon.protocol;

import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.MessageType;
import com.arthurfaby.fixmecommon.protocol.enums.OrderStatus;
import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;

public final class FixMessage {
    private final Map<Integer, String> fields;

    FixMessage(Map<Integer, String> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public String getString(FixTag tag) {
        String v = fields.get(tag.getKey());
        if (v == null) throw new NoSuchElementException("Missing tag " + tag);
        return v;
    }

    public boolean has(FixTag tag) {
        return fields.containsKey(tag.getKey());
    }

    public int getInt(FixTag tag) { return Integer.parseInt(getString(tag)); }
    public BigDecimal getPrice(FixTag tag) { return new BigDecimal(getString(tag)); }

    Set<Map.Entry<Integer, String>> entries() { return fields.entrySet(); }

    public static final class Builder {
        private final Map<Integer, String> fields = new LinkedHashMap<>();

        public Builder set(FixTag tag, String value) { fields.put(tag.getKey(), value); return this; }
        public Builder set(FixTag tag, int value) { return set(tag, String.valueOf(value)); }
        public Builder set(FixTag tag, BigDecimal value) { return set(tag, value.toPlainString()); }
        public Builder set(FixTag tag, MessageType value) { return set(tag, value.getValue()); }
        public Builder set(FixTag tag, Side value) { return set(tag, String.valueOf(value.getValue())); }
        public Builder set(FixTag tag, OrderStatus value) { return set(tag, String.valueOf(value.getValue())); }

        public FixMessage build() {
            if (!fields.containsKey(FixTag.SENDER_ID.getKey()))
                throw new IllegalStateException("SenderID (49) est obligatoire");
            return new FixMessage(fields);
        }
    }

    public static Builder builder() { return new Builder(); }
}

