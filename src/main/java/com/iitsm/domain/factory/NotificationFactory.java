package com.iitsm.domain.factory;

import com.iitsm.domain.model.Notification;
import org.springframework.stereotype.Component;

/** Factory creating channel-tagged Notification instances. */
@Component
public class NotificationFactory {

    /** Creates an in-app notification for the target userCode. */
    public Notification createInApp(String recipientUserCode, String incidentId, String subject, String message) {
        return new Notification(recipientUserCode, incidentId, "IN_APP", subject, message);
    }

    /** Creates a notification tagged with an explicit delivery channel. */
    public Notification create(String recipientUserCode, String incidentId, String channel,
                               String subject, String message) {
        return new Notification(recipientUserCode, incidentId, channel, subject, message);
    }
}
