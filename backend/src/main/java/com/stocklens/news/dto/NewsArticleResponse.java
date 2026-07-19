package com.stocklens.news.dto;

import java.time.Instant;
import java.util.List;

public record NewsArticleResponse(
        Long id,
        String headline,
        String sourceName,
        String url,
        Instant publishedAt,
        String description,
        List<String> relatedSymbols) {}
