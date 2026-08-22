package com.arthurfaby.fixmerouter;

import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmerouter.net.RouterConnectionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CommandLine.Command(
        name = "RouterApplication",
        description = "Router server for Fixme",
        mixinStandardHelpOptions = true
)
public class RouterApplication implements Callable<Integer> {
    @CommandLine.Option(names = {"-b", "--broker-port"}, description = "Broker port.", defaultValue = "5000")
    private int brokerPort;

    @CommandLine.Option(names = {"-m", "--market-port"}, description = "Market port.", defaultValue = "5001")
    private int marketPort;

    @Override
    public Integer call() {
        ExecutorService workerPool = Executors.newFixedThreadPool(8);
        Reactor reactor = new Reactor(workerPool, FixFrameDecoder::new, new RouterConnectionListener());

        try {
            int actualBrokerPort = reactor.registerServer(brokerPort, "BROKER");
            int actualMarketPort = reactor.registerServer(marketPort, "MARKET");
            logger.info("Broker port -> {}", actualBrokerPort);
            logger.info("Market port -> {}", actualMarketPort);
        } catch (IOException e) {
            logger.error("Failed to bind router ports", e);
            workerPool.shutdown();
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
        }));

        return 0;
    }

    private static final Logger logger = LogManager.getLogger(RouterApplication.class);

    static void main(String[] args) {
        logger.debug("Router is starting...");
        int exitCode = new CommandLine(new RouterApplication()).execute(args);
        if (exitCode != 0) {
            logger.debug("Router is stopping...");
            System.exit(exitCode);
        }
    }
}
