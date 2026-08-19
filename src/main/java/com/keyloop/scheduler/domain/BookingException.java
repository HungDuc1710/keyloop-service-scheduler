package com.keyloop.scheduler.domain;

public class BookingException extends RuntimeException {

    private final ErrorCode code;

    public BookingException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
