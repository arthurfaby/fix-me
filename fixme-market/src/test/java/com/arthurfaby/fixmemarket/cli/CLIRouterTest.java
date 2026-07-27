package com.arthurfaby.fixmemarket.cli;

import com.arthurfaby.fixmemarket.cli.commands.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La CLI du Market (DoD phase 6) : la table de commandes dispatche, et une
 * entree invalide n'explose pas. La sortie est capturee car SplitTerminal
 * ecrit des sequences ANSI sur System.out.
 */
class CLIRouterTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

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

    @Test
    void aRegisteredCommandIsExecuted() {
        CLIRouter cli = new CLIRouter();
        AtomicInteger calls = new AtomicInteger();
        cli.register(new StubCommand("ping", calls));

        cli.handleInput("ping");

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void exitIsRegisteredOutOfTheBox() {
        assertThat(new CLIRouter().commandsRegistry).containsKey("exit");
    }

    @Test
    void anUnknownCommandIsReportedAndExecutesNothing() {
        CLIRouter cli = new CLIRouter();
        AtomicInteger calls = new AtomicInteger();
        cli.register(new StubCommand("ping", calls));

        cli.handleInput("pong");

        assertThat(calls.get()).isZero();
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("pong: Unknown command");
    }

    @Test
    void blankAndNullInputsAreIgnored() {
        CLIRouter cli = new CLIRouter();
        AtomicInteger calls = new AtomicInteger();
        cli.register(new StubCommand("ping", calls));

        assertThatCode(() -> {
            cli.handleInput(null);
            cli.handleInput("");
            cli.handleInput("   ");
        }).doesNotThrowAnyException();

        assertThat(calls.get()).isZero();
        assertThat(captured.toString(StandardCharsets.UTF_8)).doesNotContain("Unknown command");
    }

    @Test
    void registeringTwiceUnderTheSameNameReplacesTheCommand() {
        CLIRouter cli = new CLIRouter();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        cli.register(new StubCommand("ping", first));
        cli.register(new StubCommand("ping", second));

        cli.handleInput("ping");

        assertThat(first.get()).isZero();
        assertThat(second.get()).isEqualTo(1);
    }

    private record StubCommand(String name, AtomicInteger calls) implements Command {
        @Override
        public String description() {
            return "stub";
        }

        @Override
        public void execute() {
            calls.incrementAndGet();
        }
    }
}
