package com.neulbom.backend.auth.service;

import java.time.Instant;

public interface PasswordResetNotifier {

    void send(String email, String resetToken, Instant expiresAt);
}
