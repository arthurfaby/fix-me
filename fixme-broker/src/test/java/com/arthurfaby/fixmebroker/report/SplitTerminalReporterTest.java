package com.arthurfaby.fixmebroker.report;

import com.arthurfaby.fixmebroker.order.Order;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie le format lisible attendu par le sujet ([SENT]/[EXECUTED]/[REJECTED]),
 * en capturant ce que le reporter ecrit sur le terminal.
 */
class SplitTerminalReporterTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;
    private final SplitTerminalReporter reporter = new SplitTerminalReporter();

    private static final Order BUY = new Order(1, 100_002, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sentLineShowsTheOrderAndTargetMarket() {
        reporter.sent(BUY);
        assertThat(output()).contains("[SENT]").contains("#1 Buy 100 AAPL @ 150.50").contains("-> market 100002");
    }

    @Test
    void executedLineShowsTheOrder() {
        reporter.executed(BUY);
        assertThat(output()).contains("[EXECUTED]").contains("#1 Buy 100 AAPL @ 150.50");
    }

    @Test
    void rejectedLineShowsTheReason() {
        reporter.rejected(BUY, "Not enough quantity");
        assertThat(output()).contains("[REJECTED]").contains("#1").contains("Not enough quantity");
    }

    @Test
    void orphanReportIsLabelled() {
        reporter.orphanReport(99, "executed");
        assertThat(output()).contains("[REPORT]").contains("#99").contains("no matching order");
    }
}
