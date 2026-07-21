package com.stocklens.research.controller;

import com.stocklens.research.dto.ComparisonResearchRequest;
import com.stocklens.research.dto.ComparisonResearchResponse;
import com.stocklens.research.service.ComparisonResearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonResearchController {
    private final ComparisonResearchService researchService;
    public ComparisonResearchController(ComparisonResearchService researchService) { this.researchService = researchService; }

    @PostMapping("/research")
    ComparisonResearchResponse research(@RequestBody ComparisonResearchRequest request) {
        String left = request == null ? null : request.leftTicker();
        String right = request == null ? null : request.rightTicker();
        return request != null && Boolean.TRUE.equals(request.forceRefresh())
                ? researchService.generate(left, right, true)
                : researchService.generate(left, right);
    }
}
