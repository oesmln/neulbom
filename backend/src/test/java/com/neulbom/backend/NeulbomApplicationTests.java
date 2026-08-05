package com.neulbom.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NeulbomApplicationTests {

    @Autowired
    private Clock serverClock;

    @Test
    void applicationContextLoadsWithUtcServerClock() {
        assertThat(serverClock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
