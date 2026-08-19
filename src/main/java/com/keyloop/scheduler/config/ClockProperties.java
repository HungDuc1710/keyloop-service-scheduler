package com.keyloop.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduler.clock")
public class ClockProperties {
    /**
     * ISO-8601 instant. When set, the app uses a fixed clock (tests / demos).
     */
    private String fixedInstant = "";

    public String getFixedInstant() {
        return fixedInstant;
    }

    public void setFixedInstant(String fixedInstant) {
        this.fixedInstant = fixedInstant;
    }
}
