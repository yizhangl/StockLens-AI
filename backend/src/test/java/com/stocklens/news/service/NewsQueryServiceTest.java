package com.stocklens.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stocklens.common.exception.InvalidNewsLimitException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.company.service.CompanyService;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.news.client.NewsDataClient;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.service.NewsArticlePersistenceService.PersistenceResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T20:00:00Z");

    @Mock private CompanyRepository companyRepository;
    @Mock private FinancialDataClient financialDataClient;
    @Mock private CompanyService companyService;
    @Mock private NewsDataClient newsDataClient;
    @Mock private NewsArticlePersistenceService persistenceService;
    private NewsQueryService service;

    @BeforeEach
    void setUp() {
        service = new NewsQueryService(
                new TickerNormalizer(),
                companyRepository,
                financialDataClient,
                companyService,
                newsDataClient,
                persistenceService);
    }

    @Test
    void normalizesTickerUsesExistingCompanyAndMapsPartialWarning() {
        Company company = company();
        NewsFetchResult fetch = new NewsFetchResult(List.of(), 1, "FMP", NOW);
        when(companyRepository.findByTicker("AAPL")).thenReturn(Optional.of(company));
        when(newsDataClient.getRecentNews("AAPL", 3)).thenReturn(fetch);
        when(persistenceService.persistAndLoadRecent(company, fetch, 3))
                .thenReturn(new PersistenceResult(List.of(), 2));

        var response = service.getRecentNews(" aapl ", 3);

        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.limit()).isEqualTo(3);
        assertThat(response.articles()).isEmpty();
        assertThat(response.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("INVALID_PROVIDER_RECORDS_SKIPPED");
            assertThat(warning.skippedArticleCount()).isEqualTo(2);
        });
        verify(financialDataClient, never()).getCompanyProfile(any());
    }

    @Test
    void resolvesAndPersistsCompanyWhenItIsNotAlreadyStored() {
        CompanyProfileData profile = new CompanyProfileData(
                "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null,
                "AAPL", "USD", NOW);
        Company company = company();
        NewsFetchResult fetch = new NewsFetchResult(List.of(), 0, "FMP", NOW);
        when(companyRepository.findByTicker("AAPL")).thenReturn(Optional.empty());
        when(financialDataClient.getCompanyProfile("AAPL")).thenReturn(profile);
        when(companyService.upsert(profile)).thenReturn(company);
        when(newsDataClient.getRecentNews("AAPL", 10)).thenReturn(fetch);
        when(persistenceService.persistAndLoadRecent(company, fetch, 10))
                .thenReturn(new PersistenceResult(List.of(), 0));

        var response = service.getRecentNews("AAPL", 10);

        assertThat(response.providerName()).isEqualTo("FMP");
        assertThat(response.warnings()).isEmpty();
        verify(companyService).upsert(profile);
    }

    @Test
    void rejectsLowAndHighLimitsBeforeProviderOrPersistenceWork() {
        assertThatThrownBy(() -> service.getRecentNews("AAPL", 0))
                .isInstanceOf(InvalidNewsLimitException.class);
        assertThatThrownBy(() -> service.getRecentNews("AAPL", 21))
                .isInstanceOf(InvalidNewsLimitException.class);

        verify(companyRepository, never()).findByTicker(any());
        verify(newsDataClient, never()).getRecentNews(any(), anyInt());
        verify(persistenceService, never()).persistAndLoadRecent(any(), any(), anyInt());
    }

    @Test
    void providerFailureDoesNotInvokePersistence() {
        Company company = company();
        when(companyRepository.findByTicker("AAPL")).thenReturn(Optional.of(company));
        when(newsDataClient.getRecentNews("AAPL", 10))
                .thenThrow(new NewsProviderException("provider failed"));

        assertThatThrownBy(() -> service.getRecentNews("AAPL", 10))
                .isInstanceOf(NewsProviderException.class);
        verify(persistenceService, never()).persistAndLoadRecent(any(), any(), anyInt());
    }

    private Company company() {
        return new Company(
                "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null,
                "AAPL", NOW, NOW);
    }
}
