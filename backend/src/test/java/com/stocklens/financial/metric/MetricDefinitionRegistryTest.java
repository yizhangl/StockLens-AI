package com.stocklens.financial.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricDefinitionRegistryTest {

    @Test
    void definesEveryMetricExactlyOnceWithSafeComparisonSemantics() {
        MetricDefinitionRegistry registry = new MetricDefinitionRegistry();

        assertThat(registry.all()).hasSize(MetricCode.values().length)
                .extracting(MetricDefinition::code)
                .containsExactlyInAnyOrder(MetricCode.values());
        assertThat(registry.get(MetricCode.RETURN_ON_EQUITY).comparisonStrategy())
                .isEqualTo(ComparisonStrategy.CONTEXT_DEPENDENT);
        assertThat(registry.get(MetricCode.BETA).comparisonStrategy())
                .isEqualTo(ComparisonStrategy.DESCRIPTIVE_ONLY);
    }
}
