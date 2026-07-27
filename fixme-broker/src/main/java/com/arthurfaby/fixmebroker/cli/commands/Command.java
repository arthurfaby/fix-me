package com.arthurfaby.fixmebroker.cli.commands;

/**
 * Une commande de la CLI Broker. Contrairement au Market, les commandes
 * prennent des arguments (buy/sell) : {@link #execute(String[])} recoit les
 * tokens qui suivent le nom de la commande.
 */
public interface Command {

    String name();

    String usage();

    String description();

    void execute(String[] args);
}
