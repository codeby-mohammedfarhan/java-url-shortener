package com.farhan.urlshortener.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Generates unique, monotonically increasing IDs within this application process. */
@Component
public class IdGenerator {

    private final AtomicLong sequence = new AtomicLong(1);

    public long nextId() {
        return sequence.getAndIncrement();
    }
}
