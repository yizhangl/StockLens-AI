package com.stocklens.research.dto;

import com.stocklens.research.context.GroundedSourceType;
import java.time.Instant;

public record GroundedSourceResponse(
        String id,
        GroundedSourceType type,
        String ticker,
        String label,
        String sourceName,
        String url,
        Instant asOf) {}
