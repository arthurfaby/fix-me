package com.arthurfaby.fixmecommon.net;

/**
 * Point de branchement metier d'un Reactor. Les trois methodes sont
 * appelees par le thread Selector (onConnected/onDisconnected) ou par
 * le SerialExecutor de la connexion (onMessage) - jamais concurremment
 * pour une meme connexion.
 */
public interface ConnectionListener {

    void onConnected(Connection connection);

    void onMessage(Connection connection, byte[] frame);

    void onDisconnected(Connection connection);
}
