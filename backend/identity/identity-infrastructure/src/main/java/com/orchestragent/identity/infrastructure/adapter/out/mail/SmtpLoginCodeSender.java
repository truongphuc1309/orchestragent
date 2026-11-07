package com.orchestragent.identity.infrastructure.adapter.out.mail;

import com.orchestragent.identity.application.port.LoginCodeSender;
import com.orchestragent.identity.domain.Email;
import com.orchestragent.identity.domain.LoginCode;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends the login code as a plain-text email. The code is never logged. */
@Component
class SmtpLoginCodeSender implements LoginCodeSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpLoginCodeSender(JavaMailSender mailSender, @Value("${orchestragent.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(Email to, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.value());
        message.setSubject("Your Orchestragent login code");
        message.setText("""
                Your Orchestragent login code is: %s

                It expires in %d minutes and can be used once.
                If you did not ask for it, you can ignore this email.
                """.formatted(code, LoginCode.TTL.toMinutes()));
        mailSender.send(message);
    }
}
