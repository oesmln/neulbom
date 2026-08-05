package com.neulbom.backend.common.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class UuidGenerator {

    public UUID generate() {
        return UUID.randomUUID();
    }

    public String generateString() {
        return generate().toString();
    }
}
