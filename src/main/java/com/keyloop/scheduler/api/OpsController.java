package com.keyloop.scheduler.api;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OpsController {

    private final JdbcTemplate jdbcTemplate;
    private final PrometheusMeterRegistry prometheus;

    public OpsController(JdbcTemplate jdbcTemplate, PrometheusMeterRegistry prometheus) {
        this.jdbcTemplate = jdbcTemplate;
        this.prometheus = prometheus;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public Map<String, String> ready() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "READY");
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        return prometheus.scrape();
    }
}
