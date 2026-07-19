package com.arthurfaby.fixmerouter.routing;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {
    private static final AtomicInteger id = new AtomicInteger(100000);

    public static Integer generate() {
        return id.incrementAndGet();
    }

}
