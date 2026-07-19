package com.stocklens.news.client.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.market.client.fmp.FmpProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class FmpNewsDataClientTest {

    private FmpProperties properties;
    private MockRestServiceServer server;
    private FmpNewsDataClient client;

    @BeforeEach
    void setUp() {
        properties = new FmpProperties();
        properties.setApiKey("test-api-key");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://financialmodelingprep.com/stable");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FmpNewsDataClient(
                builder.build(),
                properties,
                new FmpNewsResponseMapper(),
                Clock.fixed(Instant.parse("2026-07-18T20:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void loadsVerifiedListResponseWithDocumentedPageAndLimitParameters() {
        server.expect(once(), requestTo(newsUrl(2)))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/fmp/news-success.json"),
                        MediaType.APPLICATION_JSON));

        var result = client.getRecentNews("AAPL", 2);

        assertThat(result.articles()).hasSize(2);
        assertThat(result.articles()).extracting(article -> article.headline())
                .containsExactly("Apple announces a product update", "Apple expands a service");
        assertThat(result.providerName()).isEqualTo("FMP");
        server.verify();
    }

    @Test
    void acceptsValidEmptyList() {
        expectNews(3, withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.getRecentNews("AAPL", 3).articles()).isEmpty();
        server.verify();
    }

    @Test
    void preservesPartialValidRowsAndRejectsAllInvalidRows() {
        expectNews(3, withSuccess("""
                [
                  {"symbol":"AAPL","publishedDate":"2026-07-18 12:00:00","publisher":"Wire","title":"Valid","text":null,"url":"https://example.com/valid"},
                  {"symbol":"AAPL","publishedDate":"bad","publisher":"Wire","title":"Invalid","text":null,"url":"https://example.com/invalid"}
                ]
                """, MediaType.APPLICATION_JSON));

        var partial = client.getRecentNews("AAPL", 3);
        assertThat(partial.articles()).hasSize(1);
        assertThat(partial.skippedArticleCount()).isEqualTo(1);
        server.verify();

        setUp();
        expectNews(1, withSuccess("""
                [{"symbol":"AAPL","publishedDate":null,"title":"Invalid","url":"https://example.com/invalid"}]
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 1))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("valid article");
        server.verify();
    }

    @Test
    void translatesAuthenticationNotFoundAndRateLimitWithoutRetrying() {
        expectNews(3, withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("authentication");
        server.verify();

        setUp();
        expectNews(3, withResourceNotFound());
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(StockNotFoundException.class);
        server.verify();

        setUp();
        expectNews(3, withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "45"));
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOfSatisfying(
                        NewsProviderRateLimitedException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(45));
        server.verify();
    }

    @Test
    void retriesServerFailureAndTimeoutOnlyUpToConfiguredAttempts() {
        server.expect(once(), requestTo(newsUrl(3))).andRespond(withServerError());
        server.expect(once(), requestTo(newsUrl(3))).andRespond(withServerError());
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();

        setUp();
        server.expect(once(), requestTo(newsUrl(3))).andRespond(request -> {
            throw new ResourceAccessException("simulated timeout");
        });
        server.expect(once(), requestTo(newsUrl(3))).andRespond(request -> {
            throw new ResourceAccessException("simulated timeout");
        });
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();
    }

    @Test
    void rejectsMalformedJsonWithoutRetrying() {
        expectNews(3, withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("unreadable response");
        server.verify();
    }

    @Test
    void missingApiKeyDoesNotCallHttp() {
        properties.setApiKey(" ");

        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("not configured");
        server.verify();
    }

    private void expectNews(int limit, org.springframework.test.web.client.ResponseCreator response) {
        server.expect(once(), requestTo(newsUrl(limit))).andRespond(response);
    }

    private String newsUrl(int limit) {
        return "https://financialmodelingprep.com/stable/news/stock"
                + "?symbols=AAPL&page=0&limit=" + limit + "&apikey=test-api-key";
    }
}
