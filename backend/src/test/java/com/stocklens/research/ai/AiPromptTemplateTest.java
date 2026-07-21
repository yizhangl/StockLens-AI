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

class AiPromptTemplateTest {

    private final AiPromptTemplate promptTemplate = new AiPromptTemplate();

    @Test
    void generationPromptSpecifiesCitationSelectionAndOverallLimit() {
        String prompt = promptTemplate.build(context()).userMessage();

        assertThat(prompt)
                .contains("Use only supplied source IDs")
                .contains("Use only the strongest evidence")
                .contains("Normally use 1-2 source IDs per advantage")
                .contains("Normally use 1-2 source IDs per risk")
                .contains("Use no more than 15 unique source IDs across the entire response")
                .contains("Do not cite every available source")
                .contains("Top-level sourceIds must be the deduplicated union of all nested citations");
    }

    @Test
    void sourceCountRepairPromptIncludesExactFailureAndCorrectionInstructions() {
        String prompt = promptTemplate.build(context())
                .withRepair("too many source IDs, unknown source ID")
                .userMessage();

        assertThat(prompt)
                .contains("Validation failures: too many source IDs, unknown source ID")
                .contains("previous output exceeded 15 unique source IDs")
                .contains("Retain only the strongest citations")
                .contains("remove weak or redundant citations")
                .contains("preserve the existing analysis")
                .contains("Return corrected JSON only");
    }

    private BuiltComparisonContext context() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        Company left = company("AAPL", now);
        Company right = company("MSFT", now);
        GroundedSource source = new GroundedSource("M1", GroundedSourceType.FINANCIAL_METRIC,
                "AAPL", "P/E: 20", "FMP", null, now, null, null, null, null);
        return new BuiltComparisonContext(left, right, List.of(source), Map.of("M1", source), now, "stable");
    }

    private Company company(String ticker, Instant now) {
        return new Company(ticker, ticker, null, null, null, null, null, null, null, ticker, now, now);
    }
}
