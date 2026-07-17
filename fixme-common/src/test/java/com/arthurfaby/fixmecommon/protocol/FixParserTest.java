package com.arthurfaby.fixmecommon.protocol;

import com.arthurfaby.fixmecommon.protocol.exception.FixParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixParserTest {

    @Test
    void rejectsAnEmptyMessage() {
        assertThatThrownBy(() -> FixParser.parse(new byte[0]))
                .isInstanceOf(FixParseException.class);
    }

    @Test
    void rejectsAFieldWithoutAnEqualsSign() {
        byte[] wire = FixExamples.wire("49000000", "56=100001", "35=A", "10=999");

        assertThatThrownBy(() -> FixParser.parse(wire))
                .isInstanceOf(FixParseException.class);
    }

    @Test
    void rejectsANonNumericTag() {
        byte[] wire = FixExamples.wire("AB=xyz", "56=100001", "35=A", "10=999");

        assertThatThrownBy(() -> FixParser.parse(wire))
                .isInstanceOf(FixParseException.class);
    }

    @Test
    void rejectsAMessageMissingTheFinalSoh() {
        byte[] withFinalSoh = FixExamples.LOGON;
        byte[] withoutFinalSoh = new byte[withFinalSoh.length - 1];
        System.arraycopy(withFinalSoh, 0, withoutFinalSoh, 0, withoutFinalSoh.length);

        assertThatThrownBy(() -> FixParser.parse(withoutFinalSoh))
                .isInstanceOf(FixParseException.class);
    }

    @Test
    void rejectsAMessageMissingTheChecksumField() {
        byte[] wire = FixExamples.wire("49=000000", "56=100001", "35=A"); // pas de 10=

        assertThatThrownBy(() -> FixParser.parse(wire))
                .isInstanceOf(FixParseException.class);
    }

    @Test
    void rejectsAMessageWhereChecksumIsNotTheLastField() {
        byte[] wire = FixExamples.wire("49=000000", "10=125", "56=100001"); // 10= au milieu

        assertThatThrownBy(() -> FixParser.parse(wire))
                .isInstanceOf(FixParseException.class);
    }
}
