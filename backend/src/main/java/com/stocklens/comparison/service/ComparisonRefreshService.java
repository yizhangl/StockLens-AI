package com.stocklens.comparison.service;

import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.exception.DuplicateTickersException;
import com.stocklens.common.exception.InvalidTickerException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.service.StockQueryService;
import com.stocklens.comparison.dto.ComparisonRefreshResponse;
import com.stocklens.financial.service.FinancialMetricsQueryService;
import com.stocklens.news.service.NewsQueryService;
import com.stocklens.research.service.ComparisonResearchService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ComparisonRefreshService {
    private final TickerNormalizer normalizer;
    private final StockQueryService stock;
    private final FinancialMetricsQueryService metrics;
    private final NewsQueryService news;
    private final ComparisonResearchService research;
    private final JsonRedisCache cache;
    private final StockLensCacheKeys keys;
    public ComparisonRefreshService(TickerNormalizer normalizer, StockQueryService stock, FinancialMetricsQueryService metrics,
            NewsQueryService news, ComparisonResearchService research, JsonRedisCache cache, StockLensCacheKeys keys) {
        this.normalizer = normalizer; this.stock = stock; this.metrics = metrics; this.news = news;
        this.research = research; this.cache = cache; this.keys = keys;
    }
    public ComparisonRefreshResponse refresh(List<String> rawTickers, boolean regenerateBrief) {
        if (rawTickers == null || rawTickers.isEmpty() || rawTickers.size() > 2) throw new InvalidTickerException();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ticker : rawTickers) normalized.add(normalizer.normalize(ticker));
        if (normalized.size() != rawTickers.size()) throw new DuplicateTickersException();
        List<String> tickers = List.copyOf(normalized);
        List<String> warnings = new ArrayList<>();
        tickers.forEach(this::evictTicker);
        cache.evictByPrefix(keys.comparisonPrefix());
        cache.evictByPrefix(keys.briefPrefix());
        for (String ticker : tickers) {
            try {
                var company = stock.resolveCompany(ticker);
                stock.refreshMarketSnapshot(company);
                metrics.getMetrics(ticker);
                news.getRecentNews(ticker, NewsQueryService.DEFAULT_LIMIT);
            } catch (RuntimeException exception) { warnings.add("Some data could not be refreshed for " + ticker + "."); }
        }
        if (regenerateBrief && tickers.size() == 2) {
            try { research.generate(tickers.getFirst(), tickers.getLast(), true); }
            catch (RuntimeException exception) { warnings.add("The AI brief could not be regenerated."); }
        }
        return new ComparisonRefreshResponse(tickers, regenerateBrief, List.copyOf(warnings));
    }
    private void evictTicker(String ticker) {
        cache.evict(keys.company(ticker)); cache.evict(keys.market(ticker)); cache.evict(keys.metrics(ticker));
        for (String period : List.of("1M", "6M", "1Y", "5Y", "MAX")) cache.evict(keys.history(ticker, period));
        for (int limit : List.of(3, 10, 20)) cache.evict(keys.news(ticker, limit));
    }
}
