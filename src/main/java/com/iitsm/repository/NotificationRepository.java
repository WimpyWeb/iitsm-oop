package com.iitsm.repository;

import com.iitsm.domain.model.Notification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for user notifications across delivery channels. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByRecipientUserCodeIgnoreCaseAndChannelOrderByCreatedAtDesc(
            String recipientUserCode, String channel);

    Page<Notification> findByRecipientUserCodeIgnoreCaseOrderByCreatedAtDesc(
            String recipientUserCode, Pageable pageable);

    long countByRecipientUserCodeIgnoreCaseAndChannelAndReadFlagFalse(
            String recipientUserCode, String channel);

    List<Notification> findByRecipientUserCodeIgnoreCaseAndChannelAndReadFlagFalse(
            String recipientUserCode, String channel);
}
