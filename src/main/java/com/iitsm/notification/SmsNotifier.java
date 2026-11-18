package com.iitsm.notification;

import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.User;
import com.iitsm.exception.NotificationDeliveryException;
import com.iitsm.repository.NotificationRepository;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Simulated SMS Notifier logging outbound text alerts and recording notification history. */
@Component
public class SmsNotifier implements Notifier {

    public static final String CHANNEL = "SMS";
    private static final Logger log = LoggerFactory.getLogger(SmsNotifier.class);

    private final NotificationRepository notificationRepository;
    private final String logFilePath;

    /** Constructs SmsNotifier configured from application.yml properties. */
    public SmsNotifier(
            NotificationRepository notificationRepository,
            @Value("${app.notify.sms.log-file:./data/sms-notifications.log}") String logFilePath) {
        this.notificationRepository = notificationRepository;
        this.logFilePath = logFilePath;
    }

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void send(Notification notification, User recipient) {
        String phone = (recipient != null && recipient.getPhone() != null && !recipient.getPhone().isBlank())
                ? recipient.getPhone()
                : notification.getRecipientUserCode();
        try {
            SimulatedNotificationLogWriter.appendLine(
                    logFilePath, CHANNEL, phone, notification.getSubject(), notification.getMessage());
        } catch (IOException ex) {
            log.warn("Could not write simulated SMS log to {}", logFilePath, ex);
            throw new NotificationDeliveryException("Failed writing simulated SMS log", ex);
        }
        notificationRepository.save(notification);
    }
}
