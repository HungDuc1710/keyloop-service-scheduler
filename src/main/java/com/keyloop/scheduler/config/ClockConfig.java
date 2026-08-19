package com.keyloop.scheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Configuration
@EnableConfigurationProperties(ClockProperties.class)
public class ClockConfig {

    @Bean
    Clock clock(ClockProperties properties) {
        String fixed = properties.getFixedInstant();
        if (fixed != null && !fixed.isBlank()) {
            return Clock.fixed(Instant.parse(fixed), ZoneOffset.UTC);
        }
        return Clock.systemUTC();
    }
}
