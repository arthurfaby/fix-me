package com.arthurfaby.fixmemarket.cli;

import com.arthurfaby.fixmemarket.MarketApplication;
import com.arthurfaby.fixmemarket.cli.commands.Command;

import java.util.HashMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public class CLIRouter {
    public SortedMap<String, Command> commandsRegistry = new TreeMap<>();

    public CLIRouter() {
        Command exitCommand = new Command.ExitCommand();
        this.register(exitCommand);
    }

    public void register(Command command) {
        MarketApplication.logger.debug("Command registered: {}", command.name());
        this.commandsRegistry.put(command.name(), command);
    }

    public void handleInput(String input) {
        if (input == null || input.isBlank()) return;

        Optional.ofNullable(commandsRegistry.get(input))
                .ifPresentOrElse(
                        cmd -> {
                            cmd.execute();
                            MarketApplication.logger.debug("Command executed: {}", cmd.name());
                        },
                        () -> {
                            MarketApplication.logger.debug("Can't find command '{}' in registry.", input);
                            SplitTerminal.writeCommand(input + ": Unknown command");
                        }
                );
    }
}
