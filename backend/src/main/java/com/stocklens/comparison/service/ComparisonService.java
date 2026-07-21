package com.stocklens.comparison.service;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.DuplicateTickersException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.comparison.dto.ComparisonDashboardResponse;
import com.stocklens.comparison.dto.ComparisonNewsArticleResponse;
import com.stocklens.comparison.dto.ComparisonNewsResponse;
import com.stocklens.comparison.dto.ComparisonProvenanceResponse;
import com.stocklens.comparison.dto.ComparisonWarningResponse;
import com.stocklens.comparison.dto.CompanySummaryResponse;
import com.stocklens.comparison.dto.PricePerformanceResponse;
import com.stocklens.comparison.dto.MetricGroupResponse;
import com.stocklens.comparison.model.ComparisonMode;
import com.stocklens.comparison.model.ComparisonWarningSection;
import com.stocklens.comparison.model.ComparisonWarningSide;
import com.stocklens.comparison.model.ComparisonOutcome;
import com.stocklens.company.domain.Company;
import com.stocklens.company.service.StockQueryService;
import com.stocklens.company.service.StockQueryService.CompanyResolution;
import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.dto.MetricValueResponse;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.period.PricePeriod;
import com.stocklens.financial.service.FinancialMetricsQueryService;
import com.stocklens.financial.service.HistoricalPriceQueryService;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.news.dto.NewsArticleResponse;
import com.stocklens.news.dto.NewsResponse;
import com.stocklens.news.service.NewsQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);
    private static final int NEWS_LIMIT = 3;

    private final TickerNormalizer tickerNormalizer;
    private final StockQueryService stockQueryService;
    private final FinancialMetricsQueryService metricsQueryService;
    private final HistoricalPriceQueryService historyQueryService;
    private final NewsQueryService newsQueryService;
    private final HistoricalSeriesAligner historicalSeriesAligner;
    private final MetricComparisonService metricComparisonService;
    private final JsonRedisCache cache;
    private final StockLensCacheKeys cacheKeys;
    private final StockLensCacheProperties cacheProperties;

    public ComparisonService(
            TickerNormalizer tickerNormalizer,
            StockQueryService stockQueryService,
            FinancialMetricsQueryService metricsQueryService,
            HistoricalPriceQueryService historyQueryService,
            NewsQueryService newsQueryService,
            HistoricalSeriesAligner historicalSeriesAligner,
            MetricComparisonService metricComparisonService, JsonRedisCache cache,
            StockLensCacheKeys cacheKeys, StockLensCacheProperties cacheProperties) {
        this.tickerNormalizer = tickerNormalizer;
        this.stockQueryService = stockQueryService;
        this.metricsQueryService = metricsQueryService;
        this.historyQueryService = historyQueryService;
        this.newsQueryService = newsQueryService;
        this.historicalSeriesAligner = historicalSeriesAligner;
        this.metricComparisonService = metricComparisonService;
        this.cache = cache;
        this.cacheKeys = cacheKeys;
        this.cacheProperties = cacheProperties;
    }

    public ComparisonDashboardResponse compare(
            String rawLeft,
            String rawRight,
            String rawPeriod,
            String rawMode) {
        long startedAt = System.nanoTime();
        String leftTicker = tickerNormalizer.normalize(rawLeft);
        String rightTicker = tickerNormalizer.normalize(rawRight);
        if (leftTicker.equals(rightTicker)) {
            throw new DuplicateTickersException();
        }
        PricePeriod period = PricePeriod.parse(rawPeriod);
        ComparisonMode mode = ComparisonMode.parse(rawMode);
        String cacheKey = cacheKeys.comparison(leftTicker, rightTicker, period.code(), mode.name());
        var cached = cache.get(cacheKey, ComparisonDashboardResponse.class);
        if (cached.isPresent()) return leftTicker.compareTo(rightTicker) <= 0 ? withCached(cached.get()) : flip(cached.get(), true);
        String comparisonId = comparisonId(leftTicker, rightTicker, period, mode);

        CompanyResolution leftResolution = stockQueryService.resolveCompany(leftTicker);
        CompanyResolution rightResolution = stockQueryService.resolveCompany(rightTicker);
        WarningCollector warnings = new WarningCollector();

        MarketSnapshot leftMarket = loadFinancialSection(
                () -> stockQueryService.refreshMarketSnapshot(leftResolution),
                ComparisonWarningSection.MARKET,
                ComparisonWarningSide.LEFT,
                leftTicker,
                warnings);
        MarketSnapshot rightMarket = loadFinancialSection(
                () -> stockQueryService.refreshMarketSnapshot(rightResolution),
                ComparisonWarningSection.MARKET,
                ComparisonWarningSide.RIGHT,
                rightTicker,
                warnings);
        FinancialMetricsResponse leftMetrics = loadFinancialSection(
                () -> metricsQueryService.getMetrics(leftTicker),
                ComparisonWarningSection.METRICS,
                ComparisonWarningSide.LEFT,
                leftTicker,
                warnings);
        FinancialMetricsResponse rightMetrics = loadFinancialSection(
                () -> metricsQueryService.getMetrics(rightTicker),
                ComparisonWarningSection.METRICS,
                ComparisonWarningSide.RIGHT,
                rightTicker,
                warnings);
        HistoricalPriceResponse leftHistory = loadFinancialSection(
                () -> historyQueryService.getHistory(leftTicker, period.code()),
                ComparisonWarningSection.HISTORY,
                ComparisonWarningSide.LEFT,
                leftTicker,
                warnings);
        HistoricalPriceResponse rightHistory = loadFinancialSection(
                () -> historyQueryService.getHistory(rightTicker, period.code()),
                ComparisonWarningSection.HISTORY,
                ComparisonWarningSide.RIGHT,
                rightTicker,
                warnings);
        NewsResponse leftNews = loadNews(
                leftTicker, ComparisonWarningSide.LEFT, warnings);
        NewsResponse rightNews = loadNews(
                rightTicker, ComparisonWarningSide.RIGHT, warnings);

        HistoricalSeriesAligner.AlignmentResult alignment = historicalSeriesAligner.align(
                leftHistory, rightHistory, period, mode);
        if (leftHistory != null && rightHistory != null) {
            alignment.issues().forEach(issue -> addAlignmentWarning(issue, warnings));
            if (mode == ComparisonMode.PRICE
                    && differentCurrencies(leftHistory.currency(), rightHistory.currency())) {
                warnings.add(new ComparisonWarningResponse(
                        ComparisonWarningSection.HISTORY,
                        ComparisonWarningSide.BOTH,
                        "CURRENCY_MISMATCH",
                        "Raw prices use different currencies and are not directly comparable."));
            }
        }

        CompanySummaryResponse leftSummary = summary(
                leftResolution.company(), leftMarket, leftMetrics, leftHistory);
        CompanySummaryResponse rightSummary = summary(
                rightResolution.company(), rightMarket, rightMetrics, rightHistory);
        ComparisonNewsResponse news = new ComparisonNewsResponse(
                newsArticles(leftNews), newsArticles(rightNews));
        ComparisonProvenanceResponse provenance = provenance(
                leftResolution.company(),
                rightResolution.company(),
                leftMarket,
                rightMarket,
                leftMetrics,
                rightMetrics,
                leftHistory,
                rightHistory,
                leftNews,
                rightNews,
                alignment.performance());
        ComparisonDashboardResponse response = new ComparisonDashboardResponse(
                comparisonId,
                leftSummary,
                rightSummary,
                alignment.performance(),
                metricComparisonService.compare(leftMetrics, rightMetrics),
                news,
                null,
                provenance,
                warnings.values());

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "Comparison assembled comparisonId={} left={} right={} period={} mode={} warnings={} elapsedMs={}",
                comparisonId,
                leftTicker,
                rightTicker,
                period.code(),
                mode,
                response.warnings().size(),
                elapsedMillis);
        if (response.warnings().isEmpty()) cache.put(cacheKey, leftTicker.compareTo(rightTicker) <= 0 ? response : flip(response, false), cacheProperties.comparisonTtl());
        return response;
    }

    /** Cache values are canonical (alphabetical pair); callers retain their requested orientation. */
    private ComparisonDashboardResponse flip(ComparisonDashboardResponse response, boolean cached) {
        PricePerformanceResponse performance = response.pricePerformance();
        PricePerformanceResponse flippedPerformance = new PricePerformanceResponse(
                performance.period(), performance.mode(), performance.startDate(), performance.endDate(), performance.pointCount(),
                performance.rightReturnPercent(), performance.leftReturnPercent(), performance.rightCurrency(), performance.leftCurrency(),
                performance.series().stream().map(point -> new com.stocklens.comparison.dto.PricePerformancePoint(
                        point.date(), point.rightValue(), point.leftValue())).toList());
        List<MetricGroupResponse> groups = response.metricGroups().stream().map(group -> new MetricGroupResponse(group.category(),
                group.metrics().stream().map(metric -> new com.stocklens.comparison.dto.MetricComparisonResponse(
                        metric.code(), metric.displayName(), metric.category(), metric.unit(), metric.rightValue(), metric.leftValue(),
                        metric.comparisonStrategy(), flipOutcome(metric.outcome()), metric.explanation())).toList())).toList();
        ComparisonProvenanceResponse p = response.provenance();
        ComparisonProvenanceResponse provenance = new ComparisonProvenanceResponse(p.financialProvider(), p.newsProvider(),
                p.rightCompanyUpdatedAt(), p.leftCompanyUpdatedAt(), p.rightMarketRetrievedAt(), p.leftMarketRetrievedAt(),
                p.rightMetricsRetrievedAt(), p.leftMetricsRetrievedAt(), p.rightHistoryRetrievedAt(), p.leftHistoryRetrievedAt(),
                p.rightNewsRetrievedAt(), p.leftNewsRetrievedAt(), p.historyStartDate(), p.historyEndDate(), p.lastUpdatedAt(), cached || p.cached());
        return new ComparisonDashboardResponse(response.comparisonId(), response.right(), response.left(), flippedPerformance, groups,
                new ComparisonNewsResponse(response.news().right(), response.news().left()), response.aiBrief(), provenance,
                response.warnings().stream().map(w -> new ComparisonWarningResponse(w.section(), flipSide(w.side()), w.code(), w.message())).toList());
    }

    private ComparisonDashboardResponse withCached(ComparisonDashboardResponse response) {
        ComparisonProvenanceResponse p = response.provenance();
        ComparisonProvenanceResponse provenance = new ComparisonProvenanceResponse(p.financialProvider(), p.newsProvider(),
                p.leftCompanyUpdatedAt(), p.rightCompanyUpdatedAt(), p.leftMarketRetrievedAt(), p.rightMarketRetrievedAt(),
                p.leftMetricsRetrievedAt(), p.rightMetricsRetrievedAt(), p.leftHistoryRetrievedAt(), p.rightHistoryRetrievedAt(),
                p.leftNewsRetrievedAt(), p.rightNewsRetrievedAt(), p.historyStartDate(), p.historyEndDate(), p.lastUpdatedAt(), true);
        return new ComparisonDashboardResponse(response.comparisonId(), response.left(), response.right(), response.pricePerformance(),
                response.metricGroups(), response.news(), response.aiBrief(), provenance, response.warnings());
    }

    private ComparisonOutcome flipOutcome(ComparisonOutcome outcome) {
        return outcome == ComparisonOutcome.LEFT ? ComparisonOutcome.RIGHT
                : outcome == ComparisonOutcome.RIGHT ? ComparisonOutcome.LEFT : outcome;
    }
    private ComparisonWarningSide flipSide(ComparisonWarningSide side) {
        return side == ComparisonWarningSide.LEFT ? ComparisonWarningSide.RIGHT
                : side == ComparisonWarningSide.RIGHT ? ComparisonWarningSide.LEFT : side;
    }

    private <T> T loadFinancialSection(
            Supplier<T> supplier,
            ComparisonWarningSection section,
            ComparisonWarningSide side,
            String ticker,
            WarningCollector warnings) {
        try {
            return Objects.requireNonNull(supplier.get());
        } catch (FinancialProviderException | DataUnavailableException | StockNotFoundException exception) {
            warnings.add(new ComparisonWarningResponse(
                    section,
                    side,
                    financialFailureCode(exception),
                    financialFailureMessage(section, ticker)));
            return null;
        }
    }

    private NewsResponse loadNews(
            String ticker,
            ComparisonWarningSide side,
            WarningCollector warnings) {
        try {
            NewsResponse response = Objects.requireNonNull(
                    newsQueryService.getRecentNews(ticker, NEWS_LIMIT));
            if (response.warnings() != null && !response.warnings().isEmpty()) {
                warnings.add(new ComparisonWarningResponse(
                        ComparisonWarningSection.NEWS,
                        side,
                        "INVALID_PROVIDER_RECORDS_SKIPPED",
                        "Some recent news records were unavailable for " + ticker + "."));
            }
            return response;
        } catch (NewsProviderException | DataUnavailableException | StockNotFoundException exception) {
            warnings.add(new ComparisonWarningResponse(
                    ComparisonWarningSection.NEWS,
                    side,
                    newsFailureCode(exception),
                    "Recent news is temporarily unavailable for " + ticker + "."));
            return null;
        }
    }

    private String financialFailureCode(RuntimeException exception) {
        if (exception instanceof FinancialProviderRateLimitedException) {
            return "RATE_LIMITED";
        }
        if (exception instanceof FinancialProviderException) {
            return "FINANCIAL_PROVIDER_ERROR";
        }
        if (exception instanceof StockNotFoundException) {
            return "STOCK_NOT_FOUND";
        }
        return "DATA_UNAVAILABLE";
    }

    private String newsFailureCode(RuntimeException exception) {
        if (exception instanceof NewsProviderRateLimitedException) {
            return "RATE_LIMITED";
        }
        if (exception instanceof NewsProviderException) {
            return "NEWS_PROVIDER_ERROR";
        }
        if (exception instanceof StockNotFoundException) {
            return "STOCK_NOT_FOUND";
        }
        return "DATA_UNAVAILABLE";
    }

    private String financialFailureMessage(
            ComparisonWarningSection section,
            String ticker) {
        return switch (section) {
            case MARKET -> "Market data is temporarily unavailable for " + ticker + ".";
            case METRICS -> "Financial metrics are temporarily unavailable for " + ticker + ".";
            case HISTORY -> "Historical performance is temporarily unavailable for " + ticker + ".";
            case NEWS -> throw new IllegalArgumentException("News uses a separate failure mapper");
        };
    }

    private void addAlignmentWarning(
            HistoricalSeriesAligner.AlignmentIssue issue,
            WarningCollector warnings) {
        if (issue == HistoricalSeriesAligner.AlignmentIssue.NO_COMMON_HISTORY) {
            warnings.add(new ComparisonWarningResponse(
                    ComparisonWarningSection.HISTORY,
                    ComparisonWarningSide.BOTH,
                    "NO_COMMON_HISTORY",
                    "No common trading dates are available for the selected period."));
        } else {
            warnings.add(new ComparisonWarningResponse(
                    ComparisonWarningSection.HISTORY,
                    ComparisonWarningSide.BOTH,
                    "INSUFFICIENT_HISTORY",
                    "At least two common trading dates are required to calculate returns."));
        }
    }

    private CompanySummaryResponse summary(
            Company company,
            MarketSnapshot market,
            FinancialMetricsResponse metrics,
            HistoricalPriceResponse history) {
        return new CompanySummaryResponse(
                company.getTicker(),
                company.getName(),
                company.getExchange(),
                company.getSector(),
                company.getIndustry(),
                company.getCountry(),
                company.getWebsiteUrl(),
                company.getLogoUrl(),
                company.getDescription(),
                market == null ? null : market.getPrice(),
                market == null ? null : market.getPriceChange(),
                market == null ? null : market.getPriceChangePercent(),
                market == null ? null : market.getMarketCap(),
                firstNonBlank(
                        market == null ? null : market.getCurrency(),
                        metrics == null ? null : metrics.currency(),
                        history == null ? null : history.currency()),
                market == null ? null : market.getQuoteTimestamp(),
                metricValue(metrics, MetricCode.PE_TTM),
                metricValue(metrics, MetricCode.REVENUE_TTM),
                company.getUpdatedAt(),
                market == null ? null : market.getRetrievedAt(),
                metrics == null ? null : metrics.retrievedAt());
    }

    private BigDecimal metricValue(FinancialMetricsResponse response, MetricCode code) {
        if (response == null || response.metrics() == null) {
            return null;
        }
        return response.metrics().stream()
                .filter(metric -> metric != null && metric.code() == code)
                .findFirst()
                .map(MetricValueResponse::value)
                .orElse(null);
    }

    private List<ComparisonNewsArticleResponse> newsArticles(NewsResponse response) {
        if (response == null || response.articles() == null) {
            return List.of();
        }
        return response.articles().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(NewsArticleResponse::publishedAt)
                        .reversed()
                        .thenComparing(NewsArticleResponse::headline))
                .limit(NEWS_LIMIT)
                .map(article -> new ComparisonNewsArticleResponse(
                        article.id(),
                        article.headline(),
                        article.sourceName(),
                        article.url(),
                        article.publishedAt(),
                        article.description(),
                        article.relatedSymbols() == null
                                ? List.of()
                                : List.copyOf(article.relatedSymbols())))
                .toList();
    }

    private ComparisonProvenanceResponse provenance(
            Company leftCompany,
            Company rightCompany,
            MarketSnapshot leftMarket,
            MarketSnapshot rightMarket,
            FinancialMetricsResponse leftMetrics,
            FinancialMetricsResponse rightMetrics,
            HistoricalPriceResponse leftHistory,
            HistoricalPriceResponse rightHistory,
            NewsResponse leftNews,
            NewsResponse rightNews,
            PricePerformanceResponse performance) {
        String financialProvider = providerNames(
                leftMarket == null ? null : leftMarket.getProviderName(),
                rightMarket == null ? null : rightMarket.getProviderName(),
                leftMetrics == null ? null : leftMetrics.providerName(),
                rightMetrics == null ? null : rightMetrics.providerName(),
                leftHistory == null ? null : leftHistory.providerName(),
                rightHistory == null ? null : rightHistory.providerName());
        String newsProvider = providerNames(
                leftNews == null ? null : leftNews.providerName(),
                rightNews == null ? null : rightNews.providerName());
        Instant lastUpdatedAt = Stream.of(
                        leftCompany.getUpdatedAt(),
                        rightCompany.getUpdatedAt(),
                        leftMarket == null ? null : leftMarket.getRetrievedAt(),
                        rightMarket == null ? null : rightMarket.getRetrievedAt(),
                        leftMetrics == null ? null : leftMetrics.retrievedAt(),
                        rightMetrics == null ? null : rightMetrics.retrievedAt(),
                        leftHistory == null ? null : leftHistory.retrievedAt(),
                        rightHistory == null ? null : rightHistory.retrievedAt(),
                        leftNews == null ? null : leftNews.retrievedAt(),
                        rightNews == null ? null : rightNews.retrievedAt())
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        return new ComparisonProvenanceResponse(
                financialProvider,
                newsProvider,
                leftCompany.getUpdatedAt(),
                rightCompany.getUpdatedAt(),
                leftMarket == null ? null : leftMarket.getRetrievedAt(),
                rightMarket == null ? null : rightMarket.getRetrievedAt(),
                leftMetrics == null ? null : leftMetrics.retrievedAt(),
                rightMetrics == null ? null : rightMetrics.retrievedAt(),
                leftHistory == null ? null : leftHistory.retrievedAt(),
                rightHistory == null ? null : rightHistory.retrievedAt(),
                leftNews == null ? null : leftNews.retrievedAt(),
                rightNews == null ? null : rightNews.retrievedAt(),
                performance.startDate(),
                performance.endDate(),
                lastUpdatedAt,
                false);
    }

    private String providerNames(String... names) {
        TreeSet<String> distinct = new TreeSet<>();
        Stream.of(names)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(distinct::add);
        return distinct.isEmpty() ? null : String.join(",", distinct);
    }

    private String firstNonBlank(String... values) {
        return Stream.of(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean differentCurrencies(String left, String right) {
        return left != null && right != null && !left.equalsIgnoreCase(right);
    }

    private String comparisonId(
            String left,
            String right,
            PricePeriod period,
            ComparisonMode mode) {
        String first = left.compareTo(right) <= 0 ? left : right;
        String second = left.compareTo(right) <= 0 ? right : left;
        return first + ":" + second + ":" + period.code() + ":" + mode;
    }

    private static final class WarningCollector {

        private final Map<String, ComparisonWarningResponse> warnings = new LinkedHashMap<>();

        void add(ComparisonWarningResponse warning) {
            String key = warning.section() + ":" + warning.side() + ":" + warning.code();
            warnings.putIfAbsent(key, warning);
        }

        List<ComparisonWarningResponse> values() {
            return List.copyOf(new ArrayList<>(warnings.values()));
        }
    }
}
