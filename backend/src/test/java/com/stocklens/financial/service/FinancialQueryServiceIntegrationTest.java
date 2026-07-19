package com.stocklens.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.financial.repository.FinancialMetricSnapshotRepository;
import com.stocklens.financial.repository.HistoricalPriceRepository;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.support.IntegrationTestContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import({IntegrationTestContainers.class, FinancialQueryServiceIntegrationTest.FakeProviderConfiguration.class})
@SpringBootTest
class FinancialQueryServiceIntegrationTest {

    @Autowired private FinancialMetricsQueryService metricsQueryService;
    @Autowired private HistoricalPriceQueryService historyQueryService;
    @Autowired private FinancialMetricSnapshotRepository metricRepository;
    @Autowired private HistoricalPriceRepository priceRepository;
    @Autowired private CompanyRepository companyRepository;

    @AfterEach
    void cleanUp() {
        priceRepository.deleteAll();
        metricRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void normalizesPersistsAndReturnsProviderNeutralFinancialData() {
        var metrics = metricsQueryService.getMetrics(" aapl ");
        var firstHistory = historyQueryService.getHistory("aapl", "1Y");
        var repeatedHistory = historyQueryService.getHistory("AAPL", "1Y");

        assertThat(metrics.ticker()).isEqualTo("AAPL");
        assertThat(metrics.currency()).isEqualTo("USD");
        assertThat(metricRepository.count()).isEqualTo(1);
        assertThat(firstHistory.returnPercent()).isEqualByComparingTo("10.0000");
        assertThat(firstHistory.prices()).hasSize(2);
        assertThat(repeatedHistory.prices()).hasSize(2);
        assertThat(priceRepository.count()).isEqualTo(2);
        assertThat(companyRepository.count()).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {

        @Bean
        @Primary
        FinancialDataClient fakeFinancialDataClient() {
            return new FakeFinancialDataClient();
        }
    }

    static final class FakeFinancialDataClient implements FinancialDataClient {

        private static final Instant NOW = Instant.parse("2026-07-18T20:00:00Z");

        @Override
        public CompanyProfileData getCompanyProfile(String ticker) {
            return new CompanyProfileData(
                    ticker, "Apple Inc.", "NASDAQ", null, null, null, null, null, null,
                    ticker, "USD", NOW);
        }

        @Override
        public MarketSnapshotData getMarketSnapshot(String ticker) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public FinancialMetricsData getFinancialMetrics(String ticker) {
            return new FinancialMetricsData(
                    ticker, new BigDecimal("31.2"), null, null, null, null,
                    new BigDecimal("0.46"), new BigDecimal("0.24"), null,
                    new BigDecimal("0.063"), new BigDecimal("0.195"), null,
                    new BigDecimal("0.89"), null, null, LocalDate.parse("2025-09-27"), NOW, "FMP");
        }

        @Override
        public List<HistoricalPriceData> getHistoricalPrices(
                String ticker, LocalDate from, LocalDate to) {
            return List.of(
                    price(ticker, to.minusDays(1), "100"),
                    price(ticker, to, "110"));
        }

        private HistoricalPriceData price(String ticker, LocalDate date, String close) {
            return new HistoricalPriceData(
                    ticker, date, null, null, null, new BigDecimal(close), null,
                    1000L, null, "FMP", NOW);
        }
    }
}
