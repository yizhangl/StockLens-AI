package com.stocklens.news.client.fmp;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.market.client.fmp.FmpProperties;
import com.stocklens.news.client.NewsDataClient;
import com.stocklens.news.client.fmp.dto.FmpNewsResponse;
import com.stocklens.news.client.model.NewsFetchResult;
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
public class FmpNewsDataClient implements NewsDataClient {

    private static final ParameterizedTypeReference<List<FmpNewsResponse>> NEWS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final FmpProperties properties;
    private final FmpNewsResponseMapper mapper;
    private final Clock clock;

    public FmpNewsDataClient(
            @Qualifier("fmpRestClient") RestClient restClient,
            FmpProperties properties,
            FmpNewsResponseMapper mapper,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public NewsFetchResult getRecentNews(String ticker, int limit) {
        requireApiKey();
        RuntimeException lastTransientFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                List<FmpNewsResponse> body = restClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/news/stock")
                                .queryParam("symbols", ticker)
                                .queryParam("page", 0)
                                .queryParam("limit", limit)
                                .queryParam("apikey", properties.getApiKey())
                                .build())
                        .retrieve()
                        .onStatus(this::isAuthenticationFailure, (request, response) -> {
                            throw new NewsProviderException("News provider authentication failed.");
                        })
                        .onStatus(status -> status.value() == 404, (request, response) -> {
                            throw new StockNotFoundException(ticker);
                        })
                        .onStatus(status -> status.value() == 429, (request, response) -> {
                            throw new NewsProviderRateLimitedException(
                                    retryAfterSeconds(response.getHeaders().getFirst("Retry-After")));
                        })
                        .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                            throw new NewsProviderException("News provider rejected the request.");
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                            throw new TransientFmpNewsException();
                        })
                        .body(NEWS_TYPE);
                return mapper.toNews(
                        body == null ? List.of() : body, ticker, Instant.now(clock));
            } catch (TransientFmpNewsException | ResourceAccessException exception) {
                lastTransientFailure = exception;
                if (attempt == properties.getMaxAttempts()) {
                    break;
                }
            } catch (RestClientException exception) {
                throw new NewsProviderException(
                        "News provider returned an unreadable response.", exception);
            }
        }
        throw new NewsProviderException(
                "News provider is temporarily unavailable.", lastTransientFailure);
    }

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new DataUnavailableException("News provider is not configured.");
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

    private static final class TransientFmpNewsException extends RuntimeException {}
}
