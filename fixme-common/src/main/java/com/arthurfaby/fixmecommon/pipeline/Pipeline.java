package com.arthurfaby.fixmecommon.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Pipeline<T> {
    private final List<Handler<T>> handlers;
    private static final Logger logger = LogManager.getLogger(Pipeline.class);

    Pipeline(List<Handler<T>> handlers) {
        this.handlers = handlers;
    }

    public void execute(T ctx) {
        for (var handler : handlers) {
            try {
                HandlerResult response = handler.execute(ctx);
                if (response == HandlerResult.STOP) {
                    logger.info("Handler stopped pipeline");
                    break;
                }
            } catch (Exception e) {
                logger.warn("Handler failed, stopping pipeline", e);
                break;
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
