package com.stocklens.comparison.service;

import com.stocklens.comparison.dto.MetricComparisonResponse;
import com.stocklens.comparison.dto.MetricGroupResponse;
import com.stocklens.comparison.model.ComparisonOutcome;
import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.MetricValueResponse;
import com.stocklens.financial.metric.ComparisonStrategy;
import com.stocklens.financial.metric.MetricCategory;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricDefinition;
import com.stocklens.financial.metric.MetricDefinitionRegistry;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetricComparisonService {

    private static final List<MetricCategory> GROUP_ORDER = List.of(
            MetricCategory.VALUATION,
            MetricCategory.PROFITABILITY,
            MetricCategory.GROWTH,
            MetricCategory.FINANCIAL_HEALTH);

    private final MetricDefinitionRegistry registry;

    public MetricComparisonService(MetricDefinitionRegistry registry) {
        this.registry = registry;
    }

    public List<MetricGroupResponse> compare(
            FinancialMetricsResponse left,
            FinancialMetricsResponse right) {
        Map<MetricCode, BigDecimal> leftValues = values(left);
        Map<MetricCode, BigDecimal> rightValues = values(right);
        return GROUP_ORDER.stream()
                .map(category -> new MetricGroupResponse(
                        category,
                        registry.all().stream()
                                .filter(definition -> definition.category() == category)
                                .map(definition -> compare(
                                        definition,
                                        leftValues.get(definition.code()),
                                        rightValues.get(definition.code())))
                                .toList()))
                .toList();
    }

    private MetricComparisonResponse compare(
            MetricDefinition definition,
            BigDecimal left,
            BigDecimal right) {
        ComparisonOutcome outcome = outcome(definition, left, right);
        return new MetricComparisonResponse(
                definition.code(),
                definition.displayName(),
                definition.category(),
                definition.unit(),
                left,
                right,
                definition.comparisonStrategy(),
                outcome,
                explanation(definition, outcome, left, right));
    }

    private ComparisonOutcome outcome(
            MetricDefinition definition,
            BigDecimal left,
            BigDecimal right) {
        if (left == null || right == null) {
            return ComparisonOutcome.INSUFFICIENT_DATA;
        }
        if (definition.code() == MetricCode.PEG_RATIO
                && (left.signum() <= 0 || right.signum() <= 0)) {
            return ComparisonOutcome.NEUTRAL;
        }
        return switch (definition.comparisonStrategy()) {
            case HIGHER_IS_GENERALLY_BETTER -> compareNumeric(left, right, true);
            case LOWER_IS_GENERALLY_BETTER -> compareNumeric(left, right, false);
            case RANGE_DEPENDENT, CONTEXT_DEPENDENT, DESCRIPTIVE_ONLY ->
                    ComparisonOutcome.NEUTRAL;
        };
    }

    private ComparisonOutcome compareNumeric(
            BigDecimal left,
            BigDecimal right,
            boolean higherWins) {
        int comparison = left.compareTo(right);
        if (comparison == 0) {
            return ComparisonOutcome.EQUAL;
        }
        boolean leftWins = higherWins ? comparison > 0 : comparison < 0;
        return leftWins ? ComparisonOutcome.LEFT : ComparisonOutcome.RIGHT;
    }

    private String explanation(
            MetricDefinition definition,
            ComparisonOutcome outcome,
            BigDecimal left,
            BigDecimal right) {
        if (outcome == ComparisonOutcome.INSUFFICIENT_DATA) {
            return "One or both values are unavailable.";
        }
        if (definition.code() == MetricCode.PEG_RATIO
                && left != null && right != null
                && (left.signum() <= 0 || right.signum() <= 0)) {
            return "Non-positive PEG values are not ranked.";
        }
        return switch (outcome) {
            case EQUAL -> "The reported values are equal.";
            case LEFT, RIGHT -> definition.comparisonStrategy()
                    == ComparisonStrategy.HIGHER_IS_GENERALLY_BETTER
                            ? "The higher reported value is highlighted."
                            : "The lower reported value is highlighted.";
            case NEUTRAL -> "This metric requires context and has no universal winner.";
            case INSUFFICIENT_DATA -> throw new IllegalStateException("Handled above");
        };
    }

    private Map<MetricCode, BigDecimal> values(FinancialMetricsResponse response) {
        Map<MetricCode, BigDecimal> values = new EnumMap<>(MetricCode.class);
        if (response == null || response.metrics() == null) {
            return values;
        }
        for (MetricValueResponse metric : response.metrics()) {
            if (metric != null && metric.code() != null) {
                values.put(metric.code(), metric.value());
            }
        }
        return values;
    }
}
