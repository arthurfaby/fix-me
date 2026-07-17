package com.arthurfaby.fixmecommon.pipeline;

import java.util.ArrayList;
import java.util.List;

public class Pipeline<T> {
    private final List<Handler<T>> handlers;
    private static final System.Logger LOGGER = System.getLogger(Pipeline.class.getName());

    Pipeline(List<Handler<T>> handlers) {
        this.handlers = handlers;
    }

    public void execute(T ctx) {
        for (var handler : handlers) {
            try {
                HandlerResult response = handler.execute(ctx);
                if (response == HandlerResult.STOP) {
                    LOGGER.log(System.Logger.Level.INFO, "Handler stopped pipeline");
                    break;
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Handler failed, stopping pipeline", e);
            }
        }
    }

    public static final class Builder<T> {
        private final List<Handler<T>> handlers = new ArrayList<>();

        public Builder<T> addHandler(Handler<T> handler) {
            this.handlers.add(handler);
            return this;
        }

        public Pipeline<T> build() {
            return new Pipeline<>(this.handlers);
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
}
