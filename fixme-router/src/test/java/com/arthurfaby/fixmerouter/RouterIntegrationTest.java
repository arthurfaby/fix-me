package com.arthurfaby.fixmerouter;

import com.arthurfaby.fixmerouter.net.RouterConnectionListener;
import com.arthurfaby.fixmerouter.routing.RoutingTable;
import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.net.Reactor;
import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixConstants;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouterIntegrationTest {

    private ExecutorService workerPool;
    private Reactor reactor;
    private int brokerPort;
    private int marketPort;

    @BeforeEach
    void startRouter() throws IOException {
        workerPool = Executors.newFixedThreadPool(4);
        reactor = new Reactor(workerPool, FixFrameDecoder::new, new RouterConnectionListener());
        brokerPort = reactor.registerServer(0, "BROKER");
        marketPort = reactor.registerServer(0, "MARKET");
        reactor.start();
    }

    @AfterEach
    void stopRouter() throws InterruptedException {
        reactor.stop();
        workerPool.shutdown();
    }

    @Test
    void brokerAndMarketReceiveDistinctSixDigitIds() throws IOException {
        try (FakeClient broker = FakeClient.connect(brokerPort);
             FakeClient market = FakeClient.connect(marketPort)) {

            int brokerId = broker.readLogonId();
            int marketId = market.readLogonId();

            assertThat(brokerId).isBetween(100000, 999999);
            assertThat(marketId).isBetween(100000, 999999);
            assertThat(brokerId).isNotEqualTo(marketId);
        }
    }

    @Test
    void validBuyIsForwardedIntactToTarget() throws IOException {
        try (FakeClient broker = FakeClient.connect(brokerPort);
             FakeClient market = FakeClient.connect(marketPort)) {

            broker.readLogonId();
            int marketId = market.readLogonId();

            byte[] buy = buyOrder(marketId);
            broker.send(buy);

            assertThat(market.readFrame()).isEqualTo(buy);
        }
    }

    @Test
    void invalidChecksumIsRejectedWithoutForwarding() throws IOException {
        try (FakeClient broker = FakeClient.connect(brokerPort);
             FakeClient market = FakeClient.connect(marketPort)) {

            broker.readLogonId();
            int marketId = market.readLogonId();

            byte[] buy = buyOrder(marketId);
            buy[buy.length - 2] = (byte) (buy[buy.length - 2] == '9' ? '8' : '9');
            broker.send(buy);

            String reject = new String(broker.readFrame(), StandardCharsets.US_ASCII);
            assertThat(reject).contains("35=3").contains("58=Invalid checksum");

            market.setReadTimeout(300);
            assertThatThrownBy(market::readFrame)
                    .isInstanceOfAny(SocketTimeoutException.class, EOFException.class);
        }
    }

    @Test
    void unknownTargetIsRejected() throws IOException {
        try (FakeClient broker = FakeClient.connect(brokerPort)) {
            broker.readLogonId();

            broker.send(buyOrder(999_999));

            String reject = new String(broker.readFrame(), StandardCharsets.US_ASCII);
            assertThat(reject).contains("35=3").contains("58=Unknown target").contains("11=1");
        }
    }

    @Test
    void disconnectedTargetIsRejectedAndRouterStaysAlive() throws Exception {
        int marketId;
        try (FakeClient market = FakeClient.connect(marketPort)) {
            marketId = market.readLogonId();
        }

        long deadline = System.currentTimeMillis() + 2000;
        while (RoutingTable.find(marketId).isPresent() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(RoutingTable.find(marketId)).isEmpty();

        try (FakeClient broker = FakeClient.connect(brokerPort)) {
            broker.readLogonId();
            broker.send(buyOrder(marketId));

            String reject = new String(broker.readFrame(), StandardCharsets.US_ASCII);
            assertThat(reject).contains("58=Unknown target");
        }

        try (FakeClient freshMarket = FakeClient.connect(marketPort)) {
            assertThat(freshMarket.readLogonId()).isNotEqualTo(marketId);
        }
    }

    private static byte[] buyOrder(int targetId) {
        return wire("49=999999", "56=" + targetId, "35=D", "11=1",
                "55=AAPL", "54=1", "38=100", "44=150.50");
    }

    private static byte[] wire(String... fields) {
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

    private static final class FakeClient implements AutoCloseable {
        private final Socket socket;
        private final FixFrameDecoder decoder = new FixFrameDecoder();
        private final Deque<byte[]> pending = new ArrayDeque<>();

        private FakeClient(Socket socket) {
            this.socket = socket;
        }

        static FakeClient connect(int port) throws IOException {
            Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2000);
            return new FakeClient(socket);
        }

        void setReadTimeout(int millis) throws IOException {
            socket.setSoTimeout(millis);
        }

        void send(byte[] frame) throws IOException {
            socket.getOutputStream().write(frame);
            socket.getOutputStream().flush();
        }

        byte[] readFrame() throws IOException {
            if (!pending.isEmpty()) {
                return pending.poll();
            }
            byte[] buf = new byte[4096];
            while (true) {
                int n = socket.getInputStream().read(buf);
                if (n == -1) {
                    throw new EOFException("Connection closed before a full frame arrived");
                }
                List<byte[]> frames = decoder.decode(Arrays.copyOf(buf, n));
                if (!frames.isEmpty()) {
                    pending.addAll(frames);
                    return pending.poll();
                }
            }
        }

        int readLogonId() throws IOException {
            String text = new String(readFrame(), StandardCharsets.US_ASCII);
            String prefix = FixTag.TARGET_ID.getKey() + "=";
            for (String field : text.split(String.valueOf((char) FixConstants.SOH))) {
                if (field.startsWith(prefix)) {
                    return Integer.parseInt(field.substring(prefix.length()));
                }
            }
            throw new IllegalStateException("No TargetID in logon: " + text);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
