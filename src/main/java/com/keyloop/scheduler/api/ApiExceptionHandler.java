package com.keyloop.scheduler.api;

import com.keyloop.scheduler.api.dto.ErrorResponse;
import com.keyloop.scheduler.domain.BookingException;
import com.keyloop.scheduler.domain.ErrorCode;
import com.keyloop.scheduler.observability.RequestIdFilter;
import com.keyloop.scheduler.observability.SchedulerMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final SchedulerMetrics metrics;

    public ApiExceptionHandler(SchedulerMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> booking(BookingException ex) {
        return ResponseEntity.status(ex.code().status())
                .body(new ErrorResponse(ex.code().name(), ex.getMessage(), RequestIdFilter.current()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception ex) {
        metrics.bookingRejected(ErrorCode.VALIDATION_ERROR.name());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(new ErrorResponse(
                        ErrorCode.VALIDATION_ERROR.name(),
                        "Request body is invalid. Send ISO-8601 timestamps with an offset (e.g. 2026-08-21T08:00:00Z).",
                        RequestIdFilter.current()
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException ex) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return ResponseEntity.status(ErrorCode.MISSING_IDEMPOTENCY_KEY.status())
                    .body(new ErrorResponse(
                            ErrorCode.MISSING_IDEMPOTENCY_KEY.name(),
                            "Idempotency-Key header is required",
                            RequestIdFilter.current()
                    ));
        }
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), ex.getMessage(), RequestIdFilter.current()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("event=booking.failed path={}", request.getRequestURI(), ex);
        if (request.getRequestURI() != null && request.getRequestURI().startsWith("/appointments")
                && "POST".equals(request.getMethod())) {
            metrics.bookingFailed();
        }
        metrics.dbError("unexpected");
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Unexpected error", RequestIdFilter.current()));
    }
}
