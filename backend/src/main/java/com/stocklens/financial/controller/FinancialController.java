package com.stocklens.financial.controller;

import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.service.FinancialMetricsQueryService;
import com.stocklens.financial.service.HistoricalPriceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
public class FinancialController {

    private final FinancialMetricsQueryService metricsQueryService;
    private final HistoricalPriceQueryService historyQueryService;

    public FinancialController(
            FinancialMetricsQueryService metricsQueryService,
            HistoricalPriceQueryService historyQueryService) {
        this.metricsQueryService = metricsQueryService;
        this.historyQueryService = historyQueryService;
    }

    @GetMapping("/{ticker}/metrics")
    FinancialMetricsResponse getMetrics(@PathVariable String ticker) {
        return metricsQueryService.getMetrics(ticker);
    }

    @GetMapping("/{ticker}/history")
    HistoricalPriceResponse getHistory(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1Y") String period) {
        return historyQueryService.getHistory(ticker, period);
    }
}
