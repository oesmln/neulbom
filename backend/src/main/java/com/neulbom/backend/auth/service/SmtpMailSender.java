package com.neulbom.backend.auth.service;

import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends plain-text mail without exposing recipient addresses or tokens. */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.from();
    }

    public void send(String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from.trim());
        }
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // Do not include the recipient or token in logs or the API detail.
            log.warn("Mail delivery failed subject={}", subject);
            throw new ExternalServiceUnavailableException("이메일을 발송할 수 없습니다. SMTP 설정을 확인해 주세요.");
        }
    }
}
