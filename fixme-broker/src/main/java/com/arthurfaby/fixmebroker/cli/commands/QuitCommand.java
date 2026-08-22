package com.arthurfaby.fixmebroker.cli.commands;

public final class QuitCommand implements Command {

    private final Runnable onQuit;

    public QuitCommand(Runnable onQuit) {
        this.onQuit = onQuit;
    }

    @Override
    public String name() {
        return "quit";
    }

    @Override
    public String usage() {
        return "quit";
    }

    @Override
    public String description() {
        return "Close the connection and exit";
    }

    @Override
    public void execute(String[] args) {
        onQuit.run();
    }
}
