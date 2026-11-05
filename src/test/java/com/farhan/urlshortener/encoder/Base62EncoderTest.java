package com.farhan.urlshortener.encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encodesBase62Boundaries() {
        assertEquals("0", encoder.encode(0));
        assertEquals("z", encoder.encode(61));
        assertEquals("10", encoder.encode(62));
        assertEquals("zz", encoder.encode(3_843));
        assertEquals("100", encoder.encode(3_844));
    }

    @Test
    void rejectsNegativeIds() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(-1));
    }
}
