package com.stocklens.news.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.InvalidNewsLimitException;
import com.stocklens.common.exception.InvalidTickerException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.NewsProviderRateLimitedException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.time.TimeConfiguration;
import com.stocklens.common.web.GlobalExceptionHandler;
import com.stocklens.common.web.RequestIdFilter;
import com.stocklens.news.dto.NewsArticleResponse;
import com.stocklens.news.dto.NewsResponse;
import com.stocklens.news.dto.NewsWarningResponse;
import com.stocklens.news.service.NewsQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NewsController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, TimeConfiguration.class})
class NewsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NewsQueryService newsQueryService;

    @Test
    void returnsPublicNewestFirstContractWithDefaultLimit() throws Exception {
        when(newsQueryService.getRecentNews("AAPL", 10)).thenReturn(response(10));

        mockMvc.perform(get("/api/v1/stocks/AAPL/news").header("X-Request-ID", "news-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "news-123"))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.providerName").value("YAHOO_FINANCE"))
                .andExpect(jsonPath("$.articles[0].id").value(2))
                .andExpect(jsonPath("$.articles[0].headline").value("Newest"))
                .andExpect(jsonPath("$.articles[0].url").value("https://example.com/new"))
                .andExpect(jsonPath("$.articles[0].relatedSymbols[0]").value("AAPL"))
                .andExpect(jsonPath("$.articles[1].headline").value("Older"))
                .andExpect(jsonPath("$.warnings[0].skippedArticleCount").value(1))
                .andExpect(jsonPath("$.articles[0].externalId").doesNotExist())
                .andExpect(jsonPath("$.articles[0].urlHash").doesNotExist())
                .andExpect(jsonPath("$.articles[0].companies").doesNotExist())
                .andExpect(jsonPath("$.rawDataJson").doesNotExist());
        verify(newsQueryService).getRecentNews("AAPL", 10);
    }

    @Test
    void delegatesExplicitLimitAndSupportsEmptyResponse() throws Exception {
        when(newsQueryService.getRecentNews("AAPL", 3)).thenReturn(new NewsResponse(
                "AAPL", 3, "YAHOO_FINANCE", Instant.parse("2026-07-18T20:00:00Z"),
                List.of(), List.of()));

        mockMvc.perform(get("/api/v1/stocks/AAPL/news?limit=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.articles").isEmpty())
                .andExpect(jsonPath("$.warnings").isEmpty());
        verify(newsQueryService).getRecentNews("AAPL", 3);
    }

    @Test
    void mapsLowHighAndNonNumericLimitsToControlledBadRequest() throws Exception {
        when(newsQueryService.getRecentNews(anyString(), anyInt()))
                .thenThrow(new InvalidNewsLimitException());

        mockMvc.perform(get("/api/v1/stocks/AAPL/news?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"))
                .andExpect(jsonPath("$.message").value("News limit must be between 1 and 20."));
        mockMvc.perform(get("/api/v1/stocks/AAPL/news?limit=21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        mockMvc.perform(get("/api/v1/stocks/AAPL/news?limit=many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void mapsTickerNotFoundConfigurationProviderAndRateErrorsSafely() throws Exception {
        assertError(new InvalidTickerException(), 400, "INVALID_TICKER");
        assertError(new StockNotFoundException("UNKNOWN"), 404, "STOCK_NOT_FOUND");
        assertError(new DataUnavailableException("News provider is not configured."),
                503, "DATA_UNAVAILABLE");
        assertError(new NewsProviderException("secret provider body"),
                502, "NEWS_PROVIDER_ERROR");

        doThrow(new NewsProviderRateLimitedException(30L))
                .when(newsQueryService).getRecentNews(anyString(), anyInt());
        mockMvc.perform(get("/api/v1/stocks/AAPL/news"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Recent news is temporarily rate limited."));
    }

    private void assertError(RuntimeException exception, int expectedStatus, String code)
            throws Exception {
        doThrow(exception).when(newsQueryService).getRecentNews(anyString(), anyInt());
        mockMvc.perform(get("/api/v1/stocks/AAPL/news"))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("X-Request-ID", matchesPattern("^[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.path").value("/api/v1/stocks/AAPL/news"))
                .andExpect(jsonPath("$.requestId", matchesPattern("^[0-9a-f-]{36}$")));
    }

    private NewsResponse response(int limit) {
        return new NewsResponse(
                "AAPL",
                limit,
                "YAHOO_FINANCE",
                Instant.parse("2026-07-18T20:00:00Z"),
                List.of(
                        new NewsArticleResponse(
                                2L, "Newest", "Wire", "https://example.com/new",
                                Instant.parse("2026-07-18T18:00:00Z"), "New description",
                                List.of("AAPL")),
                        new NewsArticleResponse(
                                1L, "Older", "Wire", "https://example.com/old",
                                Instant.parse("2026-07-17T18:00:00Z"), null,
                                List.of("AAPL"))),
                List.of(new NewsWarningResponse(
                        "INVALID_PROVIDER_RECORDS_SKIPPED",
                        "Some news records were unavailable because the provider data was invalid.",
                        1)));
    }
}
