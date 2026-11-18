package com.iitsm.service;

import com.iitsm.domain.enums.Role;
import com.iitsm.domain.factory.NotifierFactory;
import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.User;
import com.iitsm.domain.observer.NotificationObserver;
import com.iitsm.exception.NotificationDeliveryException;
import com.iitsm.exception.UnauthorizedException;
import com.iitsm.notification.InAppNotifier;
import com.iitsm.notification.Notifier;
import com.iitsm.repository.NotificationRepository;
import com.iitsm.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service dispatching notifications via NotifierFactory with fallback and audit logging. */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotifierFactory notifierFactory;
    private final NotificationObserver notificationObserver;
    private final AuditService auditService;

    /** Constructs NotificationService and prepares channel dispatch. */
    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               NotifierFactory notifierFactory,
                               NotificationObserver notificationObserver,
                               AuditService auditService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notifierFactory = notifierFactory;
        this.notificationObserver = notificationObserver;
        this.auditService = auditService;
    }

    /** Connects NotificationObserver dispatch sink and admin userCode lookup on startup. */
    @PostConstruct
    public void initObserverWiring() {
        notificationObserver.setAdminCodeSupplier(() ->
                userRepository.findByRoleAndActiveTrue(Role.ADMIN).stream()
                        .map(User::getUserCode)
                        .toList());
        notificationObserver.setDispatchSink(this::dispatch);
    }

    /** Delivers a notification via its configured channel, falling back to IN_APP on failure. */
    public void dispatch(Notification notification) {
        if (notification == null) {
            return;
        }
        User recipient = userRepository.findByUserCodeIgnoreCase(notification.getRecipientUserCode()).orElse(null);
        try {
            Notifier notifier = notifierFactory.getNotifier(notification.getChannel());
            notifier.send(notification, recipient);
        } catch (NotificationDeliveryException ex) {
            log.warn("Notification delivery failed on channel {} for {}: {}",
                    notification.getChannel(), notification.getRecipientUserCode(), ex.getMessage());
            auditService.recordAction(
                    "SYSTEM",
                    "NOTIFICATION_DELIVERY_FAILED",
                    notification.getIncidentId(),
                    null,
                    null,
                    notification.getChannel() + " failed for " + notification.getRecipientUserCode()
                            + ": " + ex.getMessage() + "; falling back to IN_APP");
            if (!InAppNotifier.CHANNEL.equalsIgnoreCase(notification.getChannel())) {
                Notification fallback = notifierFactory.createNotification(
                        notification.getRecipientUserCode(),
                        notification.getIncidentId(),
                        InAppNotifier.CHANNEL,
                        "[Fallback Alert] " + notification.getSubject(),
                        notification.getMessage());
                notifierFactory.getNotifier(InAppNotifier.CHANNEL).send(fallback, recipient);
            }
        }
    }

    /** Returns count of unread in-app notifications for the userCode. */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userCode) {
        return notificationRepository.countByRecipientUserCodeIgnoreCaseAndChannelAndReadFlagFalse(
                userCode, InAppNotifier.CHANNEL);
    }

    /** Returns the latest 20 in-app notifications for bell dropdown rendering. */
    @Transactional(readOnly = true)
    public List<Notification> getRecentInApp(String userCode) {
        return notificationRepository.findTop20ByRecipientUserCodeIgnoreCaseAndChannelOrderByCreatedAtDesc(
                userCode, InAppNotifier.CHANNEL);
    }

    /** Returns paginated notification history across all channels for the user. */
    @Transactional(readOnly = true)
    public Page<Notification> getAllForUser(String userCode, Pageable pageable) {
        return notificationRepository.findByRecipientUserCodeIgnoreCaseOrderByCreatedAtDesc(userCode, pageable);
    }

    /** Marks a specific notification as read after checking ownership. */
    public void markAsRead(Long notificationId, String actorUserCode) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new UnauthorizedException("Notification not found."));
        if (!notification.getRecipientUserCode().equalsIgnoreCase(actorUserCode)) {
            throw new UnauthorizedException("Cannot modify another user's notification.");
        }
        notification.markRead();
        notificationRepository.save(notification);
    }

    /** Marks all unread in-app notifications for the user as read. */
    public void markAllRead(String actorUserCode) {
        List<Notification> unread = notificationRepository
                .findByRecipientUserCodeIgnoreCaseAndChannelAndReadFlagFalse(actorUserCode, InAppNotifier.CHANNEL);
        for (Notification n : unread) {
            n.markRead();
        }
        notificationRepository.saveAll(unread);
    }
}
