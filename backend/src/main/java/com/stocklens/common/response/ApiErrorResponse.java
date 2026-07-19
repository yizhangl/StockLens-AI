package com.stocklens.common.response;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        String requestId,
        List<ApiErrorDetail> details) {}
