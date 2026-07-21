package com.stocklens.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import com.stocklens.common.time.FreshnessPolicy;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.company.service.CompanyService;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.period.PriceDateRange;
import com.stocklens.financial.repository.HistoricalPriceRepository;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.HistoricalPriceData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalPriceQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final LocalDate TO = LocalDate.parse("2026-07-20");
    private static final LocalDate FROM = LocalDate.parse("2025-07-20");
    @Mock private FinancialDataClient client;
    @Mock private CompanyService companyService;
    @Mock private HistoricalPriceService priceService;
    @Mock private HistoricalPriceRepository repository;
    @Mock private JsonRedisCache cache;
    @Mock private Company company;
    private HistoricalPriceQueryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new HistoricalPriceQueryService(new TickerNormalizer(), client, companyService,
                priceService, new HistoricalReturnCalculator(), clock, cache, new StockLensCacheKeys(),
                new StockLensCacheProperties(null, null, null, null, null, null, null), repository,
                new FreshnessPolicy(clock));
        org.mockito.Mockito.lenient().when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(companyService.findByTicker("AAPL")).thenReturn(Optional.of(company));
        org.mockito.Mockito.lenient().when(company.getId()).thenReturn(1L);
    }

    @Test
    void redisHitAvoidsRepositoryAndProvider() {
        HistoricalPriceResponse cached = new HistoricalPriceResponse(
                "AAPL", "1Y", FROM, TO, "USD", BigDecimal.ZERO, "FMP", NOW, List.of());
        when(cache.get(anyString(), any())).thenReturn(Optional.of(cached));
        assertThat(service.getHistory("AAPL", "1Y")).isSameAs(cached);
        verify(repository, never()).findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(any());
        verify(client, never()).getHistoricalPrices(anyString(), any(), any());
    }

    @Test
    void freshCompletePersistedSeriesAvoidsProviderAndAllowsBoundaryTolerance() {
        when(repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(1L))
                .thenReturn(List.of(price(FROM.plusDays(2), "100", NOW.minusSeconds(60)),
                        price(TO.minusDays(2), "110", NOW.minusSeconds(60))));
        assertThat(service.getHistory("AAPL", "1Y").returnPercent()).isEqualByComparingTo("10.0000");
        verify(client, never()).getHistoricalPrices(anyString(), any(), any());
        verify(cache).put(anyString(), any(), any(Duration.class));
    }

    @Test
    void staleOrIncompleteSeriesCallsProviderExactlyOnce() {
        HistoricalPrice first = price(FROM, "100", NOW.minus(Duration.ofHours(6)));
        when(repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(1L)).thenReturn(List.of(first));
        List<HistoricalPriceData> provider = List.of(data(FROM, "100"), data(TO, "110"));
        when(client.getHistoricalPrices("AAPL", FROM, TO)).thenReturn(provider);
        when(priceService.upsert(company, "FMP", FROM, TO, provider))
                .thenReturn(List.of(price(FROM, "100", NOW), price(TO, "110", NOW)));
        assertThat(service.getHistory("AAPL", "1Y").prices()).hasSize(2);
        verify(client).getHistoricalPrices("AAPL", FROM, TO);
    }

    @Test
    void completeSeriesExactlyAtTtlIsStale() {
        List<HistoricalPrice> persisted = List.of(
                price(FROM, "100", NOW.minus(Duration.ofHours(6))),
                price(TO, "110", NOW.minus(Duration.ofHours(6))));
        List<HistoricalPriceData> provider = List.of(data(FROM, "100"), data(TO, "110"));
        when(repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(1L))
                .thenReturn(persisted);
        when(client.getHistoricalPrices("AAPL", FROM, TO)).thenReturn(provider);
        when(priceService.upsert(company, "FMP", FROM, TO, provider))
                .thenReturn(List.of(price(FROM, "100", NOW), price(TO, "110", NOW)));

        service.getHistory("AAPL", "1Y");

        verify(client).getHistoricalPrices("AAPL", FROM, TO);
    }

    @Test
    void providerFailureDoesNotWriteOrCorruptPersistedHistory() {
        when(repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(1L))
                .thenReturn(List.of(
                        price(FROM, "100", NOW.minus(Duration.ofHours(7))),
                        price(TO, "110", NOW.minus(Duration.ofHours(7)))));
        when(client.getHistoricalPrices("AAPL", FROM, TO))
                .thenThrow(new com.stocklens.common.exception.FinancialProviderException("failed"));

        assertThatThrownBy(() -> service.getHistory("AAPL", "1Y"))
                .isInstanceOf(com.stocklens.common.exception.FinancialProviderException.class);
        verify(priceService, never()).upsert(any(), anyString(), any(), any(), any());
    }

    @Test
    void completenessRejectsBadOrTruncatedDataAndAcceptsMax() {
        PriceDateRange bounded = new PriceDateRange(FROM, TO);
        assertThat(service.isComplete(List.of(), bounded)).isFalse();
        assertThat(service.isComplete(List.of(price(FROM, "100", NOW)), bounded)).isFalse();
        assertThat(service.usableSeries(List.of(price(FROM, "100", NOW), price(FROM, "101", NOW)), bounded)).hasSize(1);
        assertThat(service.usableSeries(List.of(price(FROM, null, NOW), price(TO, "-1", NOW)), bounded)).isEmpty();
        assertThat(service.isComplete(List.of(price(FROM.plusDays(20), "100", NOW), price(TO, "110", NOW)), bounded)).isFalse();
        assertThat(service.isComplete(List.of(price(FROM, "100", NOW), price(TO.minusDays(8), "110", NOW)), bounded)).isFalse();
        assertThat(service.isComplete(List.of(price(FROM, "100", NOW), price(TO, "110", NOW)), new PriceDateRange(null, TO))).isTrue();
    }

    private HistoricalPrice price(LocalDate date, String close, Instant retrievedAt) {
        return new HistoricalPrice(company, date, null, null, null,
                close == null ? null : new BigDecimal(close), null, 1L, "USD", "FMP", retrievedAt);
    }

    private HistoricalPriceData data(LocalDate date, String close) {
        return new HistoricalPriceData("AAPL", date, null, null, null, new BigDecimal(close), null,
                1L, "USD", "FMP", NOW);
    }
}
