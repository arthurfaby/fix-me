package com.arthurfaby.fixmerouter;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "MainFixmeRouter",
        description = "description content",
        descriptionHeading = "heading description",
        mixinStandardHelpOptions = true
)
public class MainFixmeRouter implements Callable<Integer> {
    @CommandLine.Option(names = {"-b", "--broker-port"}, description = "Port du broker.", defaultValue = "5000")
    private int brokerPort;

    @CommandLine.Option(names = {"-m", "--market-port"}, description = "Port du marché.", defaultValue = "5001")
    private int marketPort;

    @Override
    public Integer call() {
        logger.info("Broker port -> {}", brokerPort);
        logger.info("Market port -> {}", marketPort);
        return 0;
    }

    private static final Logger logger = LogManager.getLogger(MainFixmeRouter.class);

    static void main(String[] args) {
        logger.debug("Router is starting...");
        int exitCode = new CommandLine(new MainFixmeRouter()).execute(args);
        logger.debug("Router is stopping...");
        System.exit(exitCode);
    }
}
