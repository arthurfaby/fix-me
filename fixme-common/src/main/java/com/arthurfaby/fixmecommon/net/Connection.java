package com.arthurfaby.fixmecommon.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Une connexion TCP geree par un {@link Reactor}.
 *
 * Regle d'or : seul le thread Selector touche {@link #channel()} ou sa
 * {@link SelectionKey} (accessibles seulement dans le package, reserve au
 * Reactor). Tout autre thread ne passe que par {@link #send(byte[])} et
 * {@link #close()}, qui ne font qu'empiler une demande et reveiller le
 * Selector - jamais d'ecriture ou de fermeture directe du channel.
 */
public final class Connection {

    private final long id;
    private final SocketChannel channel;
    private final SelectionKey key;
    private final Reactor reactor;
    private final FrameDecoder decoder;
    private final SerialExecutor executor;
    private final Object serverTag;
    private final Queue<ByteBuffer> outbound = new ConcurrentLinkedQueue<>();

    private volatile Object attachment;

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

    /** Le tag passe a {@link Reactor#registerServer(int, Object)} pour le port sur lequel cette connexion est arrivee (null si sortante). */
    public Object serverTag() {
        return serverTag;
    }

    /** Attache libre pour le code metier (ex: l'ID FIX assigne apres le Logon). */
    public Object attachment() {
        return attachment;
    }

    public void attach(Object value) {
        this.attachment = value;
    }

    public void send(byte[] frame) {
        outbound.offer(ByteBuffer.wrap(frame));
        reactor.wakeupFor(this);
    }

    public void close() {
        reactor.requestClose(this);
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
