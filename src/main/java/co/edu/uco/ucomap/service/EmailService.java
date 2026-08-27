package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.password-reset.email-subject}")
    private String resetEmailSubject;

    public void sendPasswordResetEmail(String toEmail, String resetLink, long expiryMinutes) {
        Context ctx = new Context();
        ctx.setVariable("resetLink", resetLink);
        ctx.setVariable("expiryMinutes", expiryMinutes);
        ctx.setVariable("toEmail", toEmail);

        String htmlBody = templateEngine.process("email/password-reset", ctx);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(resetEmailSubject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Password reset email dispatched to [{}]", toEmail);
        } catch (MailException | MessagingException e) {
            // Log technical details but never expose them to the caller
            log.error("Failed to dispatch password reset email to [{}]: {}", toEmail, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.EMAIL_DELIVERY_ERROR.getMessage());
        }
    }
}
