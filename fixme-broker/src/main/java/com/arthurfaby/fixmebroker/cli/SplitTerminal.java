package com.arthurfaby.fixmebroker.cli;

/**
 * Terminal coupe en deux zones : les logs (asynchrones, thread reactor) en
 * haut, l'invite de commande en bas, separees par une ligne. Evite que les
 * rapports d'execution qui remontent du reseau ne viennent ecraser la ligne
 * que l'utilisateur est en train de taper.
 */
public final class SplitTerminal {

    private static final int LOG_ZONE_TOP = 1;
    private static final int LOG_ZONE_BOTTOM = 10;
    private static final int SEPARATOR_ROW = 11;
    private static final int COMMAND_ZONE_TOP = 12;
    private static final int COMMAND_ZONE_BOTTOM = 26;
    private static final int WIDTH = 80;

    private static final Object LOCK = new Object();

    private SplitTerminal() {
    }

    public static void init() {
        synchronized (LOCK) {
            System.out.print("\033[2J\033[H");
            System.out.print("\033[" + SEPARATOR_ROW + ";1H" + "-".repeat(WIDTH));
            System.out.print("\033[" + COMMAND_ZONE_TOP + ";" + COMMAND_ZONE_BOTTOM + "r");
            System.out.print("\033[" + COMMAND_ZONE_TOP + ";1H");
            System.out.flush();
        }
    }

    public static void restore() {
        synchronized (LOCK) {
            System.out.print("\033[r");
            System.out.print("\033[" + (COMMAND_ZONE_BOTTOM + 1) + ";1H\n");
            System.out.flush();
        }
    }

    public static void writeLog(String line) {
        synchronized (LOCK) {
            System.out.print("\0337");
            System.out.print("\033[" + LOG_ZONE_TOP + ";" + LOG_ZONE_BOTTOM + "r");
            System.out.print("\033[" + LOG_ZONE_BOTTOM + ";1H");
            System.out.print(line + "\n");
            System.out.print("\033[" + COMMAND_ZONE_TOP + ";" + COMMAND_ZONE_BOTTOM + "r");
            System.out.print("\0338");
            System.out.flush();
        }
    }

    public static void writeCommand(String line) {
        synchronized (LOCK) {
            System.out.print("\033[" + COMMAND_ZONE_TOP + ";" + COMMAND_ZONE_BOTTOM + "r");
            System.out.print("\033[" + COMMAND_ZONE_BOTTOM + ";1H");
            System.out.print(line + "\n");
            System.out.flush();
        }
    }

    public static void prompt(String text) {
        synchronized (LOCK) {
            System.out.print("\033[" + COMMAND_ZONE_TOP + ";" + COMMAND_ZONE_BOTTOM + "r");
            System.out.print("\033[" + COMMAND_ZONE_BOTTOM + ";1H");
            System.out.print(text);
            System.out.flush();
        }
    }
}
