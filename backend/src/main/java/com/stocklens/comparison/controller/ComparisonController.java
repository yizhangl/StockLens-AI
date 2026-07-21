package com.stocklens.comparison.controller;

import com.stocklens.comparison.dto.ComparisonDashboardResponse;
import com.stocklens.comparison.service.ComparisonService;
import com.stocklens.comparison.service.ComparisonRefreshService;
import com.stocklens.comparison.dto.ComparisonRefreshRequest;
import com.stocklens.comparison.dto.ComparisonRefreshResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final ComparisonRefreshService refreshService;

    public ComparisonController(ComparisonService comparisonService, ComparisonRefreshService refreshService) {
        this.comparisonService = comparisonService;
        this.refreshService = refreshService;
    }

    @GetMapping
    ComparisonDashboardResponse compare(
            @RequestParam(required = false) String left,
            @RequestParam(required = false) String right,
            @RequestParam(defaultValue = "1Y") String period,
            @RequestParam(defaultValue = "RETURN") String mode) {
        return comparisonService.compare(left, right, period, mode);
    }

    @PostMapping("/refresh")
    ComparisonRefreshResponse refresh(@RequestBody ComparisonRefreshRequest request) {
        return refreshService.refresh(request == null ? null : request.tickers(), request != null && Boolean.TRUE.equals(request.regenerateBrief()));
    }
}
