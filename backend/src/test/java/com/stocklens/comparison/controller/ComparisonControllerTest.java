package com.stocklens.comparison.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stocklens.common.exception.DuplicateTickersException;
import com.stocklens.common.exception.InvalidComparisonModeException;
import com.stocklens.common.exception.InvalidPeriodException;
import com.stocklens.common.exception.InvalidTickerException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.time.TimeConfiguration;
import com.stocklens.common.web.GlobalExceptionHandler;
import com.stocklens.common.web.RequestIdFilter;
import com.stocklens.comparison.dto.ComparisonDashboardResponse;
import com.stocklens.comparison.dto.ComparisonNewsResponse;
import com.stocklens.comparison.dto.ComparisonProvenanceResponse;
import com.stocklens.comparison.dto.ComparisonWarningResponse;
import com.stocklens.comparison.dto.CompanySummaryResponse;
import com.stocklens.comparison.dto.PricePerformanceResponse;
import com.stocklens.comparison.model.ComparisonMode;
import com.stocklens.comparison.model.ComparisonWarningSection;
import com.stocklens.comparison.model.ComparisonWarningSide;
import com.stocklens.comparison.service.ComparisonService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ComparisonController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, TimeConfiguration.class})
class ComparisonControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ComparisonService comparisonService;

    @Test
    void returnsCompletePublicContractWithDefaultsAndExplicitNullAi() throws Exception {
        when(comparisonService.compare("AAPL", "MSFT", "1Y", "RETURN"))
                .thenReturn(response(ComparisonMode.RETURN));

        mockMvc.perform(get("/api/v1/comparisons?left=AAPL&right=MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparisonId").value("AAPL:MSFT:1Y:RETURN"))
                .andExpect(jsonPath("$.left.ticker").value("AAPL"))
                .andExpect(jsonPath("$.right.ticker").value("MSFT"))
                .andExpect(jsonPath("$.left.price").value(200))
                .andExpect(jsonPath("$.pricePerformance.period").value("1Y"))
                .andExpect(jsonPath("$.pricePerformance.mode").value("RETURN"))
                .andExpect(jsonPath("$.aiBrief").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.provenance.cached").value(false))
                .andExpect(jsonPath("$.left.id").doesNotExist())
                .andExpect(jsonPath("$.left.providerSymbol").doesNotExist())
                .andExpect(jsonPath("$.rawDataJson").doesNotExist());
    }

    @Test
    void delegatesExplicitCasePreservingValuesAndReturnsPartialWarnings() throws Exception {
        when(comparisonService.compare(" aapl ", "msft", "6m", "price"))
                .thenReturn(response(ComparisonMode.PRICE));

        mockMvc.perform(get("/api/v1/comparisons")
                        .param("left", " aapl ")
                        .param("right", "msft")
                        .param("period", "6m")
                        .param("mode", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePerformance.mode").value("PRICE"))
                .andExpect(jsonPath("$.warnings[0].section").value("NEWS"))
                .andExpect(jsonPath("$.warnings[0].side").value("LEFT"));
    }

    @Test
    void mapsMissingInvalidDuplicatePeriodModeAndUnknownStock() throws Exception {
        when(comparisonService.compare(isNull(), anyString(), anyString(), anyString()))
                .thenThrow(new InvalidTickerException());
        mockMvc.perform(get("/api/v1/comparisons?right=MSFT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TICKER"));

        when(comparisonService.compare(anyString(), isNull(), anyString(), anyString()))
                .thenThrow(new InvalidTickerException());
        mockMvc.perform(get("/api/v1/comparisons?left=AAPL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TICKER"));

        assertError(new InvalidTickerException(), "left=AAPL!&right=MSFT", 400, "INVALID_TICKER");
        assertError(new DuplicateTickersException(), "left=AAPL&right=aapl", 400, "DUPLICATE_TICKERS");
        assertError(new InvalidPeriodException(), "left=AAPL&right=MSFT&period=2Y", 400, "INVALID_PERIOD");
        assertError(new InvalidComparisonModeException(), "left=AAPL&right=MSFT&mode=GAIN", 400, "INVALID_MODE");
        assertError(new StockNotFoundException("NONE"), "left=NONE&right=MSFT", 404, "STOCK_NOT_FOUND");
    }

    private void assertError(
            RuntimeException exception,
            String query,
            int expectedStatus,
            String code) throws Exception {
        reset(comparisonService);
        doThrow(exception).when(comparisonService)
                .compare(anyString(), anyString(), anyString(), anyString());
        mockMvc.perform(get("/api/v1/comparisons?" + query))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.details").isArray());
    }

    private ComparisonDashboardResponse response(ComparisonMode mode) {
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        CompanySummaryResponse left = new CompanySummaryResponse(
                "AAPL", "Apple Inc.", "NASDAQ", "Technology", "Electronics", "US",
                "https://apple.com", null, "Description", new BigDecimal("200"),
                BigDecimal.ONE, new BigDecimal("0.5"), new BigDecimal("1000000"),
                "USD", now, new BigDecimal("20"), null, now, now, now);
        CompanySummaryResponse right = new CompanySummaryResponse(
                "MSFT", "Microsoft Corporation", "NASDAQ", "Technology", "Software", "US",
                "https://microsoft.com", null, "Description", new BigDecimal("400"),
                BigDecimal.ONE, new BigDecimal("0.25"), new BigDecimal("2000000"),
                "USD", now, new BigDecimal("30"), null, now, now, now);
        PricePerformanceResponse performance = new PricePerformanceResponse(
                mode == ComparisonMode.PRICE ? "6M" : "1Y", mode, null, null, 0,
                null, null, "USD", "USD", List.of());
        ComparisonProvenanceResponse provenance = new ComparisonProvenanceResponse(
                "FMP", "YAHOO_FINANCE", now, now, now, now, now, now, now, now,
                now, now, null, null, now, false);
        return new ComparisonDashboardResponse(
                "AAPL:MSFT:1Y:" + mode,
                left,
                right,
                performance,
                List.of(),
                new ComparisonNewsResponse(List.of(), List.of()),
                null,
                provenance,
                mode == ComparisonMode.PRICE
                        ? List.of(new ComparisonWarningResponse(
                                ComparisonWarningSection.NEWS,
                                ComparisonWarningSide.LEFT,
                                "NEWS_PROVIDER_ERROR",
                                "Recent news is temporarily unavailable for AAPL."))
                        : List.of());
    }
}
