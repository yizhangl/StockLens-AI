package com.stocklens.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.news.client.NewsDataClient;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.repository.NewsArticleRepository;
import com.stocklens.support.IntegrationTestContainers;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import({IntegrationTestContainers.class, NewsQueryServiceIntegrationTest.FakeProviderConfiguration.class})
@SpringBootTest
class NewsQueryServiceIntegrationTest {

    @Autowired private NewsQueryService service;
    @Autowired private NewsArticleRepository articleRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM news_article_company");
        articleRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void normalizesResolvesCompanyAndKeepsRepeatedRequestsIdempotent() {
        var first = service.getRecentNews(" aapl ", 3);
        var repeated = service.getRecentNews("AAPL", 3);

        assertThat(first.ticker()).isEqualTo("AAPL");
        assertThat(first.articles()).singleElement().satisfies(article -> {
            assertThat(article.url()).isEqualTo("https://example.com/shared-story?edition=us");
            assertThat(article.relatedSymbols()).containsExactly("AAPL");
        });
        assertThat(repeated.articles()).hasSize(1);
        assertThat(companyRepository.count()).isEqualTo(1);
        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(relationshipCount()).isEqualTo(1);
    }

    @Test
    void associatesOneCanonicalArticleWithAnotherCompany() {
        service.getRecentNews("AAPL", 3);
        var microsoft = service.getRecentNews("MSFT", 3);

        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(companyRepository.count()).isEqualTo(2);
        assertThat(relationshipCount()).isEqualTo(2);
        assertThat(microsoft.articles()).singleElement()
                .satisfies(article -> assertThat(article.relatedSymbols())
                        .containsExactly("AAPL", "MSFT"));
    }

    @Test
    void preservesValidArticleWithWarningAndReturnsValidEmptyResult() {
        var partial = service.getRecentNews("PART", 10);
        var empty = service.getRecentNews("EMPTY", 10);

        assertThat(partial.articles()).singleElement()
                .satisfies(article -> assertThat(article.headline())
                        .isEqualTo("PART valid article"));
        assertThat(partial.warnings()).singleElement()
                .satisfies(warning -> assertThat(warning.skippedArticleCount()).isEqualTo(1));
        assertThat(empty.articles()).isEmpty();
        assertThat(empty.warnings()).isEmpty();
        assertThat(companyRepository.count()).isEqualTo(2);
    }

    @Test
    void filtersIrrelevantCandidatesBeforePersistenceAndStillFillsPublicLimit() {
        var response = service.getRecentNews("FILTER", 3);

        assertThat(response.articles()).extracting(article -> article.headline())
                .containsExactly(
                        "FILTER launches product three",
                        "FILTER launches product two",
                        "FILTER launches product one");
        assertThat(articleRepository.count()).isEqualTo(3);
        assertThat(relationshipCount()).isEqualTo(3);
    }

    private long relationshipCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM news_article_company", Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {

        @Bean
        @Primary
        FinancialDataClient fakeFinancialDataClient() {
            return new FakeFinancialDataClient();
        }

        @Bean
        @Primary
        NewsDataClient fakeNewsDataClient() {
            return new FakeNewsDataClient();
        }
    }

    static final class FakeFinancialDataClient implements FinancialDataClient {

        @Override
        public CompanyProfileData getCompanyProfile(String ticker) {
            return new CompanyProfileData(
                    ticker, ticker + " Inc.", "NASDAQ", null, null, null, null, null, null,
                    ticker, "USD", Instant.parse("2026-07-18T20:00:00Z"));
        }

        @Override
        public MarketSnapshotData getMarketSnapshot(String ticker) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public FinancialMetricsData getFinancialMetrics(String ticker) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public List<HistoricalPriceData> getHistoricalPrices(
                String ticker, LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    static final class FakeNewsDataClient implements NewsDataClient {

        private static final Instant NOW = Instant.parse("2026-07-18T20:00:00Z");

        @Override
        public NewsFetchResult getRecentNews(String ticker, int limit) {
            if (ticker.equals("EMPTY")) {
                return new NewsFetchResult(List.of(), 0, "YAHOO_FINANCE", NOW);
            }
            if (ticker.equals("PART")) {
                return new NewsFetchResult(List.of(
                        article(ticker, "PART valid article", "https://example.com/valid"),
                        article(ticker, "PART unsafe article", "javascript:alert(1)")),
                        0, "YAHOO_FINANCE", NOW);
            }
            if (ticker.equals("FILTER")) {
                return new NewsFetchResult(List.of(
                        article(
                                ticker,
                                "Tesla expands vehicle production",
                                "https://example.com/tesla"),
                        articleWithoutSymbols(
                                "Oil prices move higher",
                                "https://example.com/oil",
                                NOW.plusSeconds(500)),
                        article(
                                ticker,
                                "FILTER launches product one",
                                "https://example.com/one",
                                NOW),
                        article(
                                ticker,
                                "FILTER launches product two",
                                "https://example.com/two",
                                NOW.plusSeconds(100)),
                        article(
                                ticker,
                                "FILTER launches product three",
                                "https://example.com/three",
                                NOW.plusSeconds(200))),
                        0, "YAHOO_FINANCE", NOW);
            }
            return new NewsFetchResult(List.of(article(
                    ticker,
                    "AAPL and MSFT shared story",
                    ticker.equals("AAPL")
                            ? " HTTPS://Example.COM/shared-story?edition=us#apple "
                            : "https://example.com/shared-story?edition=us#microsoft")),
                    0, "YAHOO_FINANCE", NOW);
        }

        private NewsArticleData article(String ticker, String headline, String url) {
            return article(ticker, headline, url, NOW);
        }

        private NewsArticleData article(
                String ticker, String headline, String url, Instant publishedAt) {
            return new NewsArticleData(
                    null,
                    headline,
                    "Example Wire",
                    url,
                    null,
                    publishedAt,
                    Set.of(ticker),
                    NOW,
                    "YAHOO_FINANCE");
        }

        private NewsArticleData articleWithoutSymbols(
                String headline, String url, Instant publishedAt) {
            return new NewsArticleData(
                    null,
                    headline,
                    "Example Wire",
                    url,
                    null,
                    publishedAt,
                    Set.of(),
                    NOW,
                    "YAHOO_FINANCE");
        }
    }
}
