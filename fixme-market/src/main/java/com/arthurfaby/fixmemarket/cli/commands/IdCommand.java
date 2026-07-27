package com.arthurfaby.fixmemarket.cli.commands;

import com.arthurfaby.fixmecommon.net.Connection;
import com.arthurfaby.fixmemarket.cli.SplitTerminal;

public class IdCommand implements Command {

    private final Connection connection;

    public IdCommand(Connection connection) {
        this.connection = connection;
    }

    @Override
    public String name() {
        return "id";
    }

    @Override
    public String description() {
        return "Display the ID of the market";
    }

    @Override
    public void execute() {
        String idText = connection.attachment() == null ?
                "not yet connected to router" :
                connection.attachment().toString();
        SplitTerminal.writeCommand("Market ID: " + idText);
    }
}
