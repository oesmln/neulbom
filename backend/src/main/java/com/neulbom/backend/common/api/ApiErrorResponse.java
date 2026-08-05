package com.neulbom.backend.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiErrorResponse(
        String error,
        int code,
        String detail,
        @JsonProperty("request_id") String requestId
) {
}
