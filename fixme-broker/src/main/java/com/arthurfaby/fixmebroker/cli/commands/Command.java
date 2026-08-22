package com.arthurfaby.fixmebroker.cli.commands;

public interface Command {

    String name();

    String usage();

    String description();

    void execute(String[] args);
}
