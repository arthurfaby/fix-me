package com.arthurfaby.fixmecommon.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PipelineTest {

    static final class RecordingContext {
        final List<String> executed = new ArrayList<>();
    }

    @Test
    void executesHandlersInMountingOrder() {
        Pipeline<RecordingContext> pipeline = Pipeline.<RecordingContext>builder()
                .addHandler(recording("A"))
                .addHandler(recording("B"))
                .addHandler(recording("C"))
                .build();

        RecordingContext ctx = new RecordingContext();
        pipeline.execute(ctx);

        assertThat(ctx.executed).containsExactly("A", "B", "C");
    }

    @Test
    void stopPreventsExecutionOfSubsequentHandlers() {
        Pipeline<RecordingContext> pipeline = Pipeline.<RecordingContext>builder()
                .addHandler(recording("A"))
                .addHandler(stopping("B"))
                .addHandler(recording("C")) // ne doit jamais s'exécuter
                .build();

        RecordingContext ctx = new RecordingContext();
        pipeline.execute(ctx);

        assertThat(ctx.executed).containsExactly("A", "B");
    }

    @Test
    void emptyPipelineDoesNotThrow() {
        Pipeline<RecordingContext> pipeline = Pipeline.<RecordingContext>builder().build();

        assertThatCode(() -> pipeline.execute(new RecordingContext()))
                .doesNotThrowAnyException();
    }

    @Test
    void anExceptionInAHandlerStopsTheChainWithoutPropagating() {
        Pipeline<RecordingContext> pipeline = Pipeline.<RecordingContext>builder()
                .addHandler(recording("A"))
                .addHandler(ctx -> { throw new RuntimeException("boom"); })
                .addHandler(recording("C")) // ne doit jamais s'exécuter
                .build();

        RecordingContext ctx = new RecordingContext();

        assertThatCode(() -> pipeline.execute(ctx)).doesNotThrowAnyException();
        assertThat(ctx.executed).containsExactly("A");
    }

    private static Handler<RecordingContext> recording(String name) {
        return ctx -> {
            ctx.executed.add(name);
            return HandlerResult.CONTINUE;
        };
    }

    private static Handler<RecordingContext> stopping(String name) {
        return ctx -> {
            ctx.executed.add(name);
            return HandlerResult.STOP;
        };
    }
}
