package com.stocklens.news.client.model;

import java.time.Instant;
import java.util.List;

public record NewsFetchResult(
        List<NewsArticleData> articles,
        int skippedArticleCount,
        String providerName,
        Instant retrievedAt) {

    public NewsFetchResult {
        articles = articles == null ? List.of() : List.copyOf(articles);
        if (skippedArticleCount < 0) {
            throw new IllegalArgumentException("Skipped article count cannot be negative.");
        }
    }
}
