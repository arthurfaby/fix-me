package com.arthurfaby.fixmerouter.support;

import com.arthurfaby.fixmerouter.net.RouterConnectionListener;
import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RouterHarness implements AutoCloseable {

    private final ExecutorService workerPool;
    private final Reactor reactor;
    private final int brokerPort;
    private final int marketPort;

    public RouterHarness() throws IOException {
        workerPool = Executors.newFixedThreadPool(8);
        reactor = new Reactor(workerPool, FixFrameDecoder::new, new RouterConnectionListener());
        brokerPort = reactor.registerServer(0, "BROKER");
        marketPort = reactor.registerServer(0, "MARKET");
        reactor.start();
    }

    public int brokerPort() {
        return brokerPort;
    }

    public int marketPort() {
        return marketPort;
    }

    public FakeClient connectBroker() throws IOException {
        return FakeClient.connect(brokerPort);
    }

    public FakeClient connectMarket() throws IOException {
        return FakeClient.connect(marketPort);
    }

    @Override
    public void close() throws InterruptedException {
        reactor.stop();
        workerPool.shutdownNow();
    }
}
