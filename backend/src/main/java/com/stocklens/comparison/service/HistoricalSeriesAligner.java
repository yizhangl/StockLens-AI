package com.stocklens.comparison.service;

import com.stocklens.comparison.dto.PricePerformancePoint;
import com.stocklens.comparison.dto.PricePerformanceResponse;
import com.stocklens.comparison.model.ComparisonMode;
import com.stocklens.financial.dto.HistoricalPricePointResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.period.PricePeriod;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class HistoricalSeriesAligner {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext CALCULATION_CONTEXT =
            new MathContext(24, RoundingMode.HALF_UP);
    private static final int API_SCALE = 4;

    public AlignmentResult align(
            HistoricalPriceResponse left,
            HistoricalPriceResponse right,
            PricePeriod period,
            ComparisonMode mode) {
        Map<LocalDate, BigDecimal> leftValues = usableValues(left);
        Map<LocalDate, BigDecimal> rightValues = usableValues(right);
        List<LocalDate> commonDates = leftValues.keySet().stream()
                .filter(rightValues::containsKey)
                .toList();
        String leftCurrency = left == null ? null : left.currency();
        String rightCurrency = right == null ? null : right.currency();

        if (commonDates.isEmpty()) {
            return new AlignmentResult(
                    new PricePerformanceResponse(
                            period.code(), mode, null, null, 0, null, null,
                            leftCurrency, rightCurrency, List.of()),
                    List.of(AlignmentIssue.NO_COMMON_HISTORY));
        }

        BigDecimal firstLeft = leftValues.get(commonDates.getFirst());
        BigDecimal firstRight = rightValues.get(commonDates.getFirst());
        List<PricePerformancePoint> series = new ArrayList<>(commonDates.size());
        for (LocalDate date : commonDates) {
            BigDecimal leftValue = leftValues.get(date);
            BigDecimal rightValue = rightValues.get(date);
            if (mode == ComparisonMode.RETURN) {
                leftValue = returnPercent(leftValue, firstLeft);
                rightValue = returnPercent(rightValue, firstRight);
            }
            series.add(new PricePerformancePoint(date, leftValue, rightValue));
        }

        boolean insufficientReturnHistory =
                mode == ComparisonMode.RETURN && commonDates.size() < 2;
        BigDecimal leftReturn = null;
        BigDecimal rightReturn = null;
        if (mode == ComparisonMode.RETURN && !insufficientReturnHistory) {
            PricePerformancePoint finalPoint = series.getLast();
            leftReturn = finalPoint.leftValue();
            rightReturn = finalPoint.rightValue();
        }

        PricePerformanceResponse response = new PricePerformanceResponse(
                period.code(),
                mode,
                commonDates.getFirst(),
                commonDates.getLast(),
                commonDates.size(),
                leftReturn,
                rightReturn,
                leftCurrency,
                rightCurrency,
                List.copyOf(series));
        return new AlignmentResult(
                response,
                insufficientReturnHistory
                        ? List.of(AlignmentIssue.INSUFFICIENT_HISTORY)
                        : List.of());
    }

    private Map<LocalDate, BigDecimal> usableValues(HistoricalPriceResponse response) {
        Map<LocalDate, BigDecimal> values = new TreeMap<>();
        if (response == null || response.prices() == null) {
            return values;
        }
        for (HistoricalPricePointResponse point : response.prices()) {
            if (point == null || point.date() == null) {
                continue;
            }
            BigDecimal value = positive(point.adjustedClose())
                    ? point.adjustedClose()
                    : positive(point.close()) ? point.close() : null;
            if (value != null) {
                values.putIfAbsent(point.date(), value);
            }
        }
        return values;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal returnPercent(BigDecimal value, BigDecimal baseline) {
        return value.divide(baseline, CALCULATION_CONTEXT)
                .subtract(BigDecimal.ONE, CALCULATION_CONTEXT)
                .multiply(ONE_HUNDRED, CALCULATION_CONTEXT)
                .setScale(API_SCALE, RoundingMode.HALF_UP);
    }

    public record AlignmentResult(
            PricePerformanceResponse performance,
            List<AlignmentIssue> issues) {}

    public enum AlignmentIssue {
        NO_COMMON_HISTORY,
        INSUFFICIENT_HISTORY
    }
}
