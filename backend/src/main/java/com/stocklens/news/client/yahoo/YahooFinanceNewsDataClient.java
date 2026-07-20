package com.stocklens.news.client.yahoo;

import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import com.stocklens.news.client.NewsDataClient;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsRequest;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class YahooFinanceNewsDataClient implements NewsDataClient {

    private static final int MIN_CANDIDATE_COUNT = 10;
    private static final int MAX_CANDIDATE_COUNT = 40;
    private static final int MAX_RESPONSE_CHARACTERS = 1_000_000;

    private final RestClient restClient;
    private final YahooFinanceNewsProperties properties;
    private final YahooFinanceNewsResponseMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public YahooFinanceNewsDataClient(
            @Qualifier("yahooFinanceNewsRestClient") RestClient restClient,
            YahooFinanceNewsProperties properties,
            YahooFinanceNewsResponseMapper mapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public NewsFetchResult getRecentNews(String ticker, int limit) {
        int candidateCount = candidateCount(limit);
        YahooFinanceNewsRequest request = YahooFinanceNewsRequest.forTicker(
                ticker, candidateCount);
        byte[] requestBody = serializeRequest(request);
        RuntimeException lastTransientFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                ResponseEntity<String> response = restClient
                        .post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/xhr/ncp")
                                .queryParam("queryRef", "latestNews")
                                .queryParam("serviceKey", "ncp_fin")
                                .build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .onStatus(HttpStatusCode::is3xxRedirection, (httpRequest, httpResponse) -> {
                            throw new NewsProviderException(
                                    "Yahoo Finance news returned an unexpected redirect.");
                        })
                        .onStatus(status -> status.value() == 429, (httpRequest, httpResponse) -> {
                            throw new NewsProviderRateLimitedException(
                                    retryAfterSeconds(
                                            httpResponse.getHeaders().getFirst("Retry-After")));
                        })
                        .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, httpResponse) -> {
                            throw new NewsProviderException(
                                    "Yahoo Finance news rejected the request.");
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, httpResponse) -> {
                            throw new TransientYahooNewsException(
                                    httpResponse.getStatusCode().value());
                        })
                        .toEntity(String.class);
                YahooFinanceNewsResponse body = parseJsonResponse(response);
                return mapper.toNews(body, ticker, Instant.now(clock));
            } catch (TransientYahooNewsException | ResourceAccessException exception) {
                lastTransientFailure = exception;
                if (attempt == properties.getMaxAttempts()) {
                    break;
                }
            } catch (RestClientException exception) {
                throw new NewsProviderException(
                        "Yahoo Finance news returned an unreadable response.", exception);
            }
        }
        throw new NewsProviderException(
                "Yahoo Finance news is temporarily unavailable.", lastTransientFailure);
    }

    private YahooFinanceNewsResponse parseJsonResponse(ResponseEntity<String> response) {
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new NewsProviderException(
                    "Yahoo Finance news returned an unexpected content type.");
        }
        String body = response.getBody();
        if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_CHARACTERS) {
            throw new NewsProviderException("Yahoo Finance news returned an unusable response.");
        }
        try {
            return objectMapper.readValue(body, YahooFinanceNewsResponse.class);
        } catch (JacksonException exception) {
            throw new NewsProviderException(
                    "Yahoo Finance news returned malformed JSON.", exception);
        }
    }

    private byte[] serializeRequest(YahooFinanceNewsRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JacksonException exception) {
            throw new NewsProviderException(
                    "Yahoo Finance news request could not be serialized.", exception);
        }
    }

    private int candidateCount(int limit) {
        return Math.min(MAX_CANDIDATE_COUNT, Math.max(MIN_CANDIDATE_COUNT, limit * 2));
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

    private static final class TransientYahooNewsException extends RuntimeException {

        private TransientYahooNewsException(int statusCode) {
            super("Yahoo Finance news returned HTTP " + statusCode + ".");
        }
    }
}
