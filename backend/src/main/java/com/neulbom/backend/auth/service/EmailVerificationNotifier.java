package com.neulbom.backend.auth.service;

import java.time.Instant;

public interface EmailVerificationNotifier {

    void send(String email, String verificationToken, Instant expiresAt);
}
