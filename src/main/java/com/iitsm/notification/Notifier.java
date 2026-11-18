package com.iitsm.notification;

import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.User;

/** Channel delivery contract implemented by InApp, Email, and SMS adapters. */
public interface Notifier {

    /** Returns the channel identifier handled by this adapter (IN_APP, EMAIL, SMS). */
    String getChannel();

    /** Delivers a notification to the specified recipient through this channel. */
    void send(Notification notification, User recipient);
}
