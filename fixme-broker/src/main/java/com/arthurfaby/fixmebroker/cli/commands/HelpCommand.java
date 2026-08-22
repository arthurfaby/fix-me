package com.arthurfaby.fixmebroker.cli.commands;

import com.arthurfaby.fixmebroker.cli.CLIRouter;
import com.arthurfaby.fixmebroker.cli.SplitTerminal;

public final class HelpCommand implements Command {

    private final CLIRouter cliRouter;

    public HelpCommand(CLIRouter cliRouter) {
        this.cliRouter = cliRouter;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String usage() {
        return "help";
    }

    @Override
    public String description() {
        return "Display this help message";
    }

    @Override
    public void execute(String[] args) {
        SplitTerminal.writeCommand("Available commands:");
        cliRouter.commands().forEach((commandName, command) ->
                SplitTerminal.writeCommand("  " + command.usage() + " -> " + command.description()));
    }
}
