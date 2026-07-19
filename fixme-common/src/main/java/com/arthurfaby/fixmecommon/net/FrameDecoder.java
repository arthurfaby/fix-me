package com.arthurfaby.fixmecommon.net;

import java.util.List;

public interface FrameDecoder {

    List<byte[]> decode(byte[] chunk);
}
