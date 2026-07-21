package com.stocklens.research.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.domain.Company;
import com.stocklens.research.context.BuiltComparisonContext;
import com.stocklens.research.context.GroundedSource;
import com.stocklens.research.context.GroundedSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiComparisonValidatorTest {

    private final AiComparisonValidator validator = new AiComparisonValidator();

    @Test
    void acceptsAllCategoriesAndRebuildsStableSourceUnion() {
        var result = validator.validate(result("M1", "M2"), context());

        assertThat(result.isValid()).isTrue();
        assertThat(result.result().sourceIds()).containsExactly("M1", "M2");
    }

    @Test
    void rejectsUnknownSourcesAndDirectBuyInstructions() {
        AiComparisonResult unknown = result("M9", "M2");
        assertThat(validator.validate(unknown, context()).isValid()).isFalse();

        AiComparisonResult advice = new AiComparisonResult("Buy this stock now", result("M1", "M2").advantages(), List.of(), List.of());
        assertThat(validator.validate(advice, context()).isValid()).isFalse();
    }

    private AiComparisonResult result(String first, String second) {
        AiAdvantageResult left = new AiAdvantageResult("AAPL", "Supplied metrics differ.", List.of(first));
        AiAdvantageResult right = new AiAdvantageResult("MSFT", "Supplied metrics differ.", List.of(second));
        return new AiComparisonResult("The supplied data shows different reported characteristics.",
                new AiAdvantages(left, right, left, right), List.of(), List.of("ignored"));
    }

    private BuiltComparisonContext context() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        Company left = company("AAPL", now);
        Company right = company("MSFT", now);
        GroundedSource one = new GroundedSource("M1", GroundedSourceType.FINANCIAL_METRIC, "AAPL",
                "P/E: 20", "FMP", null, now, null, null, null, null);
        GroundedSource two = new GroundedSource("M2", GroundedSourceType.FINANCIAL_METRIC, "MSFT",
                "P/E: 30", "FMP", null, now, null, null, null, null);
        return new BuiltComparisonContext(left, right, List.of(one, two), Map.of("M1", one, "M2", two), now, "stable");
    }

    private Company company(String ticker, Instant now) {
        return new Company(ticker, ticker, null, null, null, null, null, null, null, ticker, now, now);
    }
}
