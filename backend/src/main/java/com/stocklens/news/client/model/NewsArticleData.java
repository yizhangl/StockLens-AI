package com.stocklens.news.client.model;

import java.time.Instant;
import java.util.Set;

public record NewsArticleData(
        String externalId,
        String headline,
        String sourceName,
        String articleUrl,
        String description,
        Instant publishedAt,
        Set<String> relatedSymbols,
        Instant retrievedAt,
        String providerName) {

    public NewsArticleData {
        relatedSymbols = relatedSymbols == null ? Set.of() : Set.copyOf(relatedSymbols);
    }
}
