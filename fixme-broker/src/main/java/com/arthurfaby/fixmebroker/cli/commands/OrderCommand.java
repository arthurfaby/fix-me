package com.arthurfaby.fixmebroker.cli.commands;

import com.arthurfaby.fixmebroker.cli.SplitTerminal;
import com.arthurfaby.fixmebroker.order.OrderSender;
import com.arthurfaby.fixmecommon.protocol.enums.Side;

import java.math.BigDecimal;

public final class OrderCommand implements Command {

    private final String name;
    private final Side side;
    private final OrderSender sender;

    public OrderCommand(String name, Side side, OrderSender sender) {
        this.name = name;
        this.side = side;
        this.sender = sender;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String usage() {
        return name + " <marketId> <instrument> <qty> <price>";
    }

    @Override
    public String description() {
        return "Send a " + side.name().toLowerCase() + " order to a market";
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 4) {
            SplitTerminal.writeCommand("usage: " + usage());
            return;
        }

        int marketId;
        int quantity;
        BigDecimal price;
        try {
            marketId = Integer.parseInt(args[0]);
            quantity = Integer.parseInt(args[2]);
            price = new BigDecimal(args[3]);
        } catch (NumberFormatException e) {
            SplitTerminal.writeCommand("usage: " + usage());
            return;
        }

        String instrument = args[1];
        if (quantity <= 0 || price.signum() <= 0) {
            SplitTerminal.writeCommand("quantity and price must be positive");
            return;
        }

        boolean sent = sender.submit(marketId, instrument, side, quantity, price).isPresent();
        if (!sent) {
            SplitTerminal.writeCommand("not connected to router yet, order not sent");
        }
    }
}
