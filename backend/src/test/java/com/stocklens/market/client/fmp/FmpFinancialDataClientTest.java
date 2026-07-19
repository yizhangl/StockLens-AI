package com.stocklens.market.client.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class FmpFinancialDataClientTest {

    private FmpProperties properties;
    private MockRestServiceServer server;
    private FmpFinancialDataClient client;

    @BeforeEach
    void setUp() {
        properties = new FmpProperties();
        properties.setApiKey("test-api-key");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://financialmodelingprep.com/stable");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FmpFinancialDataClient(
                builder.build(),
                properties,
                new FmpResponseMapper(),
                Clock.fixed(Instant.parse("2026-07-18T20:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void mapsSuccessfulProfileAndQuoteFixtures() {
        server.expect(once(), requestTo(
                        "https://financialmodelingprep.com/stable/profile?symbol=AAPL&apikey=test-api-key"))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/fmp/company-profile-success.json"),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=test-api-key"))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/fmp/quote-success.json"),
                        MediaType.APPLICATION_JSON));

        var profile = client.getCompanyProfile("AAPL");
        var quote = client.getMarketSnapshot("AAPL");

        assertThat(profile.name()).isEqualTo("Apple Inc.");
        assertThat(profile.currency()).isEqualTo("USD");
        assertThat(quote.price()).isEqualByComparingTo("268.47000000");
        assertThat(quote.marketCap()).isEqualByComparingTo("3967020108000.00");
        server.verify();
    }

    @Test
    void treatsEmptyArrayAsUnknownTicker() {
        expectProfile(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessageContaining("AAPL");
        server.verify();
    }

    @Test
    void translatesAuthenticationAndRateLimitWithoutRetrying() {
        expectProfile(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(FinancialProviderException.class)
                .hasMessageContaining("authentication");
        server.verify();

        setUp();
        expectProfile(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30"));
        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOfSatisfying(
                        FinancialProviderRateLimitedException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(30));
        server.verify();
    }

    @Test
    void retriesServerFailureOnlyUpToConfiguredAttempts() {
        server.expect(once(), requestTo(profileUrl())).andRespond(withServerError());
        server.expect(once(), requestTo(profileUrl())).andRespond(withServerError());

        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(FinancialProviderException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();
    }

    @Test
    void translatesTimeoutAfterBoundedRetry() {
        server.expect(once(), requestTo(profileUrl()))
                .andRespond(request -> {
                    throw new ResourceAccessException("simulated timeout");
                });
        server.expect(once(), requestTo(profileUrl()))
                .andRespond(request -> {
                    throw new ResourceAccessException("simulated timeout");
                });

        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(FinancialProviderException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();
    }

    @Test
    void rejectsMalformedJsonWithoutRetrying() {
        expectProfile(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(FinancialProviderException.class)
                .hasMessageContaining("unreadable response");
        server.verify();
    }

    @Test
    void doesNotCallHttpWhenApiKeyIsMissing() {
        properties.setApiKey(" ");

        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("not configured");
        server.verify();
    }

    @Test
    void translatesNotFoundStatus() {
        expectProfile(withResourceNotFound());

        assertThatThrownBy(() -> client.getCompanyProfile("AAPL"))
                .isInstanceOf(StockNotFoundException.class);
        server.verify();
    }

    @Test
    void loadsAndNormalizesMetricsFromDocumentedStableEndpoints() {
        expectJson("/ratios-ttm?symbol=AAPL&apikey=test-api-key", "ratios-ttm-success.json");
        expectJson("/key-metrics-ttm?symbol=AAPL&apikey=test-api-key", "key-metrics-ttm-success.json");
        expectJson(
                "/financial-growth?symbol=AAPL&period=annual&limit=1&apikey=test-api-key",
                "financial-growth-success.json");

        var result = client.getFinancialMetrics("AAPL");

        assertThat(result.peTtm()).isEqualByComparingTo("31.2");
        assertThat(result.revenueGrowth()).isEqualByComparingTo("0.063");
        assertThat(result.providerName()).isEqualTo("FMP");
        server.verify();
    }

    @Test
    void treatsMissingRatiosAsControlledUnavailableData() {
        server.expect(once(), requestTo(
                        "https://financialmodelingprep.com/stable/ratios-ttm?symbol=AAPL&apikey=test-api-key"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getFinancialMetrics("AAPL"))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("ratios");
        server.verify();
    }

    @Test
    void loadsHistoricalPricesWithExplicitBounds() {
        expectJson(
                "/historical-price-eod/full?symbol=AAPL&from=2026-07-01&to=2026-07-18&apikey=test-api-key",
                "history-success.json");

        var result = client.getHistoricalPrices(
                "AAPL", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-18"));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().tradingDate()).isEqualTo(LocalDate.parse("2026-07-17"));
        server.verify();
    }

    private void expectProfile(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(once(), requestTo(profileUrl())).andRespond(response);
    }

    private String profileUrl() {
        return "https://financialmodelingprep.com/stable/profile?symbol=AAPL&apikey=test-api-key";
    }

    private void expectJson(String pathAndQuery, String fixture) {
        server.expect(once(), requestTo("https://financialmodelingprep.com/stable" + pathAndQuery))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/fmp/" + fixture), MediaType.APPLICATION_JSON));
    }
}
