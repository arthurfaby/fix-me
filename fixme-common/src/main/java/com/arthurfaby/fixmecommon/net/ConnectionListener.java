package com.arthurfaby.fixmecommon.net;

public interface ConnectionListener {

    void onConnected(Connection connection);

    void onMessage(Connection connection, byte[] frame);

    void onDisconnected(Connection connection);
}
