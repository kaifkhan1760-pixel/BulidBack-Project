package com.buildback.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods (used by OrderExpiryJob). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
