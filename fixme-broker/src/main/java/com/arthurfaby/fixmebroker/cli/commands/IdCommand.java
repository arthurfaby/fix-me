package com.arthurfaby.fixmebroker.cli.commands;

import com.arthurfaby.fixmebroker.cli.SplitTerminal;

import java.util.function.Supplier;

public final class IdCommand implements Command {

    private final Supplier<Integer> brokerId;

    public IdCommand(Supplier<Integer> brokerId) {
        this.brokerId = brokerId;
    }

    @Override
    public String name() {
        return "id";
    }

    @Override
    public String usage() {
        return "id";
    }

    @Override
    public String description() {
        return "Display the ID of the broker";
    }

    @Override
    public void execute(String[] args) {
        Integer id = brokerId.get();
        SplitTerminal.writeCommand("Broker ID: " + (id == null ? "not yet connected to router" : id));
    }
}
