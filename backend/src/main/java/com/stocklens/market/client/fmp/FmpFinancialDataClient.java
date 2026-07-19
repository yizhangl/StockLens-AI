package com.stocklens.market.client.fmp;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.fmp.dto.FmpCompanyProfileResponse;
import com.stocklens.market.client.fmp.dto.FmpQuoteResponse;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    private <T> List<T> get(
            String path, String ticker, ParameterizedTypeReference<List<T>> responseType) {
        requireApiKey();
        RuntimeException lastTransientFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                List<T> body = restClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path(path)
                                .queryParam("symbol", ticker)
                                .queryParam("apikey", properties.getApiKey())
                                .build())
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
