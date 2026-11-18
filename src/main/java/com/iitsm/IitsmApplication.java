package com.iitsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Main entry point for the I-ITSM application with scheduled SLA monitoring enabled. */
@SpringBootApplication
@EnableScheduling
public class IitsmApplication {

    /** Launches the Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(IitsmApplication.class, args);
    }
}
