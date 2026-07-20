package com.stocklens.comparison.dto;

import java.util.List;

public record ComparisonNewsResponse(
        List<ComparisonNewsArticleResponse> left,
        List<ComparisonNewsArticleResponse> right) {}
