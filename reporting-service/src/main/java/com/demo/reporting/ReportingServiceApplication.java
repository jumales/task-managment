package com.demo.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for reporting-service.
 * Projects task events into a local read model and exposes reporting REST + WebSocket endpoints.
 */
@SpringBootApplication(scanBasePackages = "com.demo")
@EnableScheduling
public class ReportingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
