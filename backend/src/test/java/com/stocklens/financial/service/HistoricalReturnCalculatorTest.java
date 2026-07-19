package com.stocklens.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.domain.Company;
import com.stocklens.financial.domain.HistoricalPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalReturnCalculatorTest {

    private final HistoricalReturnCalculator calculator = new HistoricalReturnCalculator();

    @Test
    void calculatesPercentagePointsUsingAdjustedCloseWhenAvailable() {
        BigDecimal result = calculator.roundForApi(calculator.calculate(List.of(
                price("100", "80", LocalDate.parse("2026-01-01")),
                price("120", "100", LocalDate.parse("2026-02-01")))));

        assertThat(result).isEqualByComparingTo("25.0000");
    }

    @Test
    void handlesEmptyAndSinglePointSeries() {
        assertThat(calculator.calculate(List.of())).isNull();
        assertThat(calculator.roundForApi(calculator.calculate(List.of(
                price("100", null, LocalDate.parse("2026-01-01"))))))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void calculatesNegativeAndZeroReturns() {
        assertThat(calculator.roundForApi(calculator.calculate(List.of(
                        price("100", null, LocalDate.parse("2026-01-01")),
                        price("80", null, LocalDate.parse("2026-02-01"))))))
                .isEqualByComparingTo("-20.0000");
        assertThat(calculator.roundForApi(calculator.calculate(List.of(
                        price("100", null, LocalDate.parse("2026-01-01")),
                        price("100", null, LocalDate.parse("2026-02-01"))))))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void returnsNullWhenStartingValueIsZero() {
        assertThat(calculator.calculate(List.of(
                price("0", null, LocalDate.parse("2026-01-01")),
                price("100", null, LocalDate.parse("2026-02-01")))))
                .isNull();
    }

    private HistoricalPrice price(String close, String adjusted, LocalDate date) {
        Instant now = Instant.parse("2026-07-18T20:00:00Z");
        Company company = new Company(
                "AAPL", "Apple", "NASDAQ", null, null, null, null, null, null, "AAPL", now, now);
        return new HistoricalPrice(
                company, date, null, null, null, new BigDecimal(close),
                adjusted == null ? null : new BigDecimal(adjusted), null, "USD", "FMP", now);
    }
}
