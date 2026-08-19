package com.keyloop.scheduler.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpMetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpMetricsFilter.class);

    private final MeterRegistry meterRegistry;

    public HttpMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationNs = System.nanoTime() - start;
            String path = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
            if (path == null || "null".equals(path)) {
                path = request.getRequestURI();
            }
            String method = request.getMethod();
            String status = String.valueOf(response.getStatus());
            meterRegistry.counter("http_requests", "method", method, "path", path, "status", status).increment();
            Timer.builder("http_request_duration")
                    .tags("method", method, "path", path)
                    .register(meterRegistry)
                    .record(durationNs, TimeUnit.NANOSECONDS);

            String outcome = response.getStatus() >= 500 ? "server_error"
                    : response.getStatus() >= 400 ? "client_error" : "success";
            log.info("event=http.request method={} path={} status={} outcome={} duration_ms={}",
                    method, path, status, outcome, TimeUnit.NANOSECONDS.toMillis(durationNs));
        }
    }
}
