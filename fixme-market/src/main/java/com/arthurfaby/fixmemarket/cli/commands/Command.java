package com.arthurfaby.fixmemarket.cli.commands;

import com.arthurfaby.fixmemarket.cli.SplitTerminal;

public interface Command {
    String name();

    String description();

    void execute();

    class ExitCommand implements Command {

        @Override
        public String name() {
            return "exit";
        }

        @Override
        public String description() {
            return "Exit the application.";
        }

        @Override
        public void execute() {
            System.exit(0);
        }
    }
}
