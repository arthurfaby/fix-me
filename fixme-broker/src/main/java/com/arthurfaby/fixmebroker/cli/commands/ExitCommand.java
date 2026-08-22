package com.arthurfaby.fixmebroker.cli.commands;

public final class ExitCommand implements Command {

    private final Runnable onExit;

    public ExitCommand(Runnable onExit) {
        this.onExit = onExit;
    }

    @Override
    public String name() {
        return "exit";
    }

    @Override
    public String usage() {
        return "exit";
    }

    @Override
    public String description() {
        return "Close the connection and exit";
    }

    @Override
    public void execute(String[] args) {
        onExit.run();
    }
}
