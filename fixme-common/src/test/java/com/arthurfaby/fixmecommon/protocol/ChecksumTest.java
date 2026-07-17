package com.arthurfaby.fixmecommon.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumTest {

    @Test
    void computesAKnownValueCalculatedByHand() {
        // "1=A" + SOH : '1'(49) + '='(61) + 'A'(65) + SOH(1) = 176
        byte[] data = concat("1=A".getBytes(StandardCharsets.US_ASCII), new byte[]{FixConstants.SOH});

        assertThat(Checksum.compute(data, data.length)).isEqualTo("176");
    }

    @Test
    void zeroPadsToExactlyThreeDigits() {
        assertThat(Checksum.compute(new byte[]{7}, 1)).isEqualTo("007");
        assertThat(Checksum.compute(new byte[]{}, 0)).isEqualTo("000");
        assertThat(Checksum.compute(new byte[]{(byte) 255}, 1)).isEqualTo("255");
    }

    @Test
    void neverIncludesTheChecksumFieldItselfInTheSum() {
        // Peu importe ce qui est écrit dans le trailer "10=xxx", compute() ne doit
        // lire que les `length` premiers octets. Le champ checksum n'est jamais
        // pris en compte dans son propre calcul.
        byte[] body = "49=100001".getBytes(StandardCharsets.US_ASCII);
        byte[] withTrailerA = concat(body, "10=999".getBytes(StandardCharsets.US_ASCII));
        byte[] withTrailerB = concat(body, "10=000".getBytes(StandardCharsets.US_ASCII));

        assertThat(Checksum.compute(withTrailerA, body.length))
                .isEqualTo(Checksum.compute(withTrailerB, body.length));
    }

    @Test
    void verifiesTheFourCanonicalMessages() {
        assertThat(Checksum.verify(FixExamples.BUY)).isTrue();
        assertThat(Checksum.verify(FixExamples.EXECUTED)).isTrue();
        assertThat(Checksum.verify(FixExamples.REJECTED)).isTrue();
        assertThat(Checksum.verify(FixExamples.LOGON)).isTrue();
    }

    @Test
    void rejectsATamperedChecksumDigit() {
        byte[] tampered = FixExamples.LOGON.clone();
        tampered[tampered.length - 2] = (byte) (tampered[tampered.length - 2] == '5' ? '6' : '5');
        assertThat(Checksum.verify(tampered)).isFalse(); 
    }

    @Test
    void rejectsAMessageShorterThanTheChecksumTrailer() {
        assertThat(Checksum.verify(new byte[]{'1', '0'})).isFalse();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
