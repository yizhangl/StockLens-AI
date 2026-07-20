package com.stocklens.comparison.model;

import com.stocklens.common.exception.InvalidComparisonModeException;
import java.util.Locale;

public enum ComparisonMode {
    PRICE,
    RETURN;

    public static ComparisonMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidComparisonModeException();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidComparisonModeException();
        }
    }
}
