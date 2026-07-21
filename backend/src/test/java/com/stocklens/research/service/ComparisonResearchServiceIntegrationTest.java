package com.stocklens.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.repository.FinancialMetricSnapshotRepository;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.repository.MarketSnapshotRepository;
import com.stocklens.research.ai.AiAdvantageResult;
import com.stocklens.research.ai.AiAdvantages;
import com.stocklens.research.ai.ComparisonAiClient;
import com.stocklens.research.context.ComparisonContextBuilder;
import com.stocklens.research.context.ComparisonSourceDataLoader;
import com.stocklens.research.domain.ComparisonBrief;
import com.stocklens.research.domain.ComparisonBriefSource;
import com.stocklens.research.dto.ComparisonResearchResponse;
import com.stocklens.research.repository.ComparisonBriefRepository;
import com.stocklens.research.repository.ComparisonBriefSourceRepository;
import com.stocklens.support.IntegrationTestContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestContainers.class)
@SpringBootTest(properties = "stocklens.research.ai.model=gpt-test")
class ComparisonResearchServiceIntegrationTest {

    @Autowired private ComparisonResearchService service;
    @Autowired private ComparisonSourceDataLoader sourceLoader;
    @Autowired private ComparisonContextBuilder contextBuilder;
    @Autowired private ComparisonInputHashService inputHashService;
    @Autowired private ComparisonBriefRepository briefRepository;
    @Autowired private ComparisonBriefSourceRepository sourceRepository;
    @Autowired private FinancialMetricSnapshotRepository metricRepository;
    @Autowired private MarketSnapshotRepository marketRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ComparisonAiClient aiClient;

    @BeforeEach
    void clearRedis() {
        flushRedis();
    }

    @AfterEach
    void cleanUp() {
        flushRedis();
        sourceRepository.deleteAllInBatch();
        briefRepository.deleteAllInBatch();
        marketRepository.deleteAllInBatch();
        metricRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void redisMissReusesMatchingPersistedBriefWithoutCallingAi() throws Exception {
        Instant now = Instant.now();
        Company apple = companyRepository.saveAndFlush(company("AAPL", "Apple Inc.", now));
        Company microsoft = companyRepository.saveAndFlush(company("MSFT", "Microsoft Corporation", now));
        metricRepository.saveAndFlush(metric(apple, "20", now));
        metricRepository.saveAndFlush(metric(microsoft, "25", now));
        marketRepository.saveAndFlush(market(apple, "210", now));
        marketRepository.saveAndFlush(market(microsoft, "500", now));

        var context = contextBuilder.build(sourceLoader.load("AAPL", "MSFT"));
        String inputHash = inputHashService.hash(context);
        ComparisonBrief brief = briefRepository.saveAndFlush(new ComparisonBrief(
                apple,
                microsoft,
                "Persisted grounded summary.",
                objectMapper.writeValueAsString(advantages()),
                "[]",
                "gpt-test",
                "stock-comparison-v1",
                now,
                context.dataCutoffAt(),
                inputHash));
        sourceRepository.saveAndFlush(new ComparisonBriefSource(
                brief, "C1", "COMPANY_PROFILE", null, null, null));

        ComparisonResearchResponse response = service.generate("AAPL", "MSFT");

        assertThat(response.id()).isEqualTo(brief.getId());
        assertThat(response.cached()).isTrue();
        assertThat(response.overallSummary()).isEqualTo("Persisted grounded summary.");
        verifyNoInteractions(aiClient);
    }

    private void flushRedis() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private Company company(String ticker, String name, Instant now) {
        return new Company(ticker, name, "NASDAQ", null, null, "US", null, null, null,
                ticker, now, now);
    }

    private FinancialMetricSnapshot metric(Company company, String pe, Instant now) {
        return new FinancialMetricSnapshot(company, new BigDecimal(pe), null, null, null, null,
                null, null, null, null, null, null, null, null, "USD",
                LocalDate.of(2026, 6, 30), now, "FMP", null);
    }

    private MarketSnapshot market(Company company, String price, Instant now) {
        return new MarketSnapshot(company, new BigDecimal(price), null, null, null, "USD",
                now, now, "FMP", null);
    }

    private AiAdvantages advantages() {
        AiAdvantageResult advantage = new AiAdvantageResult(
                "AAPL", "Grounded by the company profile.", List.of("C1"));
        return new AiAdvantages(advantage, advantage, advantage, advantage);
    }
}
