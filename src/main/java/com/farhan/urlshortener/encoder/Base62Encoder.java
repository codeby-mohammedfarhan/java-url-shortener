package com.farhan.urlshortener.encoder;

import org.springframework.stereotype.Component;

/** Encodes non-negative numeric IDs using the Base62 alphabet 0-9, A-Z, a-z. */
@Component
public final class Base62Encoder {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int RADIX = ALPHABET.length;

    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62 can only encode non-negative values");
        }
        if (value == 0) {
            return "0";
        }

        StringBuilder encoded = new StringBuilder();
        while (value > 0) {
            int remainder = (int) (value % RADIX);
            encoded.append(ALPHABET[remainder]);
            value /= RADIX;
        }
        return encoded.reverse().toString();
    }
}
