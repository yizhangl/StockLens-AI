package com.stocklens.comparison.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.comparison.model.ComparisonMode;
import com.stocklens.financial.dto.HistoricalPricePointResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.period.PricePeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalSeriesAlignerTest {

    private static final Instant NOW = Instant.parse("2026-07-19T20:00:00Z");
    private final HistoricalSeriesAligner aligner = new HistoricalSeriesAligner();

    @Test
    void alignsIntersectionAscendingAndUsesOneSharedReturnBaseline() {
        var left = history("AAPL", "USD", List.of(
                point("2026-01-04", "140", null),
                point("2026-01-01", "100", null),
                point("2026-01-02", "110", "120"),
                point("2026-01-02", "999", null),
                point("2026-01-03", "130", null)));
        var right = history("MSFT", "USD", List.of(
                point("2026-01-02", "200", null),
                point("2026-01-03", "220", null),
                point("2026-01-05", "240", null)));

        var result = aligner.align(left, right, PricePeriod.ONE_YEAR, ComparisonMode.RETURN);

        assertThat(result.issues()).isEmpty();
        assertThat(result.performance().startDate()).isEqualTo(LocalDate.parse("2026-01-02"));
        assertThat(result.performance().endDate()).isEqualTo(LocalDate.parse("2026-01-03"));
        assertThat(result.performance().pointCount()).isEqualTo(2);
        assertThat(result.performance().series()).extracting(point -> point.date().toString())
                .containsExactly("2026-01-02", "2026-01-03");
        assertThat(result.performance().series().getFirst().leftValue()).isEqualByComparingTo("0.0000");
        assertThat(result.performance().series().getFirst().rightValue()).isEqualByComparingTo("0.0000");
        assertThat(result.performance().leftReturnPercent()).isEqualByComparingTo("8.3333");
        assertThat(result.performance().rightReturnPercent()).isEqualByComparingTo("10.0000");
    }

    @Test
    void priceModeUsesAdjustedCloseFallbackAndKeepsRawValues() {
        var left = history("AAPL", "USD", List.of(
                point("2026-01-01", "100", "0"),
                point("2026-01-02", "110", "105")));
        var right = history("MSFT", "CAD", List.of(
                point("2026-01-01", "200", null),
                point("2026-01-02", "210", null)));

        var performance = aligner.align(
                left, right, PricePeriod.SIX_MONTHS, ComparisonMode.PRICE).performance();

        assertThat(performance.period()).isEqualTo("6M");
        assertThat(performance.leftCurrency()).isEqualTo("USD");
        assertThat(performance.rightCurrency()).isEqualTo("CAD");
        assertThat(performance.series().getFirst().leftValue()).isEqualByComparingTo("100");
        assertThat(performance.series().getLast().leftValue()).isEqualByComparingTo("105");
        assertThat(performance.leftReturnPercent()).isNull();
        assertThat(performance.rightReturnPercent()).isNull();
    }

    @Test
    void reportsEmptyMissingAndNonOverlappingHistories() {
        var emptyLeft = aligner.align(
                history("AAPL", "USD", List.of()),
                history("MSFT", "USD", List.of(point("2026-01-01", "1", null))),
                PricePeriod.ONE_MONTH,
                ComparisonMode.RETURN);
        var noCommon = aligner.align(
                history("AAPL", "USD", List.of(point("2026-01-01", "1", null))),
                history("MSFT", "USD", List.of(point("2026-01-02", "1", null))),
                PricePeriod.MAX,
                ComparisonMode.PRICE);

        assertThat(emptyLeft.performance().series()).isEmpty();
        assertThat(emptyLeft.issues()).containsExactly(
                HistoricalSeriesAligner.AlignmentIssue.NO_COMMON_HISTORY);
        assertThat(noCommon.performance().pointCount()).isZero();
        assertThat(noCommon.performance().period()).isEqualTo("MAX");
        assertThat(noCommon.issues()).containsExactly(
                HistoricalSeriesAligner.AlignmentIssue.NO_COMMON_HISTORY);
    }

    @Test
    void oneCommonReturnPointHasZeroSeriesButNullSummary() {
        var result = aligner.align(
                history("AAPL", "USD", List.of(point("2026-01-01", "100", null))),
                history("MSFT", "USD", List.of(point("2026-01-01", "200", null))),
                PricePeriod.FIVE_YEARS,
                ComparisonMode.RETURN);

        assertThat(result.performance().series()).singleElement().satisfies(point -> {
            assertThat(point.leftValue()).isEqualByComparingTo("0.0000");
            assertThat(point.rightValue()).isEqualByComparingTo("0.0000");
        });
        assertThat(result.performance().leftReturnPercent()).isNull();
        assertThat(result.performance().rightReturnPercent()).isNull();
        assertThat(result.issues()).containsExactly(
                HistoricalSeriesAligner.AlignmentIssue.INSUFFICIENT_HISTORY);
    }

    @Test
    void discardsNullAndNonPositiveEffectiveValues() {
        var result = aligner.align(
                history("AAPL", "USD", java.util.Arrays.asList(
                        null,
                        point("2026-01-01", "0", null),
                        point("2026-01-02", "-1", null),
                        point("2026-01-03", "100", null))),
                history("MSFT", "USD", List.of(
                        point("2026-01-01", "200", null),
                        point("2026-01-03", "300", null))),
                PricePeriod.ONE_YEAR,
                ComparisonMode.PRICE);

        assertThat(result.performance().series()).singleElement()
                .satisfies(point -> assertThat(point.date())
                        .isEqualTo(LocalDate.parse("2026-01-03")));
    }

    @Test
    void propagatesEverySupportedPeriod() {
        for (PricePeriod period : PricePeriod.values()) {
            var result = aligner.align(
                    history("AAPL", "USD", List.of(point("2026-01-01", "1", null))),
                    history("MSFT", "USD", List.of(point("2026-01-01", "1", null))),
                    period,
                    ComparisonMode.PRICE);
            assertThat(result.performance().period()).isEqualTo(period.code());
        }
    }

    private HistoricalPriceResponse history(
            String ticker, String currency, List<HistoricalPricePointResponse> prices) {
        return new HistoricalPriceResponse(
                ticker, "1Y", LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-01"),
                currency, null, "FMP", NOW, prices);
    }

    private HistoricalPricePointResponse point(String date, String close, String adjusted) {
        return new HistoricalPricePointResponse(
                LocalDate.parse(date), null, null, null,
                close == null ? null : new BigDecimal(close),
                adjusted == null ? null : new BigDecimal(adjusted),
                null);
    }
}
