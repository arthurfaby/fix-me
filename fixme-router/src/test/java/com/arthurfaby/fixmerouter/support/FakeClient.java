package com.arthurfaby.fixmerouter.support;

import com.arthurfaby.fixmecommon.net.FixFrameDecoder;
import com.arthurfaby.fixmecommon.protocol.Checksum;
import com.arthurfaby.fixmecommon.protocol.FixConstants;
import com.arthurfaby.fixmecommon.protocol.enums.FixTag;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public final class FakeClient implements AutoCloseable {

    private final Socket socket;
    private final FixFrameDecoder decoder = new FixFrameDecoder();
    private final Deque<byte[]> pending = new ArrayDeque<>();

    private FakeClient(Socket socket) {
        this.socket = socket;
    }

    public static FakeClient connect(int port) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(2000);
        socket.setTcpNoDelay(true);
        return new FakeClient(socket);
    }

    public void setReadTimeout(int millis) throws IOException {
        socket.setSoTimeout(millis);
    }

    public void send(byte[] frame) throws IOException {
        socket.getOutputStream().write(frame);
        socket.getOutputStream().flush();
    }

    public void sendBytesOneByOne(byte[] frame, long millisBetween) throws IOException {
        for (byte b : frame) {
            socket.getOutputStream().write(new byte[]{b});
            socket.getOutputStream().flush();
            if (millisBetween > 0) {
                try {
                    Thread.sleep(millisBetween);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void closeAbruptly() throws IOException {
        socket.setSoLinger(true, 0);
        socket.close();
    }

    public byte[] readFrame() throws IOException {
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

    public String readFrameAsString() throws IOException {
        return new String(readFrame(), StandardCharsets.US_ASCII);
    }

    public int readLogonId() throws IOException {
        String text = readFrameAsString();
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

    public static byte[] wire(String... fields) {
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

    public static byte[] buyOrder(int senderId, int targetId, int clOrdId, String instrument, int qty, String price) {
        return wire("49=" + senderId, "56=" + targetId, "35=D", "11=" + clOrdId,
                "55=" + instrument, "54=1", "38=" + qty, "44=" + price);
    }
}
