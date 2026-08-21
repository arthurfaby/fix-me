package com.arthurfaby.fixmerouter.routing;

import com.arthurfaby.fixmecommon.net.Connection;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    static ConcurrentHashMap<Integer, Connection> table = new ConcurrentHashMap<>();

    public static void register(Integer fixId, Connection connection) {
        table.put(fixId, connection);
    }

    public static void unregister(Integer fixId) {
        table.remove(fixId);
    }

    public static Optional<Connection> find(Integer fixId) {
        return Optional.ofNullable(table.get(fixId));
    }

    /** Nombre d'entrees vivantes. Sert a verifier l'absence de fuite (entrees mortes). */
    public static int size() {
        return table.size();
    }
}
