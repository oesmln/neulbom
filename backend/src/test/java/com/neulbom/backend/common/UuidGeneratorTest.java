package com.neulbom.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.neulbom.backend.common.id.UuidGenerator;
import org.junit.jupiter.api.Test;

class UuidGeneratorTest {

    private final UuidGenerator uuidGenerator = new UuidGenerator();

    @Test
    void generatesUuidAndStringRepresentations() {
        UUID uuid = uuidGenerator.generate();
        String value = uuidGenerator.generateString();

        assertThat(uuid).isNotNull();
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
