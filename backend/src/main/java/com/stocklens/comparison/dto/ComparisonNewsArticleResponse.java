package com.stocklens.comparison.dto;

import java.time.Instant;
import java.util.List;

public record ComparisonNewsArticleResponse(
        Long id,
        String headline,
        String sourceName,
        String url,
        Instant publishedAt,
        String description,
        List<String> relatedSymbols) {}
