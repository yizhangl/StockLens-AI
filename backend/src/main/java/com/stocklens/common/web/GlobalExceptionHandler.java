package com.stocklens.common.web;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.InvalidTickerException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidTickerException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidTicker(
            InvalidTickerException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_TICKER", exception.getMessage(), request);
    }

    @ExceptionHandler(StockNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleStockNotFound(
            StockNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(FinancialProviderRateLimitedException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(
            FinancialProviderRateLimitedException exception, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> base = response(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "Financial data is temporarily rate limited.",
                request);
        Long retryAfterSeconds = exception.getRetryAfterSeconds();
        if (retryAfterSeconds == null) {
            return base;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(base.getHeaders());
        headers.set(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString());
        return new ResponseEntity<>(base.getBody(), headers, base.getStatusCode());
    }

    @ExceptionHandler(FinancialProviderException.class)
    ResponseEntity<ApiErrorResponse> handleFinancialProvider(
            FinancialProviderException exception, HttpServletRequest request) {
        log.warn(
                "Financial provider request failed requestId={} exceptionType={}",
                requestId(request),
                exception.getClass().getSimpleName());
        return response(
                HttpStatus.BAD_GATEWAY,
                "FINANCIAL_PROVIDER_ERROR",
                "Financial data is temporarily unavailable.",
                request);
    }

    @ExceptionHandler(DataUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleDataUnavailable(
            DataUnavailableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DATA_UNAVAILABLE",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error(
                "Unexpected request failure requestId={} exceptionType={}",
                requestId(request),
                exception.getClass().getName());
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                code,
                message,
                Instant.now(clock),
                request.getRequestURI(),
                requestId(request),
                List.of());
        return ResponseEntity.status(status).body(body);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
