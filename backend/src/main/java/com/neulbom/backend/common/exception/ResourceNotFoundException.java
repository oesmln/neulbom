package com.neulbom.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", detail);
    }
}
