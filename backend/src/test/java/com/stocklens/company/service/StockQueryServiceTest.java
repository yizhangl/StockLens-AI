package com.stocklens.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.common.time.FreshnessPolicy;
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.repository.MarketSnapshotRepository;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.service.MarketSnapshotService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock
    private FinancialDataClient financialDataClient;

    @Mock
    private CompanyService companyService;

    @Mock
    private MarketSnapshotService marketSnapshotService;
    @Mock private CompanyRepository companyRepository;
    @Mock private MarketSnapshotRepository marketRepository;
    @Mock private JsonRedisCache cache;

    private StockQueryService service;

    @BeforeEach
    void setUp() {
        service = new StockQueryService(
                new TickerNormalizer(), financialDataClient, companyService, marketSnapshotService, companyRepository, marketRepository,
                new FreshnessPolicy(java.time.Clock.systemUTC()), cache, new StockLensCacheKeys(), new StockLensCacheProperties(null, null, null, null, null, null, null));
        org.mockito.Mockito.lenient().when(companyRepository.findByTicker(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.lenient().when(cache.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());
    }

    @Test
    void normalizesThenFetchesBothResourcesAndPersistsInOrder() {
        CompanyProfileData profile = profile();
        MarketSnapshotData quote = quote(null);
        Company company = company();
        MarketSnapshot snapshot = snapshot(company);
        when(financialDataClient.getCompanyProfile("AAPL")).thenReturn(profile);
        when(financialDataClient.getMarketSnapshot("AAPL")).thenReturn(quote);
        when(companyService.upsert(profile)).thenReturn(company);
        when(marketSnapshotService.create(company, quote.withCurrency("USD"))).thenReturn(snapshot);

        var result = service.getStock(" aapl ");

        assertThat(result.company()).isSameAs(company);
        assertThat(result.latestMarketSnapshot()).isSameAs(snapshot);
        InOrder order = inOrder(financialDataClient, companyService, marketSnapshotService);
        order.verify(financialDataClient).getCompanyProfile("AAPL");
        order.verify(companyService).upsert(profile);
        order.verify(financialDataClient).getMarketSnapshot("AAPL");
        order.verify(marketSnapshotService).create(company, quote.withCurrency("USD"));
    }

    @Test
    void doesNotPersistWhenQuoteRetrievalFails() {
        CompanyProfileData profile = profile();
        Company company = company();
        when(financialDataClient.getCompanyProfile("AAPL")).thenReturn(profile);
        when(companyService.upsert(profile)).thenReturn(company);
        when(financialDataClient.getMarketSnapshot("AAPL"))
                .thenThrow(new FinancialProviderException("provider failure"));

        assertThatThrownBy(() -> service.getStock("AAPL"))
                .isInstanceOf(FinancialProviderException.class);
        verify(companyService).upsert(profile);
        verify(marketSnapshotService, never()).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvesCompanyBeforeOptionalMarketRefresh() {
        CompanyProfileData profile = profile();
        Company company = company();
        when(financialDataClient.getCompanyProfile("AAPL")).thenReturn(profile);
        when(companyService.upsert(profile)).thenReturn(company);

        var resolution = service.resolveCompany(" aapl ");

        assertThat(resolution.company()).isSameAs(company);
        assertThat(resolution.currency()).isEqualTo("USD");
        verify(financialDataClient, never()).getMarketSnapshot("AAPL");
    }

    @Test
    void refreshesMarketForAnAlreadyResolvedCompany() {
        Company company = company();
        MarketSnapshotData quote = quote(null);
        MarketSnapshot snapshot = snapshot(company);
        var resolution = new StockQueryService.CompanyResolution(company, "USD");
        when(financialDataClient.getMarketSnapshot("AAPL")).thenReturn(quote);
        when(marketSnapshotService.create(company, quote.withCurrency("USD"))).thenReturn(snapshot);

        assertThat(service.refreshMarketSnapshot(resolution)).isSameAs(snapshot);
        verify(financialDataClient, never()).getCompanyProfile("AAPL");
    }

    private CompanyProfileData profile() {
        return new CompanyProfileData(
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                "Technology",
                "Consumer Electronics",
                "US",
                null,
                null,
                null,
                "AAPL",
                "USD",
                Instant.parse("2026-07-18T20:00:00Z"));
    }

    private MarketSnapshotData quote(String currency) {
        return new MarketSnapshotData(
                "AAPL",
                new BigDecimal("268.47"),
                null,
                null,
                null,
                currency,
                Instant.parse("2026-07-18T19:00:00Z"),
                Instant.parse("2026-07-18T20:00:00Z"),
                "FMP");
    }

    private Company company() {
        return new Company(
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                null,
                null,
                null,
                null,
                null,
                null,
                "AAPL",
                Instant.parse("2026-07-18T20:00:00Z"),
                Instant.parse("2026-07-18T20:00:00Z"));
    }

    private MarketSnapshot snapshot(Company company) {
        return new MarketSnapshot(
                company,
                new BigDecimal("268.47"),
                null,
                null,
                null,
                "USD",
                Instant.parse("2026-07-18T19:00:00Z"),
                Instant.parse("2026-07-18T20:00:00Z"),
                "FMP",
                null);
    }
}
