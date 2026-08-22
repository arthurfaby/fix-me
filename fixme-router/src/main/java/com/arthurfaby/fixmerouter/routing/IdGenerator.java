package com.arthurfaby.fixmerouter.routing;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {
    // 0 is reserved for the Router, clients start at 100001: 6 digits by construction
    private static final AtomicInteger id = new AtomicInteger(100000);

    public static Integer generate() {
        return id.incrementAndGet();
    }

}
