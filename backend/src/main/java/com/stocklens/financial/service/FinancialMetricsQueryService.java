package com.stocklens.financial.service;

import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.company.service.CompanyService;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.MetricValueResponse;
import com.stocklens.financial.dto.MetricWarningResponse;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricDefinition;
import com.stocklens.financial.metric.MetricDefinitionRegistry;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FinancialMetricsQueryService {

    private final TickerNormalizer tickerNormalizer;
    private final FinancialDataClient financialDataClient;
    private final CompanyService companyService;
    private final FinancialMetricSnapshotService snapshotService;
    private final MetricDefinitionRegistry registry;
    private final JsonRedisCache cache;
    private final StockLensCacheKeys cacheKeys;
    private final StockLensCacheProperties cacheProperties;

    public FinancialMetricsQueryService(
            TickerNormalizer tickerNormalizer,
            FinancialDataClient financialDataClient,
            CompanyService companyService,
            FinancialMetricSnapshotService snapshotService,
            MetricDefinitionRegistry registry, JsonRedisCache cache, StockLensCacheKeys cacheKeys,
            StockLensCacheProperties cacheProperties) {
        this.tickerNormalizer = tickerNormalizer;
        this.financialDataClient = financialDataClient;
        this.companyService = companyService;
        this.snapshotService = snapshotService;
        this.registry = registry;
        this.cache = cache; this.cacheKeys = cacheKeys; this.cacheProperties = cacheProperties;
    }

    public FinancialMetricsResponse getMetrics(String rawTicker) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        var cached = cache.get(cacheKeys.metrics(ticker), FinancialMetricsResponse.class);
        if (cached.isPresent()) return cached.get();
        CompanyProfileData profile = financialDataClient.getCompanyProfile(ticker);
        FinancialMetricsData data = financialDataClient.getFinancialMetrics(ticker)
                .withCurrency(profile.currency());
        Company company = companyService.upsert(profile);
        FinancialMetricSnapshot snapshot = snapshotService.create(company, data);

        List<MetricValueResponse> metrics = registry.all().stream()
                .map(definition -> toMetric(definition, snapshot))
                .toList();
        List<MetricWarningResponse> warnings = new ArrayList<>();
        metrics.stream()
                .filter(metric -> metric.value() == null)
                .forEach(metric -> warnings.add(new MetricWarningResponse(
                        metric.code(), "The selected provider did not supply this metric directly.")));
        FinancialMetricsResponse response = new FinancialMetricsResponse(
                ticker,
                snapshot.getCurrency(),
                snapshot.getReportedAt(),
                snapshot.getRetrievedAt(),
                snapshot.getProviderName(),
                metrics,
                List.copyOf(warnings));
        cache.put(cacheKeys.metrics(ticker), response, cacheProperties.metricsTtl());
        return response;
    }

    private MetricValueResponse toMetric(
            MetricDefinition definition, FinancialMetricSnapshot snapshot) {
        return new MetricValueResponse(
                definition.code(),
                definition.displayName(),
                definition.category(),
                definition.unit(),
                definition.comparisonStrategy(),
                value(definition.code(), snapshot),
                definition.description());
    }

    private BigDecimal value(MetricCode code, FinancialMetricSnapshot snapshot) {
        return switch (code) {
            case PE_TTM -> snapshot.getPeTtm();
            case FORWARD_PE -> snapshot.getForwardPe();
            case PEG_RATIO -> snapshot.getPegRatio();
            case PRICE_TO_SALES -> snapshot.getPriceToSales();
            case REVENUE_TTM -> snapshot.getRevenueTtm();
            case GROSS_MARGIN -> snapshot.getGrossMargin();
            case NET_MARGIN -> snapshot.getNetMargin();
            case RETURN_ON_EQUITY -> snapshot.getReturnOnEquity();
            case REVENUE_GROWTH -> snapshot.getRevenueGrowth();
            case EARNINGS_GROWTH -> snapshot.getEarningsGrowth();
            case DEBT_TO_EQUITY -> snapshot.getDebtToEquity();
            case CURRENT_RATIO -> snapshot.getCurrentRatio();
            case BETA -> snapshot.getBeta();
        };
    }
}
