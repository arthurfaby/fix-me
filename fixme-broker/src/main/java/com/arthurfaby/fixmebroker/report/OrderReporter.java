package com.arthurfaby.fixmebroker.report;

import com.arthurfaby.fixmebroker.order.Order;

/**
 * Rend visibles a l'utilisateur les etapes de vie d'un ordre. Abstraction
 * pour que le pipeline et la CLI restent testables sans terminal ANSI :
 * la prod ecrit dans la zone commande du SplitTerminal, les tests capturent.
 */
public interface OrderReporter {

    void sent(Order order);

    void executed(Order order);

    void rejected(Order order, String reason);

    /** Rapport recu pour un ClOrdID inconnu (aucun ordre en attente ne correspond). */
    void orphanReport(int clOrdId, String detail);

    /**
     * Reject de transport du Router non correle a un ordre (checksum invalide :
     * le ClOrdID d'origine n'est pas fiable, donc absent du Reject).
     */
    void transportReject(String reason);
}
