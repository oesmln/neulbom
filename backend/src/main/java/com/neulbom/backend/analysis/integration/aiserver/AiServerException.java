package com.neulbom.backend.analysis.integration.aiserver;

import com.neulbom.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AiServerException extends ApiException {

    private final String upstreamCode;
    private final boolean retryable;

    public AiServerException(HttpStatus status, String upstreamCode, String message, boolean retryable) {
        super(status, "AI 서버 요청에 실패했습니다.", message);
        this.upstreamCode = upstreamCode;
        this.retryable = retryable;
    }

    public String upstreamCode() {
        return upstreamCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
