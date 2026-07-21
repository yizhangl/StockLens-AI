package com.stocklens.research.context;

import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.news.domain.NewsArticle;
import java.time.Instant;

public record GroundedSource(
        String id,
        GroundedSourceType type,
        String ticker,
        String label,
        String sourceName,
        String url,
        Instant asOf,
        Long companyId,
        MarketSnapshot marketSnapshot,
        FinancialMetricSnapshot financialSnapshot,
        NewsArticle newsArticle) {}
