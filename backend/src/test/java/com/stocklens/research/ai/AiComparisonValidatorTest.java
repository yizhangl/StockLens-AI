package com.stocklens.research.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.domain.Company;
import com.stocklens.research.context.BuiltComparisonContext;
import com.stocklens.research.context.GroundedSource;
import com.stocklens.research.context.GroundedSourceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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

        AiComparisonResult unknownTopLevel = new AiComparisonResult(
                unknown.overallSummary(), result("M1", "M2").advantages(), List.of(), List.of(" M9 "));
        assertThat(validator.validate(unknownTopLevel, context()).failures()).contains("unknown source ID");

        AiComparisonResult advice = new AiComparisonResult("Buy this stock now", result("M1", "M2").advantages(), List.of(), List.of());
        assertThat(validator.validate(advice, context()).isValid()).isFalse();
    }

    @Test
    void acceptsFifteenUniqueNestedSourceIds() {
        var validation = validator.validate(resultWithUniqueSources(15), contextWithSources(15));

        assertThat(validation.isValid()).isTrue();
        assertThat(validation.result().sourceIds()).hasSize(15);
    }

    @Test
    void rejectsSixteenUniqueNestedSourceIds() {
        var validation = validator.validate(resultWithUniqueSources(16), contextWithSources(16));

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.failures()).containsExactly("too many source IDs");
    }

    @Test
    void normalizesNestedSourceIdsBeforeCountingAndValidation() {
        AiAdvantageResult first = new AiAdvantageResult(
                "AAPL", "Supplied metrics differ.", List.of(" M1 ", "M1", "", "  "));
        AiAdvantageResult second = new AiAdvantageResult(
                "MSFT", "Supplied metrics differ.", List.of("M2", " M2 "));
        AiRiskResult risk = new AiRiskResult(
                "AAPL", "Supplied data identifies a risk.", List.of("M1", " M2 ", "M2"));
        AiComparisonResult response = new AiComparisonResult(
                "The supplied data shows different reported characteristics.",
                new AiAdvantages(first, second, first, second), List.of(risk), List.of(" M1 ", "M2"));

        var validation = validator.validate(response, context());

        assertThat(validation.isValid()).isTrue();
        assertThat(validation.result().advantages().valuation().sourceIds()).containsExactly("M1");
        assertThat(validation.result().keyRisks().getFirst().sourceIds()).containsExactly("M1", "M2");
        assertThat(validation.result().sourceIds()).containsExactly("M1", "M2");
    }

    private AiComparisonResult result(String first, String second) {
        AiAdvantageResult left = new AiAdvantageResult("AAPL", "Supplied metrics differ.", List.of(first));
        AiAdvantageResult right = new AiAdvantageResult("MSFT", "Supplied metrics differ.", List.of(second));
        return new AiComparisonResult("The supplied data shows different reported characteristics.",
                new AiAdvantages(left, right, left, right), List.of(), List.of(first, second));
    }

    private AiComparisonResult resultWithUniqueSources(int count) {
        List<String> ids = IntStream.rangeClosed(1, count).mapToObj(index -> "M" + index).toList();
        AiAdvantages advantages = new AiAdvantages(
                advantage("AAPL", ids.subList(0, 2)),
                advantage("MSFT", ids.subList(2, 4)),
                advantage("AAPL", ids.subList(4, 6)),
                advantage("MSFT", ids.subList(6, 8)));
        List<AiRiskResult> risks = new ArrayList<>();
        for (int index = 8; index < ids.size(); index += 2) {
            risks.add(new AiRiskResult(index % 4 == 0 ? "AAPL" : "MSFT",
                    "Supplied data identifies a risk.", ids.subList(index, Math.min(index + 2, ids.size()))));
        }
        return new AiComparisonResult("The supplied data shows different reported characteristics.",
                advantages, risks, List.of());
    }

    private AiAdvantageResult advantage(String ticker, List<String> sourceIds) {
        return new AiAdvantageResult(ticker, "Supplied metrics differ.", sourceIds);
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

    private BuiltComparisonContext contextWithSources(int count) {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        Company left = company("AAPL", now);
        Company right = company("MSFT", now);
        List<GroundedSource> sources = IntStream.rangeClosed(1, count)
                .mapToObj(index -> new GroundedSource("M" + index, GroundedSourceType.FINANCIAL_METRIC,
                        index % 2 == 0 ? "MSFT" : "AAPL", "Supplied metric " + index,
                        "FMP", null, now, null, null, null, null))
                .toList();
        Map<String, GroundedSource> sourcesById = new LinkedHashMap<>();
        sources.forEach(source -> sourcesById.put(source.id(), source));
        return new BuiltComparisonContext(left, right, sources, sourcesById, now, "stable");
    }

    private Company company(String ticker, Instant now) {
        return new Company(ticker, ticker, null, null, null, null, null, null, null, ticker, now, now);
    }
}
