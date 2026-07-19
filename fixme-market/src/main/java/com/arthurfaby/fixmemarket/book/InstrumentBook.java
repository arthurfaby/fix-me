package com.arthurfaby.fixmemarket.book;

import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class InstrumentBook {

    public enum Result { EXECUTED, UNKNOWN_INSTRUMENT, INSUFFICIENT_QUANTITY }

    private final ConcurrentHashMap<String, Integer> quantities;

    public InstrumentBook(Map<String, Integer> initial) {
        this.quantities = new ConcurrentHashMap<>(initial);
    }

    public static InstrumentBook of(Instrument... instruments) {
        Map<String, Integer> initial = new ConcurrentHashMap<>();
        for (Instrument instrument : instruments) {
            initial.put(instrument.symbol(), instrument.quantity());
        }
        return new InstrumentBook(initial);
    }

    public boolean isKnown(String symbol) {
        return quantities.containsKey(symbol);
    }

    public int quantityOf(String symbol) {
        return quantities.getOrDefault(symbol, 0);
    }

    public Result tryExecute(String symbol, Side side, int quantity) {
        AtomicReference<Result> outcome = new AtomicReference<>(Result.EXECUTED);
        quantities.compute(symbol, (s, current) -> {
            if (current == null) {
                outcome.set(Result.UNKNOWN_INSTRUMENT);
                return null;
            }
            if (side == Side.SELL) {
                outcome.set(Result.EXECUTED);
                return current + quantity;
            }
            if (current < quantity) {
                outcome.set(Result.INSUFFICIENT_QUANTITY);
                return current;
            }
            outcome.set(Result.EXECUTED);
            return current - quantity;
        });
        return outcome.get();
    }
}
