package com.arthurfaby.fixmecommon.net;

import com.arthurfaby.fixmecommon.protocol.enums.FixDebug;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public final class Connection {

    private final static Logger logger = LogManager.getLogger(Connection.class);

    private final long id;
    private final SocketChannel channel;
    private final SelectionKey key;
    private final Reactor reactor;
    private final FrameDecoder decoder;
    private final SerialExecutor executor;
    private final Object serverTag;
    private final Queue<ByteBuffer> outbound = new ConcurrentLinkedQueue<>();

    private volatile Integer attachment;

    Connection(long id, SocketChannel channel, SelectionKey key, Reactor reactor,
               FrameDecoder decoder, Executor sharedExecutor, Object serverTag) {
        this.id = id;
        this.channel = channel;
        this.key = key;
        this.reactor = reactor;
        this.decoder = decoder;
        this.executor = new SerialExecutor(sharedExecutor);
        this.serverTag = serverTag;
    }

    public long id() {
        return id;
    }

    public Object serverTag() {
        return serverTag;
    }

    public Integer attachment() {
        return attachment;
    }

    public void attach(Integer value) {
        this.attachment = value;
    }

    public void send(byte[] frame) {
        logger.debug("[{}] Message sent : {}", attachment, FixDebug.getReadableWire(frame));
        outbound.offer(ByteBuffer.wrap(frame));
        reactor.wakeupFor(this);
    }

    public void close() {
        logger.debug("[{}] Closing connection", attachment);
        reactor.requestClose(this);
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public SocketAddress remoteAddress() throws IOException {
        return channel.getRemoteAddress();
    }

    SocketChannel channel() {
        return channel;
    }

    SelectionKey key() {
        return key;
    }

    FrameDecoder decoder() {
        return decoder;
    }

    SerialExecutor executor() {
        return executor;
    }

    Queue<ByteBuffer> outbound() {
        return outbound;
    }
}
