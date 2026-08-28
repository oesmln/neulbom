package com.neulbom.backend.common.exception;

public class EmptyTranscriptException extends RuntimeException {

    public static final String CODE = "EMPTY_TRANSCRIPT";
    public static final String USER_MESSAGE = "음성이 인식되지 않았습니다. 다시 답변해 주세요.";

    public EmptyTranscriptException() {
        super(USER_MESSAGE);
    }
}
