package com.arthurfaby.fixmecommon.net;

import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReactorEchoIntegrationTest {

    private Reactor reactor;
    private ExecutorService workerPool;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (reactor != null) {
            reactor.stop();
        }
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    @Test
    void hundredClientsHundredMessagesEachAllEchoedInOrderWithoutLoss() throws Exception {
        workerPool = Executors.newFixedThreadPool(8);
        ConnectionListener echoListener = new ConnectionListener() {
            @Override
            public void onConnected(Connection connection) {
            }

            @Override
            public void onMessage(Connection connection, byte[] frame) {
                connection.send(frame);
            }

            @Override
            public void onDisconnected(Connection connection) {
            }
        };

        reactor = new Reactor(workerPool, FixFrameDecoder::new, echoListener);
        int port = reactor.registerServer(0, "TEST");
        reactor.start();

        int clientCount = 100;
        int messagesPerClient = 100;
        ExecutorService clients = Executors.newFixedThreadPool(clientCount);
        AtomicInteger failures = new AtomicInteger(0);

        for (int c = 0; c < clientCount; c++) {
            int clientId = c;
            clients.submit(() -> {
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();
                    for (int m = 0; m < messagesPerClient; m++) {
                        byte[] sent = frame("58=client" + clientId + "-msg" + m);
                        out.write(sent);
                        out.flush();
                        byte[] received = in.readNBytes(sent.length);
                        if (!Arrays.equals(sent, received)) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (IOException e) {
                    failures.incrementAndGet();
                }
            });
        }

        clients.shutdown();
        assertThat(clients.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(failures).hasValue(0);
    }

    private static byte[] frame(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            sb.append(field).append((char) FixConstants.SOH);
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.US_ASCII);
        String checksum = Checksum.compute(body, body.length);
        byte[] trailer = ("10=" + checksum).getBytes(StandardCharsets.US_ASCII);

        byte[] wire = new byte[body.length + trailer.length + 1];
        System.arraycopy(body, 0, wire, 0, body.length);
        System.arraycopy(trailer, 0, wire, body.length, trailer.length);
        wire[wire.length - 1] = FixConstants.SOH;
        return wire;
    }
}
