package com.arthurfaby.fixmecommon.protocol;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FixRoundTripTest {

    static Stream<byte[]> canonicalMessages() {
        return Stream.of(FixExamples.BUY, FixExamples.EXECUTED, FixExamples.REJECTED, FixExamples.LOGON);
    }

    @ParameterizedTest
    @MethodSource("canonicalMessages")
    void serializeOfParseReturnsTheOriginalBytes(byte[] original) {
        FixMessage parsed = FixParser.parse(original);
        byte[] reserialized = FixSerializer.serialize(parsed);

        assertThat(reserialized).isEqualTo(original);
    }
}
