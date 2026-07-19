package com.stocklens.company.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.FinancialProviderRateLimitedException;
import com.stocklens.common.exception.InvalidTickerException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.time.TimeConfiguration;
import com.stocklens.common.web.GlobalExceptionHandler;
import com.stocklens.common.web.RequestIdFilter;
import com.stocklens.company.domain.Company;
import com.stocklens.company.dto.StockResponseMapper;
import com.stocklens.company.service.StockQueryService;
import com.stocklens.company.service.StockQueryService.StockResult;
import com.stocklens.market.domain.MarketSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
@Import({StockResponseMapper.class, GlobalExceptionHandler.class, RequestIdFilter.class, TimeConfiguration.class})
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockQueryService stockQueryService;

    @Test
    void returnsNormalizedApplicationContractWithoutInternalFields() throws Exception {
        when(stockQueryService.getStock("AAPL")).thenReturn(result());

        mockMvc.perform(get("/api/v1/stocks/AAPL").header("X-Request-ID", "request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "request-123"))
                .andExpect(jsonPath("$.company.ticker").value("AAPL"))
                .andExpect(jsonPath("$.company.name").value("Apple Inc."))
                .andExpect(jsonPath("$.latestMarketSnapshot.price").value(268.47))
                .andExpect(jsonPath("$.latestMarketSnapshot.currency").value("USD"))
                .andExpect(jsonPath("$.latestMarketSnapshot.providerName").value("FMP"))
                .andExpect(jsonPath("$.company.id").doesNotExist())
                .andExpect(jsonPath("$.company.providerSymbol").doesNotExist())
                .andExpect(jsonPath("$.latestMarketSnapshot.rawDataJson").doesNotExist());
    }

    @Test
    void generatesRequestIdAndReturnsConsistentInvalidTickerError() throws Exception {
        when(stockQueryService.getStock(anyString())).thenThrow(new InvalidTickerException());

        mockMvc.perform(get("/api/v1/stocks/BAD_SYMBOL"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-ID", matchesPattern("^[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.code").value("INVALID_TICKER"))
                .andExpect(jsonPath("$.message").value("Ticker must match ^[A-Z][A-Z0-9.-]{0,9}$."))
                .andExpect(jsonPath("$.path").value("/api/v1/stocks/BAD_SYMBOL"))
                .andExpect(jsonPath("$.requestId", matchesPattern("^[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void mapsKnownApplicationFailuresToSafeStatuses() throws Exception {
        assertError(new StockNotFoundException("UNKNOWN"), 404, "STOCK_NOT_FOUND");
        assertError(new FinancialProviderRateLimitedException(30L), 429, "RATE_LIMITED");
        assertError(new FinancialProviderException("secret provider body"), 502, "FINANCIAL_PROVIDER_ERROR");
        assertError(new DataUnavailableException("Financial provider is not configured."), 503, "DATA_UNAVAILABLE");
        assertError(new IllegalStateException("internal secret"), 500, "INTERNAL_ERROR");
    }

    private void assertError(RuntimeException exception, int status, String code) throws Exception {
        doThrow(exception).when(stockQueryService).getStock(anyString());
        mockMvc.perform(get("/api/v1/stocks/AAPL"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    private StockResult result() {
        Instant retrievedAt = Instant.parse("2026-07-18T20:00:00Z");
        Company company = new Company(
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                "Technology",
                "Consumer Electronics",
                "US",
                "https://www.apple.com",
                "Description",
                null,
                "AAPL",
                retrievedAt,
                retrievedAt);
        MarketSnapshot snapshot = new MarketSnapshot(
                company,
                new BigDecimal("268.47000000"),
                new BigDecimal("-1.30000000"),
                new BigDecimal("-0.481890"),
                new BigDecimal("3967020108000.00"),
                "USD",
                Instant.parse("2026-07-18T19:00:00Z"),
                retrievedAt,
                "FMP",
                null);
        return new StockResult(company, snapshot);
    }
}
