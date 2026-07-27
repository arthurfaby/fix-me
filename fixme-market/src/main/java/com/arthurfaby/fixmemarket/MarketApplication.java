package com.arthurfaby.fixmemarket;

import com.arthurfaby.fixmemarket.book.InstrumentBook;

import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmemarket.cli.CLIRouter;
import com.arthurfaby.fixmemarket.cli.SplitTerminal;
import com.arthurfaby.fixmemarket.cli.commands.BookCommand;
import com.arthurfaby.fixmemarket.cli.commands.HelpCommand;
import com.arthurfaby.fixmemarket.cli.commands.IdCommand;
import com.arthurfaby.fixmemarket.net.MarketConnectionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CommandLine.Command(
        name = "MarketApplication",
        description = "Market client for Fixme",
        mixinStandardHelpOptions = true
)
public class MarketApplication implements Callable<Integer> {
    @CommandLine.Option(names = {"--host"}, description = "Router host.", defaultValue = "localhost")
    private String routerHost;


    @CommandLine.Option(names = {"-p", "--port"}, description = "Router port.", defaultValue = "5000")
    private int routerPort;

    private ConcurrentHashMap<String, Integer> instruments = new ConcurrentHashMap<>();

    @CommandLine.Option(names = "--instruments",
           description = "Instruments list.")
    public void setInstruments(String value) {
        String[] pairs = value.split(",");

        for (String pair : pairs) {
            String[] keyValue = pair.split(":");

            if (keyValue.length != 2) {
                throw new IllegalArgumentException("Invalid format : '" + pair + "'. Expected : SYMBOL:QUANTITY");
            }

            try {
                String symbol = keyValue[0].trim();
                Integer quantity = Integer.parseInt(keyValue[1].trim());
                this.instruments.put(symbol, quantity);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Quantity for '" + keyValue[0] + "' must be a valid integer.");
            }
        }
    }

    @CommandLine.Option(names = "--instruments-file",
            description = "Path to a .properties file containing instruments (ex: AAPL=1000)")
    public void setInstrumentsFile(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("The file " + filePath + " does not exist.");
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(filePath)) {
            props.load(in);

            for (String key : props.stringPropertyNames()) {
                String symbol = key.trim();
                try {
                    Integer quantity = Integer.parseInt(props.getProperty(key).trim());
                    this.instruments.put(symbol, quantity);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Quantity for '" + symbol + "' in file must be a valid integer.");
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error reading file " + filePath + " : " + e.getMessage());
        }
    }

    /** Les instruments resolus depuis --instruments / --instruments-file (visible pour les tests). */
    Map<String, Integer> instruments() {
        return instruments;
    }

    public static final Logger logger = LogManager.getLogger(MarketApplication.class);

    private final Scanner scanner = new Scanner(System.in);

    private String readInput() {
        SplitTerminal.prompt("> ");
        return scanner.nextLine();
    }

    @Override
    public Integer call() {
        if (this.instruments.isEmpty()) {
            logger.fatal("No instruments specified.");
            return 1;
        }

        SplitTerminal.init();
        logger.info("Instruments : {}", this.instruments);

        InstrumentBook book = new InstrumentBook(this.instruments);
        ExecutorService workerPool = Executors.newFixedThreadPool(4);
        MarketConnectionListener marketConnectionListener = new MarketConnectionListener(book);
        Reactor reactor = new Reactor(workerPool, FixFrameDecoder::new, marketConnectionListener);

        try {
            reactor.connect(routerHost, routerPort);
        } catch (IOException e) {
            logger.error("Failed to connect to router at {}:{}", routerHost, routerPort, e);
            workerPool.shutdown();
            SplitTerminal.restore();
            return 1;
        }

        reactor.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            try {
                reactor.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workerPool.shutdown();
            SplitTerminal.restore();
        }));

        var cli = new CLIRouter();
        cli.register(new HelpCommand(cli));
        cli.register(new IdCommand(marketConnectionListener.connection()));
        cli.register(new BookCommand(book));

        while (true) {
            var input = readInput();
            cli.handleInput(input.strip());
        }
    }

    static void main(String[] args) {
        logger.debug("MarketApplication starts");
        int exitCode = new CommandLine(new MarketApplication()).execute(args);
        System.exit(exitCode);
    }
}
