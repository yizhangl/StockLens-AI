package com.stocklens.financial.period;

import com.stocklens.common.exception.InvalidPeriodException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

public enum PricePeriod {
    ONE_MONTH("1M"),
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    FIVE_YEARS("5Y"),
    MAX("MAX");

    private final String code;

    PricePeriod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public PriceDateRange range(Clock clock) {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = switch (this) {
            case ONE_MONTH -> to.minusMonths(1);
            case SIX_MONTHS -> to.minusMonths(6);
            case ONE_YEAR -> to.minusYears(1);
            case FIVE_YEARS -> to.minusYears(5);
            case MAX -> null;
        };
        return new PriceDateRange(from, to);
    }

    public static PricePeriod parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidPeriodException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (PricePeriod period : values()) {
            if (period.code.equals(normalized)) {
                return period;
            }
        }
        throw new InvalidPeriodException();
    }
}
