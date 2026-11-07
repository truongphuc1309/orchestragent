package com.orchestragent.workspace.infrastructure.adapter.out.mail;

import com.orchestragent.workspace.application.port.InvitationNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Emails the invited person a link to the page where they accept the invitation. */
@Component
class SmtpInvitationNotifier implements InvitationNotifier {

    private final JavaMailSender mailSender;
    private final String from;
    private final String appBaseUrl;

    SmtpInvitationNotifier(
            JavaMailSender mailSender,
            @Value("${orchestragent.mail.from}") String from,
            @Value("${orchestragent.app-base-url}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    @Override
    public void invited(Notice notice) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notice.recipientEmail());
        message.setSubject(notice.inviterName() + " invited you to " + notice.workspaceName() + " on Orchestragent");
        message.setText("""
                %s invited you to join "%s" on Orchestragent as %s.

                Open this link and sign in with this email address (%s) to accept:
                %s/invitations/%s
                """.formatted(
                notice.inviterName(),
                notice.workspaceName(),
                notice.role(),
                notice.recipientEmail(),
                appBaseUrl,
                notice.invitationId()));
        mailSender.send(message);
    }
}
