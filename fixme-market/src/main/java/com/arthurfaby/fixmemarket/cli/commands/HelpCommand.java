package com.arthurfaby.fixmemarket.cli.commands;

import com.arthurfaby.fixmemarket.cli.CLIRouter;
import com.arthurfaby.fixmemarket.cli.SplitTerminal;

public class HelpCommand implements Command {

    CLIRouter cliRouterInstance;

    public HelpCommand(CLIRouter cliRouter) {
        this.cliRouterInstance = cliRouter;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "Display help message";
    }

    @Override
    public void execute() {
        SplitTerminal.writeCommand("Availables commands:");
        this.cliRouterInstance.commandsRegistry.forEach((commandName, command) ->
                SplitTerminal.writeCommand(commandName + " -> " + command.description()));
    }
}
