package com.farhan.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdGeneratorTest {

    @Test
    void returnsIncreasingIds() {
        IdGenerator generator = new IdGenerator();

        assertEquals(1, generator.nextId());
        assertEquals(2, generator.nextId());
        assertEquals(3, generator.nextId());
    }
}
