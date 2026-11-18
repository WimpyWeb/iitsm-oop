package com.iitsm.domain.factory;

import com.iitsm.domain.model.Notification;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.notification.Notifier;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Factory resolving channel Notifier adapters via ConcurrentHashMap and creating Notifications. */
@Component
public class NotifierFactory {

    private final Map<String, Notifier> notifiers = new ConcurrentHashMap<>();
    private final NotificationFactory notificationFactory;

    /** Default constructor for standalone domain and unit tests. */
    public NotifierFactory() {
        this.notificationFactory = new NotificationFactory();
    }

    /** Spring constructor registering all discovered Notifier adapters by channel. */
    @Autowired
    public NotifierFactory(List<Notifier> availableNotifiers, NotificationFactory notificationFactory) {
        this.notificationFactory = notificationFactory == null ? new NotificationFactory() : notificationFactory;
        if (availableNotifiers != null) {
            for (Notifier notifier : availableNotifiers) {
                registerNotifier(notifier);
            }
        }
    }

    /** Registers or replaces a channel notifier dynamically. */
    public void registerNotifier(Notifier notifier) {
        if (notifier == null || notifier.getChannel() == null || notifier.getChannel().isBlank()) {
            throw new InvalidInputException("Notifier and channel cannot be blank.");
        }
        notifiers.put(notifier.getChannel().trim().toUpperCase(Locale.ROOT), notifier);
    }

    /** Resolves a registered notifier for the given channel name. */
    public Notifier getNotifier(String channel) {
        return findNotifier(channel)
                .orElseThrow(() -> new InvalidInputException("Unsupported notification channel: " + channel));
    }

    /** Returns an optional notifier for the given channel name. */
    public Optional<Notifier> findNotifier(String channel) {
        if (channel == null || channel.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(notifiers.get(channel.trim().toUpperCase(Locale.ROOT)));
    }

    /** Creates a Notification domain entity using the underlying NotificationFactory. */
    public Notification createNotification(String recipientUserCode, String incidentId, String channel,
                                           String subject, String message) {
        return notificationFactory.create(recipientUserCode, incidentId, channel, subject, message);
    }

    /** Returns an unmodifiable view of currently registered notification channels. */
    public Set<String> getRegisteredChannels() {
        return Collections.unmodifiableSet(notifiers.keySet());
    }
}
