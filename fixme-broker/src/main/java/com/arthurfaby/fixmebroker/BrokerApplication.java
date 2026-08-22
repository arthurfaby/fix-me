package com.arthurfaby.fixmebroker;

import com.arthurfaby.fixmebroker.cli.CLIRouter;
import com.arthurfaby.fixmebroker.cli.SplitTerminal;
import com.arthurfaby.fixmebroker.cli.commands.HelpCommand;
import com.arthurfaby.fixmebroker.cli.commands.IdCommand;
import com.arthurfaby.fixmebroker.cli.commands.OrderCommand;
import com.arthurfaby.fixmebroker.cli.commands.QuitCommand;
import com.arthurfaby.fixmebroker.net.BrokerConnectionListener;
import com.arthurfaby.fixmebroker.order.OrderSender;
import com.arthurfaby.fixmebroker.order.PendingOrders;
import com.arthurfaby.fixmebroker.report.OrderReporter;
import com.arthurfaby.fixmebroker.report.SplitTerminalReporter;
import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@CommandLine.Command(
        name = "BrokerApplication",
        description = "Broker client for Fixme",
        mixinStandardHelpOptions = true
)
public final class BrokerApplication implements Callable<Integer> {

    public static final Logger logger = LogManager.getLogger(BrokerApplication.class);

    @CommandLine.Option(names = {"--host"}, description = "Router host.", defaultValue = "localhost")
    private String routerHost;

    @CommandLine.Option(names = {"-p", "--port"}, description = "Router port.", defaultValue = "5000")
    private int routerPort;

    String host() {
        return routerHost;
    }

    int port() {
        return routerPort;
    }

    private final Scanner scanner = new Scanner(System.in);

    private String readInput() {
        SplitTerminal.prompt("> ");
        return scanner.nextLine();
    }

    @Override
    public Integer call() {
        SplitTerminal.init();

        PendingOrders pending = new PendingOrders();
        OrderReporter reporter = new SplitTerminalReporter();
        ExecutorService workerPool = Executors.newFixedThreadPool(4);
        BrokerConnectionListener listener = new BrokerConnectionListener(pending, reporter);
        Reactor reactor = new Reactor(workerPool, FixFrameDecoder::new, listener);

        Connection connection; // connect() returns the Connection right away; the id arrives via the logon
        try {
            connection = reactor.connect(routerHost, routerPort);
        } catch (IOException e) {
            logger.error("Failed to connect to router at {}:{}", routerHost, routerPort, e);
            workerPool.shutdown();
            SplitTerminal.restore();
            return 1;
        }
        reactor.start();

        AtomicBoolean running = new AtomicBoolean(true);
        Runnable shutdown = () -> {
            if (!running.compareAndSet(true, false)) {
                return;
            }
            logger.info("Shutting down...");
            try {
                reactor.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workerPool.shutdown();
            SplitTerminal.restore();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown));

        OrderSender sender = new OrderSender(pending, connection::attachment, connection::send, reporter);
        CLIRouter cli = new CLIRouter();
        cli.register(new OrderCommand("buy", Side.BUY, sender));
        cli.register(new OrderCommand("sell", Side.SELL, sender));
        cli.register(new IdCommand(connection::attachment));
        cli.register(new HelpCommand(cli));
        cli.register(new QuitCommand(shutdown));

        while (running.get()) {
            String input;
            try {
                input = readInput();
            } catch (NoSuchElementException endOfInput) { // stdin EOF (pipe, kill, Ctrl-D)
                shutdown.run();
                break;
            }
            if (!running.get()) {
                break;
            }
            cli.handleInput(input);
        }
        return 0;
    }

    static void main(String[] args) {
        logger.debug("BrokerApplication starts");
        int exitCode = new CommandLine(new BrokerApplication()).execute(args);
        System.exit(exitCode);
    }
}
