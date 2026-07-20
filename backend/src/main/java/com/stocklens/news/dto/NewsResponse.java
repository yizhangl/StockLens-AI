package com.stocklens.news.dto;

import java.time.Instant;
import java.util.List;

public record NewsResponse(
        String ticker,
        int limit,
        String providerName,
        Instant retrievedAt,
        List<NewsArticleResponse> articles,
        List<NewsWarningResponse> warnings) {}
