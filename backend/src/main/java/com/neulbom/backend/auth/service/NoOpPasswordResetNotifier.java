package com.neulbom.backend.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * 이메일/SMS provider를 연결하기 전까지 사용하는 전달 경계다.
 * reset token 원문은 로그나 응답에 남기지 않는다.
 */
@Component
public class NoOpPasswordResetNotifier implements PasswordResetNotifier {

    @Override
    public void send(String email, String resetToken, Instant expiresAt) {
        // 운영 환경에서는 이 구현을 이메일 또는 SMS adapter로 교체한다.
    }
}
