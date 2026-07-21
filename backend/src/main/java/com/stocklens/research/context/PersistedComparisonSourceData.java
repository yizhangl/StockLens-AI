package com.stocklens.research.context;

import com.stocklens.company.domain.Company;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.news.domain.NewsArticle;
import java.util.List;

public record PersistedComparisonSourceData(
        Company leftCompany,
        Company rightCompany,
        MarketSnapshot leftMarket,
        MarketSnapshot rightMarket,
        FinancialMetricSnapshot leftMetrics,
        FinancialMetricSnapshot rightMetrics,
        List<HistoricalPrice> leftHistory,
        List<HistoricalPrice> rightHistory,
        List<NewsArticle> leftNews,
        List<NewsArticle> rightNews) {}
