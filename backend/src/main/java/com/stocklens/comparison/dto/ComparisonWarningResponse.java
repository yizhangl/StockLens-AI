package com.stocklens.comparison.dto;

import com.stocklens.comparison.model.ComparisonWarningSection;
import com.stocklens.comparison.model.ComparisonWarningSide;

public record ComparisonWarningResponse(
        ComparisonWarningSection section,
        ComparisonWarningSide side,
        String code,
        String message) {}
