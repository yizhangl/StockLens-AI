package com.stocklens.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.news.client.NewsDataClient;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.support.IntegrationTestContainers;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({IntegrationTestContainers.class, ComparisonApiIntegrationTest.FakeProviderConfiguration.class})
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ComparisonApiIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM news_article_company");
        jdbcTemplate.update("DELETE FROM news_article");
        jdbcTemplate.update("DELETE FROM historical_price");
        jdbcTemplate.update("DELETE FROM financial_metric_snapshot");
        jdbcTemplate.update("DELETE FROM market_snapshot");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void assemblesDefaultComparisonThroughHttpWithFakeProviderBoundaries() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/v1/comparisons?left=AAPL&right=MSFT"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = new ObjectMapper().readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.get("comparisonId").asText()).isEqualTo("AAPL:MSFT:1Y:RETURN");
        assertThat(json.get("left").get("ticker").asText()).isEqualTo("AAPL");
        assertThat(json.get("right").get("ticker").asText()).isEqualTo("MSFT");
        assertThat(json.get("pricePerformance").get("mode").asText()).isEqualTo("RETURN");
        assertThat(json.get("pricePerformance").get("pointCount").asInt()).isEqualTo(2);
        assertThat(json.get("pricePerformance").get("series").get(0).get("leftValue").decimalValue())
                .isEqualByComparingTo("0.0000");
        assertThat(json.get("metricGroups").size()).isEqualTo(4);
        assertThat(json.get("news").get("left").size()).isEqualTo(3);
        assertThat(json.get("news").get("right").size()).isEqualTo(3);
        assertThat(json.get("aiBrief").isNull()).isTrue();
        assertThat(json.get("provenance").get("financialProvider").asText()).isEqualTo("FMP");
        assertThat(json.get("provenance").get("newsProvider").asText())
                .isEqualTo("YAHOO_FINANCE");
        assertThat(json.get("provenance").get("cached").asBoolean()).isFalse();
        assertThat(json.get("warnings").isEmpty()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM company", Long.class))
                .isEqualTo(2);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {

        @Bean
        @Primary
        FinancialDataClient comparisonFinancialDataClient() {
            return new FakeFinancialDataClient();
        }

        @Bean
        @Primary
        NewsDataClient comparisonNewsDataClient() {
            return new FakeNewsDataClient();
        }
    }

    static final class FakeFinancialDataClient implements FinancialDataClient {

        private static final Instant NOW = Instant.parse("2026-07-19T20:00:00Z");

        @Override
        public CompanyProfileData getCompanyProfile(String ticker) {
            String name = ticker.equals("AAPL") ? "Apple Inc." : "Microsoft Corporation";
            return new CompanyProfileData(
                    ticker, name, "NASDAQ", "Technology", "Software", "US",
                    "https://example.com/" + ticker, "Description", null, ticker, "USD", NOW);
        }

        @Override
        public MarketSnapshotData getMarketSnapshot(String ticker) {
            return new MarketSnapshotData(
                    ticker,
                    ticker.equals("AAPL") ? new BigDecimal("200") : new BigDecimal("400"),
                    BigDecimal.ONE,
                    new BigDecimal("0.5"),
                    new BigDecimal("1000000"),
                    "USD",
                    NOW.minusSeconds(1),
                    NOW,
                    "FMP");
        }

        @Override
        public FinancialMetricsData getFinancialMetrics(String ticker) {
            return new FinancialMetricsData(
                    ticker,
                    ticker.equals("AAPL") ? new BigDecimal("20") : new BigDecimal("30"),
                    null,
                    new BigDecimal("2"),
                    new BigDecimal("5"),
                    new BigDecimal("1000000"),
                    new BigDecimal("0.4"),
                    new BigDecimal("0.2"),
                    new BigDecimal("0.3"),
                    new BigDecimal("0.1"),
                    new BigDecimal("0.1"),
                    new BigDecimal("0.5"),
                    new BigDecimal("1.5"),
                    new BigDecimal("1.0"),
                    "USD",
                    LocalDate.parse("2025-12-31"),
                    NOW,
                    "FMP");
        }

        @Override
        public List<HistoricalPriceData> getHistoricalPrices(
                String ticker, LocalDate from, LocalDate to) {
            BigDecimal start = ticker.equals("AAPL")
                    ? new BigDecimal("100") : new BigDecimal("200");
            return List.of(
                    price(ticker, to.minusDays(1), start),
                    price(ticker, to, start.multiply(new BigDecimal("1.1"))));
        }

        private HistoricalPriceData price(String ticker, LocalDate date, BigDecimal close) {
            return new HistoricalPriceData(
                    ticker, date, null, null, null, close, null, 1000L,
                    "USD", "FMP", NOW);
        }
    }

    static final class FakeNewsDataClient implements NewsDataClient {

        private static final Instant NOW = Instant.parse("2026-07-19T20:00:00Z");

        @Override
        public NewsFetchResult getRecentNews(String ticker, int limit) {
            String alias = ticker.equals("AAPL") ? "Apple" : "Microsoft";
            return new NewsFetchResult(
                    List.of(
                            article(ticker, alias, 1),
                            article(ticker, alias, 2),
                            article(ticker, alias, 3),
                            article(ticker, alias, 4)),
                    0,
                    "YAHOO_FINANCE",
                    NOW);
        }

        private NewsArticleData article(String ticker, String alias, long sequence) {
            return new NewsArticleData(
                    ticker + "-" + sequence,
                    alias + " company update " + sequence,
                    "Example Wire",
                    "https://example.com/" + ticker + "/" + sequence,
                    null,
                    NOW.plusSeconds(sequence),
                    Set.of(),
                    NOW,
                    "YAHOO_FINANCE");
        }
    }
}
