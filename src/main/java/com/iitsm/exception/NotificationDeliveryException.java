package com.iitsm.exception;

/** Thrown when an external notification channel fails to deliver a message. */
public class NotificationDeliveryException extends ITSMException {

    /** Constructs a NotificationDeliveryException with message and underlying cause. */
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause, 502);
    }

    /** Constructs a NotificationDeliveryException with a descriptive message. */
    public NotificationDeliveryException(String message) {
        super(message, 502);
    }
}
