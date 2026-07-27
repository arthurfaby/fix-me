package com.arthurfaby.fixmemarket.cli.commands;

import com.arthurfaby.fixmemarket.book.InstrumentBook;
import com.arthurfaby.fixmemarket.cli.SplitTerminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BookCommand implements Command{
    private final InstrumentBook instrumentBook;

    public BookCommand(InstrumentBook instrumentBook) {
        this.instrumentBook = instrumentBook;
    }

    @Override
    public String name() {
        return "book";
    }

    @Override
    public String description() {
        return "Display the book list";
    }

    @Override
    public void execute() {
        ConcurrentHashMap<String, Integer> quantities = instrumentBook.quantities();
        SplitTerminal.writeCommand("Book list :");
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            SplitTerminal.writeCommand(entry.getKey() + ": " + entry.getValue());
        }
    }
}
