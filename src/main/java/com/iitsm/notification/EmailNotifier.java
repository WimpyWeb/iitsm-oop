package com.iitsm.notification;

import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.User;
import com.iitsm.exception.NotificationDeliveryException;
import com.iitsm.repository.NotificationRepository;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Adapter over JavaMailSender supporting simulated file logging and live Gmail SMTP delivery. */
@Component
public class EmailNotifier implements Notifier {

    public static final String CHANNEL = "EMAIL";
    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final String logFilePath;
    private volatile String mode;

    /** Constructs EmailNotifier configured from application.yml properties. */
    public EmailNotifier(
            NotificationRepository notificationRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.notify.email.mode:simulated}") String mode,
            @Value("${app.notify.email.from:noreply@iitsm.local}") String fromAddress,
            @Value("${app.notify.email.log-file:./data/email-notifications.log}") String logFilePath) {
        this.notificationRepository = notificationRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.mode = mode == null ? "simulated" : mode.trim().toLowerCase();
        this.fromAddress = fromAddress;
        this.logFilePath = logFilePath;
    }

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    /** Switches delivery mode (simulated or smtp) at runtime or in tests. */
    public void setMode(String mode) {
        this.mode = mode == null ? "simulated" : mode.trim().toLowerCase();
    }

    public String getMode() {
        return mode;
    }

    @Override
    public void send(Notification notification, User recipient) {
        String email = recipient != null ? recipient.getEmail() : notification.getRecipientUserCode();
        if ("smtp".equalsIgnoreCase(mode)) {
            sendViaSmtp(notification, email);
        } else {
            sendSimulated(notification, email);
        }
        notificationRepository.save(notification);
    }

    private void sendViaSmtp(Notification notification, String recipientEmail) {
        if (recipientEmail == null || !recipientEmail.contains("@")) {
            throw new NotificationDeliveryException("Recipient email address is invalid: " + recipientEmail);
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new NotificationDeliveryException("JavaMailSender bean is unavailable in smtp mode.");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(recipientEmail);
            msg.setSubject(notification.getSubject());
            msg.setText(notification.getMessage());
            sender.send(msg);
        } catch (MailException ex) {
            throw new NotificationDeliveryException("SMTP email delivery failed for " + recipientEmail, ex);
        }
    }

    private void sendSimulated(Notification notification, String recipientEmail) {
        try {
            SimulatedNotificationLogWriter.appendLine(
                    logFilePath, CHANNEL, recipientEmail, notification.getSubject(), notification.getMessage());
        } catch (IOException ex) {
            log.warn("Could not write simulated email log to {}", logFilePath, ex);
            throw new NotificationDeliveryException("Failed writing simulated email log", ex);
        }
    }
}
