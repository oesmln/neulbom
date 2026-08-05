package com.neulbom.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceUnavailableException extends ApiException {

    public ExternalServiceUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스가 일시적으로 unavailable합니다.", detail);
    }
}
