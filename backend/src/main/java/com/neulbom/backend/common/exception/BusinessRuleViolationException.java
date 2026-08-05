package com.neulbom.backend.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends ApiException {

    public BusinessRuleViolationException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "업무 규칙에 맞지 않는 요청입니다.", detail);
    }
}
