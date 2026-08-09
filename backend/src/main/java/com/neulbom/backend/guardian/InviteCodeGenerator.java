package com.neulbom.backend.guardian;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
