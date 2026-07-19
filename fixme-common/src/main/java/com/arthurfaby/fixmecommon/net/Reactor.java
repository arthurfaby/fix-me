package com.arthurfaby.fixmecommon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class Reactor {

    private static final Logger logger = LogManager.getLogger(
            Reactor.class
    );
    private static final int READ_BUFFER_SIZE = 8192;

    private final Selector selector;
    private final Executor workerPool;
    private final Supplier<FrameDecoder> decoderFactory;
    private final ConnectionListener listener;
    private final AtomicLong nextConnectionId = new AtomicLong(1);
    private final Queue<Connection> pendingWrites = new ConcurrentLinkedQueue<>();
    private final Queue<Connection> pendingCloses = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread loopThread;

    public Reactor(Executor workerPool, Supplier<FrameDecoder> decoderFactory, ConnectionListener listener) {
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.workerPool = workerPool;
        this.decoderFactory = decoderFactory;
        this.listener = listener;
    }

    public int registerServer(int port, Object tag) throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port), 1024);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT, tag);
        return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
    }

    public Connection connect(String host, int port) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));

        selector.wakeup();
        SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT);
        Connection connection = new Connection(
                nextConnectionId.getAndIncrement(), channel, key, this,
                decoderFactory.get(), workerPool, null);
        key.attach(connection);
        return connection;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        loopThread = new Thread(this::loop, "reactor");
        loopThread.start();
    }

    public void stop() throws InterruptedException {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        selector.wakeup();
        loopThread.join();
    }

    void wakeupFor(Connection connection) {
        pendingWrites.offer(connection);
        selector.wakeup();
    }

    void requestClose(Connection connection) {
        pendingCloses.offer(connection);
        selector.wakeup();
    }

    private void loop() {
        while (running.get()) {
            try {
                selector.select();
            } catch (IOException e) {
                logger.error("Selector failure, stopping reactor", e);
                break;
            }

            drainPendingCloses();
            drainPendingWrites();

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (!key.isValid()) {
                    continue;
                }
                try {
                    dispatch(key);
                } catch (IOException e) {
                    closeQuietly(key);
                }
            }
        }
        closeSelector();
    }

    private void dispatch(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            handleAccept(key);
        } else if (key.isConnectable()) {
            handleConnect(key);
        } else if (key.isReadable()) {
            handleRead(key);
        } else if (key.isWritable()) {
            handleWrite(key);
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
        Connection connection = new Connection(
                nextConnectionId.getAndIncrement(), client, clientKey, this,
                decoderFactory.get(), workerPool, key.attachment());
        clientKey.attach(connection);
        listener.onConnected(connection);
    }

    private void handleConnect(SelectionKey key) throws IOException {
        Connection connection = (Connection) key.attachment();
        boolean finished;
        try {
            finished = connection.channel().finishConnect();
        } catch (IOException e) {
            closeConnection(connection);
            return;
        }
        if (!finished) {
            return; // reveil premature, on repassera
        }
        key.interestOps(SelectionKey.OP_READ);
        listener.onConnected(connection);
    }

    private void handleRead(SelectionKey key) throws IOException {
        Connection connection = (Connection) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        int read;
        try {
            read = connection.channel().read(buffer);
        } catch (IOException e) {
            closeConnection(connection);
            return;
        }
        if (read == -1) {
            closeConnection(connection);
            return;
        }
        if (read == 0) {
            return;
        }

        buffer.flip();
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);

        List<byte[]> frames;
        try {
            frames = connection.decoder().decode(chunk);
        } catch (IllegalStateException oversized) {
            logger.warn("Frame too large on connection {}, closing", connection.id());
            closeConnection(connection);
            return;
        }

        for (byte[] frame : frames) {
            connection.executor().execute(() -> listener.onMessage(connection, frame));
        }
    }

    private void handleWrite(SelectionKey key) {
        Connection connection = (Connection) key.attachment();
        Queue<ByteBuffer> outbound = connection.outbound();
        try {
            ByteBuffer buffer;
            while ((buffer = outbound.peek()) != null) {
                connection.channel().write(buffer);
                if (buffer.hasRemaining()) {
                    return;
                }
                outbound.poll();
            }
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        } catch (IOException e) {
            closeConnection(connection);
        }
    }

    private void drainPendingWrites() {
        Connection connection;
        while ((connection = pendingWrites.poll()) != null) {
            SelectionKey key = connection.key();
            if (key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        }
    }

    private void drainPendingCloses() {
        Connection connection;
        while ((connection = pendingCloses.poll()) != null) {
            closeConnection(connection);
        }
    }

    private void closeConnection(Connection connection) {
        connection.key().cancel();
        try {
            connection.channel().close();
        } catch (IOException ignored) {
            // la connexion part de toute facon
        }
        listener.onDisconnected(connection);
    }

    private void closeQuietly(SelectionKey key) {
        Object attachment = key.attachment();
        if (attachment instanceof Connection connection) {
            closeConnection(connection);
        } else {
            logger.warn("I/O error on a server channel");
        }
    }

    private void closeSelector() {
        try {
            selector.close();
        } catch (IOException ignored) {
        }
    }
}
