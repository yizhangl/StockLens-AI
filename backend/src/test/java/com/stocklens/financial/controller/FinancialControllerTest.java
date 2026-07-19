package com.stocklens.financial.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stocklens.common.exception.InvalidPeriodException;
import com.stocklens.common.time.TimeConfiguration;
import com.stocklens.common.web.GlobalExceptionHandler;
import com.stocklens.common.web.RequestIdFilter;
import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.HistoricalPricePointResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.dto.MetricValueResponse;
import com.stocklens.financial.metric.ComparisonStrategy;
import com.stocklens.financial.metric.MetricCategory;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricUnit;
import com.stocklens.financial.service.FinancialMetricsQueryService;
import com.stocklens.financial.service.HistoricalPriceQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, TimeConfiguration.class})
class FinancialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialMetricsQueryService metricsQueryService;

    @MockitoBean
    private HistoricalPriceQueryService historyQueryService;

    @Test
    void returnsMetricsContractWithoutPersistenceOrProviderObjects() throws Exception {
        when(metricsQueryService.getMetrics("AAPL")).thenReturn(new FinancialMetricsResponse(
                "AAPL", "USD", LocalDate.parse("2025-09-27"),
                Instant.parse("2026-07-18T20:00:00Z"), "FMP",
                List.of(new MetricValueResponse(
                        MetricCode.PE_TTM, "P/E (TTM)", MetricCategory.VALUATION,
                        MetricUnit.RATIO, ComparisonStrategy.CONTEXT_DEPENDENT,
                        new BigDecimal("31.2"), "Trailing price relative to earnings.")),
                List.of()));

        mockMvc.perform(get("/api/v1/stocks/AAPL/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.metrics[0].code").value("PE_TTM"))
                .andExpect(jsonPath("$.metrics[0].value").value(31.2))
                .andExpect(jsonPath("$.rawDataJson").doesNotExist());
    }

    @Test
    void defaultsHistoryToOneYearAndReturnsOrderedPointContract() throws Exception {
        when(historyQueryService.getHistory("AAPL", "1Y")).thenReturn(new HistoricalPriceResponse(
                "AAPL", "1Y", LocalDate.parse("2025-07-18"), LocalDate.parse("2026-07-18"),
                "USD", new BigDecimal("10.5000"), "FMP", Instant.parse("2026-07-18T20:00:00Z"),
                List.of(new HistoricalPricePointResponse(
                        LocalDate.parse("2026-07-17"), new BigDecimal("200"),
                        new BigDecimal("205"), new BigDecimal("199"), new BigDecimal("204"),
                        null, 1000L))));

        mockMvc.perform(get("/api/v1/stocks/AAPL/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("1Y"))
                .andExpect(jsonPath("$.returnPercent").value(10.5))
                .andExpect(jsonPath("$.prices[0].date").value("2026-07-17"));
    }

    @Test
    void mapsUnsupportedPeriodToConsistentBadRequest() throws Exception {
        when(historyQueryService.getHistory(anyString(), anyString()))
                .thenThrow(new InvalidPeriodException());

        mockMvc.perform(get("/api/v1/stocks/AAPL/history?period=YTD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.message").value("Period must be one of 1M, 6M, 1Y, 5Y, or MAX."));
    }
}
