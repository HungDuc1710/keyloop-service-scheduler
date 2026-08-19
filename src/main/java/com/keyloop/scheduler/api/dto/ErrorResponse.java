package com.keyloop.scheduler.api.dto;

public record ErrorResponse(String code, String message, String requestId) {
}
