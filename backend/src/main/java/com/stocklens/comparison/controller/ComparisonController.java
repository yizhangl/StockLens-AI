package com.stocklens.comparison.controller;

import com.stocklens.comparison.dto.ComparisonDashboardResponse;
import com.stocklens.comparison.service.ComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping
    ComparisonDashboardResponse compare(
            @RequestParam(required = false) String left,
            @RequestParam(required = false) String right,
            @RequestParam(defaultValue = "1Y") String period,
            @RequestParam(defaultValue = "RETURN") String mode) {
        return comparisonService.compare(left, right, period, mode);
    }
}
