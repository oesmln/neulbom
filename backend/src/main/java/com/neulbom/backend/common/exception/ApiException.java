package com.neulbom.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final String detail;

    public ApiException(HttpStatus status, String error, String detail) {
        super(detail);
        this.status = status;
        this.error = error;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public String detail() {
        return detail;
    }
}
