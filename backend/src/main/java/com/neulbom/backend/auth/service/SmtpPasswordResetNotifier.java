package com.neulbom.backend.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.neulbom.backend.config.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Delivers one-time password reset links through the configured SMTP server. */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpPasswordResetNotifier implements PasswordResetNotifier {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")
            .withZone(KOREA);

    private final SmtpMailSender mailSender;
    private final MailProperties properties;

    public SmtpPasswordResetNotifier(SmtpMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String resetToken, Instant expiresAt) {
        String link = linkWithToken(properties.passwordResetUrl(), resetToken);
        mailSender.send(
                email,
                "늘봄 비밀번호 재설정 안내",
                "안녕하세요. 늘봄 비밀번호 재설정 요청을 받았어요.\n\n"
                        + "아래 링크를 열어 새 비밀번호를 설정해 주세요.\n"
                        + link + "\n\n"
                        + "이 링크는 " + EXPIRES_FORMAT.format(expiresAt) + "까지 유효하며 한 번만 사용할 수 있어요.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해 주세요."
        );
    }

    private String linkWithToken(String baseUrl, String token) {
        String delimiter = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + delimiter + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
