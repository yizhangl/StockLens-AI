package com.stocklens.market.client.fmp;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.fmp.dto.FmpCompanyProfileResponse;
import com.stocklens.market.client.fmp.dto.FmpFinancialGrowthResponse;
import com.stocklens.market.client.fmp.dto.FmpHistoricalPriceResponse;
import com.stocklens.market.client.fmp.dto.FmpKeyMetricsTtmResponse;
import com.stocklens.market.client.fmp.dto.FmpQuoteResponse;
import com.stocklens.market.client.fmp.dto.FmpRatiosTtmResponse;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.market.client.model.MarketSnapshotData;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FmpFinancialDataClient implements FinancialDataClient {

    private static final ParameterizedTypeReference<List<FmpCompanyProfileResponse>> PROFILE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FmpQuoteResponse>> QUOTE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FmpKeyMetricsTtmResponse>> KEY_METRICS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FmpRatiosTtmResponse>> RATIOS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FmpFinancialGrowthResponse>> GROWTH_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FmpHistoricalPriceResponse>> HISTORY_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final FmpProperties properties;
    private final FmpResponseMapper mapper;
    private final Clock clock;

    public FmpFinancialDataClient(
            @Qualifier("fmpRestClient") RestClient restClient,
            FmpProperties properties,
            FmpResponseMapper mapper,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public CompanyProfileData getCompanyProfile(String ticker) {
        List<FmpCompanyProfileResponse> responses = get("/profile", ticker, PROFILE_TYPE);
        FmpCompanyProfileResponse response = firstOrNotFound(responses, ticker);
        return mapper.toCompanyProfile(response, ticker, Instant.now(clock));
    }

    @Override
    public MarketSnapshotData getMarketSnapshot(String ticker) {
        List<FmpQuoteResponse> responses = get("/quote", ticker, QUOTE_TYPE);
        FmpQuoteResponse response = firstOrNotFound(responses, ticker);
        return mapper.toMarketSnapshot(response, ticker, Instant.now(clock));
    }

    @Override
    public FinancialMetricsData getFinancialMetrics(String ticker) {
        Instant retrievedAt = Instant.now(clock);
        List<FmpRatiosTtmResponse> ratios = get("/ratios-ttm", ticker, Map.of(), RATIOS_TYPE);
        if (ratios.isEmpty()) {
            throw new DataUnavailableException("Financial ratios are unavailable for " + ticker + ".");
        }
        List<FmpKeyMetricsTtmResponse> keys = get("/key-metrics-ttm", ticker, Map.of(), KEY_METRICS_TYPE);
        Map<String, Object> growthParameters = new LinkedHashMap<>();
        growthParameters.put("period", "annual");
        growthParameters.put("limit", 1);
        List<FmpFinancialGrowthResponse> growth = get(
                "/financial-growth", ticker, growthParameters, GROWTH_TYPE);
        return mapper.toFinancialMetrics(
                ratios.getFirst(),
                keys.isEmpty() ? null : keys.getFirst(),
                growth.isEmpty() ? null : growth.getFirst(),
                ticker,
                retrievedAt);
    }

    @Override
    public List<HistoricalPriceData> getHistoricalPrices(
            String ticker, LocalDate from, LocalDate to) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (from != null) {
            parameters.put("from", from);
        }
        parameters.put("to", to);
        List<FmpHistoricalPriceResponse> responses = get(
                "/historical-price-eod/full", ticker, parameters, HISTORY_TYPE);
        return mapper.toHistoricalPrices(responses, ticker, from, to, Instant.now(clock));
    }

    private <T> List<T> get(
            String path, String ticker, ParameterizedTypeReference<List<T>> responseType) {
        return get(path, ticker, Map.of(), responseType);
    }

    private <T> List<T> get(
            String path,
            String ticker,
            Map<String, Object> parameters,
            ParameterizedTypeReference<List<T>> responseType) {
        requireApiKey();
        RuntimeException lastTransientFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                List<T> body = restClient
                        .get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(path).queryParam("symbol", ticker);
                            parameters.forEach(uriBuilder::queryParam);
                            return uriBuilder.queryParam("apikey", properties.getApiKey()).build();
                        })
                        .retrieve()
                        .onStatus(this::isAuthenticationFailure, (request, response) -> {
                            throw new FinancialProviderException("Financial provider authentication failed.");
                        })
                        .onStatus(status -> status.value() == 404, (request, response) -> {
                            throw new StockNotFoundException(ticker);
                        })
                        .onStatus(status -> status.value() == 429, (request, response) -> {
                            throw new FinancialProviderRateLimitedException(
                                    retryAfterSeconds(response.getHeaders().getFirst("Retry-After")));
                        })
                        .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                            throw new FinancialProviderException("Financial provider rejected the request.");
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                            throw new TransientFmpException();
                        })
                        .body(responseType);
                return body == null ? List.of() : body;
            } catch (TransientFmpException | ResourceAccessException exception) {
                lastTransientFailure = exception;
                if (attempt == properties.getMaxAttempts()) {
                    break;
                }
            } catch (RestClientException exception) {
                throw new FinancialProviderException(
                        "Financial provider returned an unreadable response.", exception);
            }
        }
        throw new FinancialProviderException(
                "Financial provider is temporarily unavailable.", lastTransientFailure);
    }

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new DataUnavailableException("Financial provider is not configured.");
        }
    }

    private boolean isAuthenticationFailure(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }

    private Long retryAfterSeconds(String value) {
        if (value == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value);
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> T firstOrNotFound(List<T> responses, String ticker) {
        if (responses == null || responses.isEmpty()) {
            throw new StockNotFoundException(ticker);
        }
        return responses.getFirst();
    }

    private static final class TransientFmpException extends RuntimeException {}
}
