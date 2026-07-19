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
}
