package com.iitsm.notification;

import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.User;
import com.iitsm.repository.NotificationRepository;
import org.springframework.stereotype.Component;

/** In-app Notifier persisting notifications directly to the database for UI bell display. */
@Component
public class InAppNotifier implements Notifier {

    public static final String CHANNEL = "IN_APP";

    private final NotificationRepository notificationRepository;

    /** Constructs InAppNotifier backed by NotificationRepository. */
    public InAppNotifier(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void send(Notification notification, User recipient) {
        notificationRepository.save(notification);
    }
}
