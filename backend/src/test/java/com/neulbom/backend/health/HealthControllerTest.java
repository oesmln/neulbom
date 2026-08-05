package com.neulbom.backend.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUpAndRequestId() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void healthEndpointPreservesIncomingRequestId() throws Exception {
        mockMvc.perform(get("/health").header("X-Request-Id", "req_test_001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req_test_001"));
    }

    @Test
    void protectedEndpointReturnsCommonUnauthorizedErrorWithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/foundation-test").header("X-Request-Id", "req_auth_001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "req_auth_001"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.request_id").value("req_auth_001"));
    }
}
