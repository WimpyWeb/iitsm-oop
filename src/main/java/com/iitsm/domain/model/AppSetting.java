package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Runtime-configurable system setting persisted across restarts (e.g. active strategies). */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    public static final String KEY_PRIORITY_STRATEGY = "priority.strategy";
    public static final String KEY_ASSIGNMENT_STRATEGY = "assignment.strategy";
    public static final String KEY_ESCALATION_THRESHOLD = "escalation.min.priority";

    @Id
    @Column(length = 80)
    private String settingKey;

    @Column(nullable = false, length = 120)
    private String settingValue;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** Required by JPA. */
    protected AppSetting() {
    }

    /** Constructs a named application setting entry. */
    public AppSetting(String settingKey, String settingValue) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new InvalidInputException("Setting key cannot be blank.");
        }
        this.settingKey = settingKey.trim();
        setSettingValue(settingValue);
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public final void setSettingValue(String settingValue) {
        if (settingValue == null || settingValue.isBlank()) {
            throw new InvalidInputException("Setting value cannot be blank.");
        }
        this.settingValue = settingValue.trim();
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
