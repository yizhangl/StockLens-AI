package com.stocklens.research.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.research.domain.ComparisonBrief;
import com.stocklens.support.IntegrationTestContainers;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestContainers.class)
@SpringBootTest
class ComparisonBriefRepositoryIntegrationTest {

    private static final String INPUT_HASH = "a".repeat(64);
    private static final String PROMPT_VERSION = "stock-comparison-v1";
    private static final String MODEL_NAME = "gpt-test";
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");

    @Autowired private ComparisonBriefRepository repository;
    @Autowired private CompanyRepository companyRepository;

    @AfterEach
    void cleanUp() {
        repository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void selectsNewestMatchingCanonicalBriefWithDeterministicIdTieBreak() {
        Company apple = companyRepository.saveAndFlush(company("AAPL", "Apple Inc."));
        Company microsoft = companyRepository.saveAndFlush(company("MSFT", "Microsoft Corporation"));

        repository.saveAndFlush(brief(apple, microsoft, INPUT_HASH, PROMPT_VERSION, MODEL_NAME, NOW.minusSeconds(60)));
        repository.saveAndFlush(brief(apple, microsoft, INPUT_HASH, PROMPT_VERSION, MODEL_NAME, NOW));
        ComparisonBrief expected = repository.saveAndFlush(brief(
                apple, microsoft, INPUT_HASH, PROMPT_VERSION, MODEL_NAME, NOW));
        repository.saveAndFlush(brief(
                apple, microsoft, "b".repeat(64), PROMPT_VERSION, MODEL_NAME, NOW.plusSeconds(60)));
        repository.saveAndFlush(brief(
                apple, microsoft, INPUT_HASH, "other-prompt", MODEL_NAME, NOW.plusSeconds(60)));
        repository.saveAndFlush(brief(
                apple, microsoft, INPUT_HASH, PROMPT_VERSION, "other-model", NOW.plusSeconds(60)));

        assertThat(repository.findNewestMatchingId(
                        apple.getId(), microsoft.getId(), INPUT_HASH, PROMPT_VERSION, MODEL_NAME))
                .contains(expected.getId());
    }

    private Company company(String ticker, String name) {
        return new Company(ticker, name, "NASDAQ", null, null, "US", null, null, null,
                ticker, NOW, NOW);
    }

    private ComparisonBrief brief(
            Company left,
            Company right,
            String inputHash,
            String promptVersion,
            String modelName,
            Instant generatedAt) {
        return new ComparisonBrief(left, right, "Grounded summary.", "{}", "[]", modelName,
                promptVersion, generatedAt, NOW, inputHash);
    }
}
