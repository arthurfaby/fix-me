package com.arthurfaby.fixmebroker.cli;

import com.arthurfaby.fixmebroker.cli.commands.IdCommand;
import com.arthurfaby.fixmebroker.cli.commands.OrderCommand;
import com.arthurfaby.fixmebroker.cli.commands.QuitCommand;
import com.arthurfaby.fixmebroker.order.OrderSender;
import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.report.RecordingReporter;
import com.arthurfaby.fixmecommon.protocol.FixParser;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD phase 7 (PLAN.md) : la CLai dispatche par nom, parse les arguments de
 * buy/sell, et une entree invalide produit un message d'usage - jamais une
 * stack trace ni un envoi. La sortie ANSI de SplitTerminal est capturee.
 */
class CLITest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    private final List<byte[]> sink = new ArrayList<>();
    private final RecordingReporter reporter = new RecordingReporter();
    private final PendingOrders pending = new PendingOrders();

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

    private OrderSender senderWithId(Integer brokerId) {
        return new OrderSender(pending, () -> brokerId, sink::add, reporter);
    }

    private CLIRouter routerWith(int brokerId) {
        CLIRouter cli = new CLIRouter();
        OrderSender sender = senderWithId(brokerId);
        cli.register(new OrderCommand("buy", Side.BUY, sender));
        cli.register(new OrderCommand("sell", Side.SELL, sender));
        return cli;
    }

    @Test
    void aValidBuyIsParsedAndSubmitted() {
        routerWith(100_003).handleInput("buy 100002 AAPL 100 150.50");

        assertThat(sink).hasSize(1);
        assertThat(FixParser.parse(sink.get(0)).getInt(FixTag.TARGET_ID)).isEqualTo(100_002);
        assertThat(FixParser.parse(sink.get(0)).getInt(FixTag.SIDE)).isEqualTo(Side.BUY.getValue());
        assertThat(reporter.last().kind()).isEqualTo("SENT");
    }

    @Test
    void aValidSellIsParsedAndSubmitted() {
        routerWith(100_003).handleInput("sell 100002 AAPL 50 151.0");

        assertThat(FixParser.parse(sink.get(0)).getInt(FixTag.SIDE)).isEqualTo(Side.SELL.getValue());
    }

    @Test
    void extraWhitespaceBetweenTokensIsTolerated() {
        routerWith(100_003).handleInput("buy   100002    AAPL 100   150.50");

        assertThat(sink).hasSize(1);
    }

    @Test
    void aBuyWithTooFewArgumentsShowsUsageAndSendsNothing() {
        routerWith(100_003).handleInput("buy 100002 AAPL 100");

        assertThat(sink).isEmpty();
        assertThat(output()).contains("usage: buy <marketId> <instrument> <qty> <price>");
    }

    @Test
    void aNonNumericQuantityShowsUsageAndSendsNothing() {
        routerWith(100_003).handleInput("buy 100002 AAPL lots 150.50");

        assertThat(sink).isEmpty();
        assertThat(output()).contains("usage: buy");
    }

    @Test
    void aNonPositiveQuantityIsRejected() {
        routerWith(100_003).handleInput("buy 100002 AAPL 0 150.50");

        assertThat(sink).isEmpty();
        assertThat(output()).contains("must be positive");
    }

    @Test
    void anOrderBeforeTheIdIsAssignedIsNotSent() {
        CLIRouter cli = new CLIRouter();
        cli.register(new OrderCommand("buy", Side.BUY, senderWithId(null)));

        cli.handleInput("buy 100002 AAPL 100 150.50");

        assertThat(sink).isEmpty();
        assertThat(output()).contains("not connected to router yet");
    }

    @Test
    void anUnknownCommandIsReported() {
        routerWith(100_003).handleInput("frobnicate now");

        assertThat(output()).contains("frobnicate: unknown command");
        assertThat(sink).isEmpty();
    }

    @Test
    void blankInputIsIgnored() {
        CLIRouter cli = routerWith(100_003);
        cli.handleInput("");
        cli.handleInput("   ");
        cli.handleInput(null);

        assertThat(output()).isEmpty();
        assertThat(sink).isEmpty();
    }

    @Test
    void idCommandShowsTheAssignedIdOrAPlaceholder() {
        AtomicInteger id = new AtomicInteger();
        CLIRouter cli = new CLIRouter();
        cli.register(new IdCommand(() -> id.get() == 0 ? null : id.get()));

        cli.handleInput("id");
        assertThat(output()).contains("not yet connected to router");

        id.set(100_003);
        cli.handleInput("id");
        assertThat(output()).contains("Broker ID: 100003");
    }

    @Test
    void quitTriggersTheShutdownAction() {
        AtomicBoolean stopped = new AtomicBoolean(false);
        CLIRouter cli = new CLIRouter();
        cli.register(new QuitCommand(() -> stopped.set(true)));

        cli.handleInput("quit");

        assertThat(stopped).isTrue();
    }
}
