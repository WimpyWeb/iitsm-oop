package com.iitsm.repository;

import com.iitsm.domain.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for runtime-configurable strategy and threshold settings. */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
