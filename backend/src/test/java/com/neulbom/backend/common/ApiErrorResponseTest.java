package com.neulbom.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesRequestIdInApiContractFormat() throws Exception {
        String json = objectMapper.writeValueAsString(
                new ApiErrorResponse("잘못된 요청입니다.", 400, "detail", "req_test_001")
        );

        assertThat(json).contains("\"request_id\":\"req_test_001\"");
        assertThat(json).contains("\"code\":400");
    }
}
