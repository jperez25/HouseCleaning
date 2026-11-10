package com.occ.housecleaning.exception;

import com.occ.housecleaning.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        log.warn("Request validation failed for {}: {}", request.getRequestURI(), validationErrors);
        return errorResponse(HttpStatus.BAD_REQUEST, "Request validation failed", request, validationErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ConstraintViolationException
                ? exception.getMessage()
                : "Request body is missing or malformed";
        log.warn("Invalid request for {}: {}", request.getRequestURI(), message);
        return errorResponse(HttpStatus.BAD_REQUEST, message, request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        String message = "Invalid value '" + exception.getValue() + "' for parameter '" + exception.getName() + "'";
        log.warn("Invalid parameter for {}: {}", request.getRequestURI(), message);
        return errorResponse(HttpStatus.BAD_REQUEST, message, request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        log.warn("Invalid argument for {}: {}", request.getRequestURI(), exception.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(TradeRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleTradeRejected(
            TradeRejectedException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getReason().httpStatus());
        log.info("Trade rejected at {} [{}]: {}", request.getRequestURI(), exception.getReason(), exception.getMessage());
        return errorResponse(status, exception.getMessage(), request, Map.of(), exception.getReason().name());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        if (status.is4xxClientError()) {
            log.info("Request to {} returned {}: {}", request.getRequestURI(), status.value(), message);
        } else {
            log.warn("Request to {} returned {}: {}", request.getRequestURI(), status.value(), message);
        }
        return errorResponse(status, message, request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unexpected error handling request to {}", request.getRequestURI(), exception);
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request,
                Map.of());
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {
        return errorResponse(status, message, request, validationErrors, null);
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors,
            String code) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors,
                code);
        return ResponseEntity.status(status).body(response);
    }
}
