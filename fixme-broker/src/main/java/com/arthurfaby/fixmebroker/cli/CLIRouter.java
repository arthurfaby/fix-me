package com.arthurfaby.fixmebroker.cli;

import com.arthurfaby.fixmebroker.cli.commands.Command;

import java.util.Arrays;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Dispatch de la CLI Broker : decoupe la ligne en {@code <commande> <args...>},
 * cherche la commande par son premier token et lui passe le reste. Une ligne
 * vide est ignoree ; une commande inconnue est signalee sans planter.
 */
public final class CLIRouter {

    private final SortedMap<String, Command> commandsRegistry = new TreeMap<>();

    public void register(Command command) {
        commandsRegistry.put(command.name(), command);
    }

    public SortedMap<String, Command> commands() {
        return commandsRegistry;
    }

    public void handleInput(String input) {
        if (input == null || input.isBlank()) {
            return;
        }

        String[] tokens = input.strip().split("\\s+");
        String name = tokens[0];
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);

        Optional.ofNullable(commandsRegistry.get(name))
                .ifPresentOrElse(
                        command -> command.execute(args),
                        () -> SplitTerminal.writeCommand(name + ": unknown command (try 'help')"));
    }
}
