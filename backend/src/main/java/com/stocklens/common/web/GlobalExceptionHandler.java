package com.stocklens.common.web;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.DuplicateTickersException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.InvalidComparisonModeException;
import com.stocklens.common.exception.InvalidTickerException;
import com.stocklens.common.exception.InvalidPeriodException;
import com.stocklens.common.exception.InvalidNewsLimitException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.research.ai.AiGenerationUnavailableException;
import com.stocklens.research.ai.AiProviderException;
import com.stocklens.research.ai.AiRateLimitedException;
import com.stocklens.research.ai.InvalidAiResponseException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(InvalidPeriodException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidPeriod(
            InvalidPeriodException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidComparisonModeException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidComparisonMode(
            InvalidComparisonModeException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_MODE", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateTickersException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateTickers(
            DuplicateTickersException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "DUPLICATE_TICKERS", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidNewsLimitException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidNewsLimit(
            InvalidNewsLimitException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        if ("limit".equals(exception.getName())) {
            return response(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LIMIT",
                    new InvalidNewsLimitException().getMessage(),
                    request);
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "A request parameter has an invalid value.",
                request);
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

    @ExceptionHandler(NewsProviderRateLimitedException.class)
    ResponseEntity<ApiErrorResponse> handleNewsRateLimit(
            NewsProviderRateLimitedException exception, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> base = response(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "Recent news is temporarily rate limited.",
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

    @ExceptionHandler(NewsProviderException.class)
    ResponseEntity<ApiErrorResponse> handleNewsProvider(
            NewsProviderException exception, HttpServletRequest request) {
        log.warn(
                "News provider request failed requestId={} exceptionType={} reason={} causeType={}",
                requestId(request),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception.getCause() == null
                        ? "none"
                        : exception.getCause().getClass().getSimpleName());
        return response(
                HttpStatus.BAD_GATEWAY,
                "NEWS_PROVIDER_ERROR",
                "Recent news is temporarily unavailable.",
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

    @ExceptionHandler(AiGenerationUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleAiUnavailable(
            AiGenerationUnavailableException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DATA_UNAVAILABLE",
                "AI comparison generation is not configured.", request);
    }

    @ExceptionHandler(AiRateLimitedException.class)
    ResponseEntity<ApiErrorResponse> handleAiRateLimited(
            AiRateLimitedException exception, HttpServletRequest request) {
        return response(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "AI comparison generation is temporarily rate limited.", request);
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<ApiErrorResponse> handleAiProvider(
            AiProviderException exception, HttpServletRequest request) {
        log.warn("AI comparison provider failure requestId={} exceptionType={}", requestId(request),
                exception.getCause() == null ? exception.getClass().getSimpleName()
                        : exception.getCause().getClass().getSimpleName());
        return response(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                "AI comparison generation is temporarily unavailable.", request);
    }

    @ExceptionHandler(InvalidAiResponseException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAiResponse(
            InvalidAiResponseException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_GATEWAY, "INVALID_AI_RESPONSE",
                "The AI response could not be validated.", request);
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
