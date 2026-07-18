package com.arthurfaby.fixmecommon.net;

import java.util.List;

/**
 * Decoupe un flux d'octets accumule au fil des lectures reseau en frames
 * applicatives completes. Implementation stateful : le reste non decode
 * entre deux appels doit etre conserve en interne.
 */
public interface FrameDecoder {

    List<byte[]> decode(byte[] chunk);
}
