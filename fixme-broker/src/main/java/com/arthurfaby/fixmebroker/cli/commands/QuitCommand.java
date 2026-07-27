package com.arthurfaby.fixmebroker.cli.commands;

/**
 * Termine proprement : declenche l'action d'arret fournie par l'application
 * (stop du Reactor, shutdown de l'executor, restauration du terminal) plutot
 * qu'un System.exit() brutal.
 */
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
