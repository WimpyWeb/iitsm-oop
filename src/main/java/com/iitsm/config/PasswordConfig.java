package com.iitsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Provides the BCrypt password encoder bean shared by user management and security config. */
@Configuration
public class PasswordConfig {

    /** Returns a standard BCryptPasswordEncoder instance. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
