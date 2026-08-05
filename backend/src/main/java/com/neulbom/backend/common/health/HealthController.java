package com.neulbom.backend.common.health;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final Clock serverClock;

    public HealthController(Clock serverClock) {
        this.serverClock = serverClock;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now(serverClock));
    }

    public record HealthResponse(String status, Instant timestamp) {
    }
}
