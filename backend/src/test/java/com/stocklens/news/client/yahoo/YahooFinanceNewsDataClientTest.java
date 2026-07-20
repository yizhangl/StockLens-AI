package com.stocklens.news.client.yahoo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class YahooFinanceNewsDataClientTest {

    private static final String BASE_URL = "https://yahoo-news.example";
    private static final String NEWS_URL = BASE_URL
            + "/xhr/ncp?queryRef=latestNews&serviceKey=ncp_fin";

    private YahooFinanceNewsProperties properties;
    private MockRestServiceServer server;
    private YahooFinanceNewsDataClient client;

    @BeforeEach
    void setUp() {
        properties = new YahooFinanceNewsProperties();
        properties.setBaseUrl(URI.create(BASE_URL));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        YahooFinanceNewsClientConfiguration.USER_AGENT);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YahooFinanceNewsDataClient(
                builder.build(),
                properties,
                new YahooFinanceNewsResponseMapper(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-19T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sendsVerifiedPostContractUsesConfiguredBaseUrlAndMapsStories() {
        server.expect(once(), requestTo(NEWS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(
                        HttpHeaders.USER_AGENT,
                        YahooFinanceNewsClientConfiguration.USER_AGENT))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header(HttpHeaders.CONTENT_LENGTH, "50"))
                .andExpect(content().json("""
                        {"serviceConfig":{"snippetCount":10,"s":["AAPL"]}}
                        """))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/yahoo/news-success.json"),
                        MediaType.APPLICATION_JSON));

        var result = client.getRecentNews("AAPL", 3);

        assertThat(result.providerName()).isEqualTo("YAHOO_FINANCE");
        assertThat(result.articles()).extracting(article -> article.headline())
                .containsExactly(
                        "Apple & partners announce an update",
                        "Apple expands a service");
        assertThat(result.skippedArticleCount()).isEqualTo(2);
        server.verify();
    }

    @Test
    void capsCandidateRequestAtFortyForMaximumPublicLimit() {
        server.expect(once(), requestTo(NEWS_URL))
                .andExpect(content().json("""
                        {"serviceConfig":{"snippetCount":40,"s":["AAPL"]}}
                        """))
                .andRespond(withSuccess(emptyStream(), MediaType.APPLICATION_JSON));

        assertThat(client.getRecentNews("AAPL", 20).articles()).isEmpty();
        server.verify();
    }

    @Test
    void distinguishesValidEmptyStreamFromMissingAndNullStream() {
        expectJson(emptyStream());
        assertThat(client.getRecentNews("AAPL", 3).articles()).isEmpty();
        server.verify();

        setUp();
        expectJson("{\"data\":{\"tickerStream\":{}}}");
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("contract");
        server.verify();

        setUp();
        expectJson("{\"data\":{\"tickerStream\":{\"stream\":null}}}");
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("contract");
        server.verify();
    }

    @Test
    void preservesValidRowsAndRejectsAllInvalidNonEmptyStreams() {
        expectJson("""
                {"data":{"tickerStream":{"stream":[
                  {"id":"valid","content":{"contentType":"STORY","title":"Valid","pubDate":"2026-07-18T18:30:00Z","canonicalUrl":{"url":"https://example.com/valid"}}},
                  {"id":"invalid","content":{"contentType":"STORY","title":"Invalid","pubDate":"bad","canonicalUrl":{"url":"https://example.com/invalid"}}}
                ]}}}
                """);
        var partial = client.getRecentNews("AAPL", 3);
        assertThat(partial.articles()).hasSize(1);
        assertThat(partial.skippedArticleCount()).isEqualTo(1);
        server.verify();

        setUp();
        expectJson("""
                {"data":{"tickerStream":{"stream":[
                  {"id":"invalid","content":{"contentType":"STORY","title":"Missing date","canonicalUrl":{"url":"https://example.com/invalid"}}}
                ]}}}
                """);
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("valid article");
        server.verify();
    }

    @Test
    void rejectsMalformedJsonAndHtmlWithoutRetrying() {
        expectJson("{not-json");
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("malformed JSON");
        server.verify();

        setUp();
        server.expect(once(), requestTo(NEWS_URL))
                .andRespond(withSuccess("<html>challenge</html>", MediaType.TEXT_HTML));
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("content type");
        server.verify();
    }

    @Test
    void mapsAllNonRateLimitedClientStatusesToProviderErrorWithoutRetry() {
        for (HttpStatus status : new HttpStatus[] {
                HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND
        }) {
            setUp();
            server.expect(once(), requestTo(NEWS_URL)).andRespond(withStatus(status));
            assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                    .isInstanceOf(NewsProviderException.class)
                    .isNotInstanceOf(NewsProviderRateLimitedException.class)
                    .hasMessageContaining("rejected");
            server.verify();
        }
    }

    @Test
    void mapsRateLimitAndUnexpectedRedirectWithoutRetrying() {
        server.expect(once(), requestTo(NEWS_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "45"));
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOfSatisfying(
                        NewsProviderRateLimitedException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(45));
        server.verify();

        setUp();
        server.expect(once(), requestTo(NEWS_URL))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "https://example.com/challenge"));
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("redirect");
        server.verify();
    }

    @Test
    void retriesServerFailureTimeoutAndConnectionFailureOnlyWithinBound() {
        server.expect(once(), requestTo(NEWS_URL)).andRespond(withServerError());
        server.expect(once(), requestTo(NEWS_URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();

        assertBoundedResourceFailure("simulated timeout");
        assertBoundedResourceFailure("simulated connection reset");
    }

    private void assertBoundedResourceFailure(String message) {
        setUp();
        server.expect(once(), requestTo(NEWS_URL)).andRespond(request -> {
            throw new ResourceAccessException(message);
        });
        server.expect(once(), requestTo(NEWS_URL)).andRespond(request -> {
            throw new ResourceAccessException(message);
        });
        assertThatThrownBy(() -> client.getRecentNews("AAPL", 3))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();
    }

    private void expectJson(String body) {
        server.expect(once(), requestTo(NEWS_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String emptyStream() {
        return "{\"data\":{\"tickerStream\":{\"stream\":[]}}}";
    }
}
