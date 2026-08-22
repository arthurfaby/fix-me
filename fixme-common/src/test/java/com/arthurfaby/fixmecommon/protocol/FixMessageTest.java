package com.arthurfaby.fixmecommon.protocol;

import com.arthurfaby.fixmecommon.protocol.enums.FixTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixMessageTest {

    @Test
    void mutatingTheEntrySetThrows() {
        FixMessage message = FixMessage.builder()
                .set(FixTag.SENDER_ID, 100001)
                .set(FixTag.INSTRUMENT, "AAPL")
                .build();

        Map.Entry<Integer, String> entry = message.entries().iterator().next();

        assertThatThrownBy(() -> entry.setValue("HACKED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reusingTheBuilderAfterBuildDoesNotAffectTheAlreadyBuiltMessage() {
        FixMessage.Builder builder = FixMessage.builder()
                .set(FixTag.SENDER_ID, 100001)
                .set(FixTag.INSTRUMENT, "AAPL");

        FixMessage built = builder.build();
        builder.set(FixTag.INSTRUMENT, "GOOG");

        assertThat(built.getString(FixTag.INSTRUMENT)).isEqualTo("AAPL");
    }
}
