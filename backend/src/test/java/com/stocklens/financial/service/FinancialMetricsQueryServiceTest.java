package com.stocklens.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import com.stocklens.company.domain.Company;
import com.stocklens.company.service.CompanyService;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricDefinitionRegistry;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialMetricsQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T20:00:00Z");

    @Mock private FinancialDataClient client;
    @Mock private CompanyService companyService;
    @Mock private FinancialMetricSnapshotService snapshotService;
    @Mock private JsonRedisCache cache;
    private FinancialMetricsQueryService service;

    @BeforeEach
    void setUp() {
        service = new FinancialMetricsQueryService(
                new TickerNormalizer(), client, companyService, snapshotService,
                new MetricDefinitionRegistry(), cache, new StockLensCacheKeys(), new StockLensCacheProperties(null, null, null, null, null, null, null));
        org.mockito.Mockito.lenient().when(cache.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(com.stocklens.financial.dto.FinancialMetricsResponse.class))).thenReturn(java.util.Optional.empty());
    }

    @Test
    void fetchesBeforeWritingFallsBackCurrencyAndWarnsForUnavailableFields() {
        CompanyProfileData profile = profile();
        FinancialMetricsData providerData = data(null);
        FinancialMetricsData normalized = providerData.withCurrency("USD");
        Company company = company();
        FinancialMetricSnapshot snapshot = snapshot(company, normalized);
        when(client.getCompanyProfile("AAPL")).thenReturn(profile);
        when(client.getFinancialMetrics("AAPL")).thenReturn(providerData);
        when(companyService.upsert(profile)).thenReturn(company);
        when(snapshotService.create(company, normalized)).thenReturn(snapshot);

        var response = service.getMetrics(" aapl ");

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.metrics()).hasSize(MetricCode.values().length);
        assertThat(response.warnings()).extracting(warning -> warning.metricCode())
                .contains(MetricCode.FORWARD_PE, MetricCode.REVENUE_TTM, MetricCode.BETA);
        InOrder order = inOrder(client, companyService, snapshotService);
        order.verify(client).getCompanyProfile("AAPL");
        order.verify(client).getFinancialMetrics("AAPL");
        order.verify(companyService).upsert(profile);
        order.verify(snapshotService).create(company, normalized);
    }

    @Test
    void providerFailureDoesNotWrite() {
        CompanyProfileData profile = profile();
        when(client.getCompanyProfile("AAPL")).thenReturn(profile);
        when(client.getFinancialMetrics("AAPL")).thenThrow(new FinancialProviderException("failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getMetrics("AAPL"))
                .isInstanceOf(FinancialProviderException.class);
        verify(companyService, never()).upsert(profile);
    }

    private CompanyProfileData profile() {
        return new CompanyProfileData(
                "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null,
                "AAPL", "USD", NOW);
    }

    private FinancialMetricsData data(String currency) {
        return new FinancialMetricsData(
                "AAPL", new BigDecimal("31.2"), null, new BigDecimal("2.4"),
                new BigDecimal("8.1"), null, new BigDecimal("0.46"), new BigDecimal("0.24"),
                new BigDecimal("1.61"), new BigDecimal("0.063"), new BigDecimal("0.195"),
                new BigDecimal("1.52"), new BigDecimal("0.89"), null, currency,
                LocalDate.parse("2025-09-27"), NOW, "FMP");
    }

    private Company company() {
        return new Company(
                "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null, "AAPL", NOW, NOW);
    }

    private FinancialMetricSnapshot snapshot(Company company, FinancialMetricsData data) {
        return new FinancialMetricSnapshot(
                company, data.peTtm(), data.forwardPe(), data.pegRatio(), data.priceToSales(),
                data.revenueTtm(), data.grossMargin(), data.netMargin(), data.returnOnEquity(),
                data.revenueGrowth(), data.earningsGrowth(), data.debtToEquity(), data.currentRatio(),
                data.beta(), data.currency(), data.reportedAt(), data.retrievedAt(), data.providerName(), null);
    }
}
