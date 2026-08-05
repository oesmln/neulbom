package com.neulbom.backend.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.api.ApiErrorResponse;
import com.neulbom.backend.common.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorResponseWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        write(response, request, 401, "인증이 필요합니다.", "유효한 access token을 확인하세요.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        write(response, request, 403, "접근 권한이 없습니다.", "역할, 연결 상태 또는 동의 범위를 확인하세요.");
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String error,
            String detail
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(error, status, detail, requestId(request))
        );
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "req_unknown" : requestId.toString();
    }
}
