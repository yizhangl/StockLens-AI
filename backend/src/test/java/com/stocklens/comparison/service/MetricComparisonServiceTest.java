package com.stocklens.comparison.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.comparison.dto.MetricComparisonResponse;
import com.stocklens.comparison.model.ComparisonOutcome;
import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.MetricValueResponse;
import com.stocklens.financial.metric.MetricCategory;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricDefinition;
import com.stocklens.financial.metric.MetricDefinitionRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricComparisonServiceTest {

    private final MetricDefinitionRegistry registry = new MetricDefinitionRegistry();
    private final MetricComparisonService service = new MetricComparisonService(registry);

    @Test
    void appliesHigherAndLowerStrategiesInBothDirectionsAndEquality() {
        var groups = service.compare(
                response(value(MetricCode.GROSS_MARGIN, "0.40"),
                        value(MetricCode.NET_MARGIN, "0.10"),
                        value(MetricCode.DEBT_TO_EQUITY, "0.50"),
                        value(MetricCode.PEG_RATIO, "2.0"),
                        value(MetricCode.REVENUE_GROWTH, "0.10")),
                response(value(MetricCode.GROSS_MARGIN, "0.30"),
                        value(MetricCode.NET_MARGIN, "0.20"),
                        value(MetricCode.DEBT_TO_EQUITY, "0.70"),
                        value(MetricCode.PEG_RATIO, "1.0"),
                        value(MetricCode.REVENUE_GROWTH, "0.10")));

        assertThat(metric(groups, MetricCode.GROSS_MARGIN).outcome()).isEqualTo(ComparisonOutcome.LEFT);
        assertThat(metric(groups, MetricCode.NET_MARGIN).outcome()).isEqualTo(ComparisonOutcome.RIGHT);
        assertThat(metric(groups, MetricCode.DEBT_TO_EQUITY).outcome()).isEqualTo(ComparisonOutcome.LEFT);
        assertThat(metric(groups, MetricCode.PEG_RATIO).outcome()).isEqualTo(ComparisonOutcome.RIGHT);
        assertThat(metric(groups, MetricCode.REVENUE_GROWTH).outcome()).isEqualTo(ComparisonOutcome.EQUAL);
    }

    @Test
    void missingValuesAreInsufficientAndRawDecimalsArePreserved() {
        var groups = service.compare(
                response(value(MetricCode.GROSS_MARGIN, "0.123456789012")),
                response(value(MetricCode.GROSS_MARGIN, null), value(MetricCode.NET_MARGIN, "0.2")));

        MetricComparisonResponse gross = metric(groups, MetricCode.GROSS_MARGIN);
        assertThat(gross.outcome()).isEqualTo(ComparisonOutcome.INSUFFICIENT_DATA);
        assertThat(gross.leftValue()).isEqualByComparingTo("0.123456789012");
        assertThat(metric(groups, MetricCode.NET_MARGIN).outcome())
                .isEqualTo(ComparisonOutcome.INSUFFICIENT_DATA);
        assertThat(metric(service.compare(null, null), MetricCode.BETA).outcome())
                .isEqualTo(ComparisonOutcome.INSUFFICIENT_DATA);
    }

    @Test
    void contextRangeAndDescriptiveMetricsRemainNeutral() {
        var left = response(
                value(MetricCode.PE_TTM, "10"),
                value(MetricCode.FORWARD_PE, "10"),
                value(MetricCode.RETURN_ON_EQUITY, "0.3"),
                value(MetricCode.CURRENT_RATIO, "2"),
                value(MetricCode.BETA, "0.8"));
        var right = response(
                value(MetricCode.PE_TTM, "20"),
                value(MetricCode.FORWARD_PE, "20"),
                value(MetricCode.RETURN_ON_EQUITY, "0.1"),
                value(MetricCode.CURRENT_RATIO, "1"),
                value(MetricCode.BETA, "1.2"));

        for (MetricCode code : List.of(
                MetricCode.PE_TTM,
                MetricCode.FORWARD_PE,
                MetricCode.RETURN_ON_EQUITY,
                MetricCode.CURRENT_RATIO,
                MetricCode.BETA)) {
            assertThat(metric(service.compare(left, right), code).outcome())
                    .isEqualTo(ComparisonOutcome.NEUTRAL);
        }
    }

    @Test
    void nonPositivePegNeverBecomesAnUnsafeWinner() {
        var groups = service.compare(
                response(value(MetricCode.PEG_RATIO, "-2")),
                response(value(MetricCode.PEG_RATIO, "1")));

        assertThat(metric(groups, MetricCode.PEG_RATIO).outcome())
                .isEqualTo(ComparisonOutcome.NEUTRAL);
    }

    @Test
    void groupsAndMetricsFollowRegistryOrderAndExcludeSummary() {
        var groups = service.compare(null, null);

        assertThat(groups).extracting(group -> group.category())
                .containsExactly(
                        MetricCategory.VALUATION,
                        MetricCategory.PROFITABILITY,
                        MetricCategory.GROWTH,
                        MetricCategory.FINANCIAL_HEALTH);
        assertThat(groups.getFirst().metrics()).extracting(MetricComparisonResponse::code)
                .containsExactly(
                        MetricCode.PE_TTM,
                        MetricCode.FORWARD_PE,
                        MetricCode.PEG_RATIO,
                        MetricCode.PRICE_TO_SALES);
        assertThat(groups.stream().flatMap(group -> group.metrics().stream()))
                .extracting(MetricComparisonResponse::code)
                .doesNotContain(MetricCode.REVENUE_TTM);
    }

    private FinancialMetricsResponse response(MetricValueResponse... metrics) {
        return new FinancialMetricsResponse(
                "AAPL", "USD", null, Instant.parse("2026-07-19T20:00:00Z"),
                "FMP", List.of(metrics), List.of());
    }

    private MetricValueResponse value(MetricCode code, String value) {
        MetricDefinition definition = registry.get(code);
        return new MetricValueResponse(
                code,
                definition.displayName(),
                definition.category(),
                definition.unit(),
                definition.comparisonStrategy(),
                value == null ? null : new BigDecimal(value),
                definition.description());
    }

    private MetricComparisonResponse metric(
            List<com.stocklens.comparison.dto.MetricGroupResponse> groups,
            MetricCode code) {
        return groups.stream()
                .flatMap(group -> group.metrics().stream())
                .filter(metric -> metric.code() == code)
                .findFirst()
                .orElseThrow();
    }
}
