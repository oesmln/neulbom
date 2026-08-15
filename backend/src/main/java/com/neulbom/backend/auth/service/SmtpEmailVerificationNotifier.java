package com.neulbom.backend.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.neulbom.backend.config.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Delivers one-time email verification links through the configured SMTP server. */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailVerificationNotifier implements EmailVerificationNotifier {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")
            .withZone(KOREA);

    private final SmtpMailSender mailSender;
    private final MailProperties properties;

    public SmtpEmailVerificationNotifier(SmtpMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String verificationToken, Instant expiresAt) {
        String link = linkWithToken(properties.emailVerificationUrl(), verificationToken);
        mailSender.send(
                email,
                "늘봄 이메일 인증 안내",
                "안녕하세요. 늘봄 가입을 완료하려면 아래 링크를 열어 이메일을 인증해 주세요.\n\n"
                        + link + "\n\n"
                        + "이 링크는 " + EXPIRES_FORMAT.format(expiresAt) + "까지 유효해요."
        );
    }

    private String linkWithToken(String baseUrl, String token) {
        String delimiter = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + delimiter + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
