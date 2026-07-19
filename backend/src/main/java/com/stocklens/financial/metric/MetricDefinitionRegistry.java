package com.stocklens.financial.metric;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MetricDefinitionRegistry {

    private final Map<MetricCode, MetricDefinition> definitions = new EnumMap<>(MetricCode.class);

    public MetricDefinitionRegistry() {
        add(MetricCode.PE_TTM, "P/E (TTM)", MetricCategory.VALUATION, MetricUnit.RATIO,
                ComparisonStrategy.CONTEXT_DEPENDENT, "Trailing price relative to trailing earnings.");
        add(MetricCode.FORWARD_PE, "Forward P/E", MetricCategory.VALUATION, MetricUnit.RATIO,
                ComparisonStrategy.CONTEXT_DEPENDENT, "Price relative to forecast earnings when directly supplied.");
        add(MetricCode.PEG_RATIO, "PEG Ratio", MetricCategory.VALUATION, MetricUnit.RATIO,
                ComparisonStrategy.LOWER_IS_GENERALLY_BETTER, "P/E interpreted relative to earnings growth when positive.");
        add(MetricCode.PRICE_TO_SALES, "Price-to-Sales", MetricCategory.VALUATION, MetricUnit.RATIO,
                ComparisonStrategy.CONTEXT_DEPENDENT, "Market value relative to trailing sales.");
        add(MetricCode.REVENUE_TTM, "Revenue (TTM)", MetricCategory.SUMMARY, MetricUnit.CURRENCY_AMOUNT,
                ComparisonStrategy.DESCRIPTIVE_ONLY, "Trailing twelve-month revenue.");
        add(MetricCode.GROSS_MARGIN, "Gross Margin", MetricCategory.PROFITABILITY,
                MetricUnit.DECIMAL_FRACTION_PERCENT, ComparisonStrategy.HIGHER_IS_GENERALLY_BETTER,
                "Gross profit as a fraction of revenue.");
        add(MetricCode.NET_MARGIN, "Net Margin", MetricCategory.PROFITABILITY,
                MetricUnit.DECIMAL_FRACTION_PERCENT, ComparisonStrategy.HIGHER_IS_GENERALLY_BETTER,
                "Net income as a fraction of revenue.");
        add(MetricCode.RETURN_ON_EQUITY, "Return on Equity", MetricCategory.PROFITABILITY,
                MetricUnit.DECIMAL_FRACTION_PERCENT, ComparisonStrategy.CONTEXT_DEPENDENT,
                "Return on equity can be distorted by leverage.");
        add(MetricCode.REVENUE_GROWTH, "Revenue Growth", MetricCategory.GROWTH,
                MetricUnit.DECIMAL_FRACTION_PERCENT, ComparisonStrategy.HIGHER_IS_GENERALLY_BETTER,
                "Latest annual year-over-year revenue growth.");
        add(MetricCode.EARNINGS_GROWTH, "Earnings Growth", MetricCategory.GROWTH,
                MetricUnit.DECIMAL_FRACTION_PERCENT, ComparisonStrategy.HIGHER_IS_GENERALLY_BETTER,
                "Latest annual year-over-year net-income growth.");
        add(MetricCode.DEBT_TO_EQUITY, "Debt-to-Equity", MetricCategory.FINANCIAL_HEALTH,
                MetricUnit.RATIO, ComparisonStrategy.LOWER_IS_GENERALLY_BETTER,
                "Debt relative to shareholder equity.");
        add(MetricCode.CURRENT_RATIO, "Current Ratio", MetricCategory.FINANCIAL_HEALTH,
                MetricUnit.RATIO, ComparisonStrategy.RANGE_DEPENDENT,
                "Short-term liquidity must be interpreted by range and industry.");
        add(MetricCode.BETA, "Beta", MetricCategory.FINANCIAL_HEALTH, MetricUnit.RATIO,
                ComparisonStrategy.DESCRIPTIVE_ONLY, "Market sensitivity measure with no universal winner.");
    }

    public List<MetricDefinition> all() {
        return definitions.values().stream().toList();
    }

    public MetricDefinition get(MetricCode code) {
        return definitions.get(code);
    }

    private void add(
            MetricCode code,
            String displayName,
            MetricCategory category,
            MetricUnit unit,
            ComparisonStrategy strategy,
            String description) {
        definitions.put(code, new MetricDefinition(code, displayName, category, unit, strategy, description));
    }
}
