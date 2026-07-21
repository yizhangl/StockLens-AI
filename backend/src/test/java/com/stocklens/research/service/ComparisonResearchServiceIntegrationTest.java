package com.stocklens.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.repository.FinancialMetricSnapshotRepository;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.repository.MarketSnapshotRepository;
import com.stocklens.research.ai.AiAdvantageResult;
import com.stocklens.research.ai.AiAdvantages;
import com.stocklens.research.ai.AiComparisonPrompt;
import com.stocklens.research.ai.AiComparisonResult;
import com.stocklens.research.ai.AiRiskResult;
import com.stocklens.research.ai.ComparisonAiClient;
import com.stocklens.research.ai.InvalidAiResponseException;
import com.stocklens.research.context.BuiltComparisonContext;
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
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;
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

    @Test
    void overLimitFirstResponseThenValidRepairSucceedsWithExactlyTwoAiCalls() {
        BuiltComparisonContext context = persistComparisonInputsWithManySources();
        when(aiClient.generate(any(AiComparisonPrompt.class)))
                .thenReturn(resultWithSourceCount(context, 16), resultWithSourceCount(context, 15));

        ComparisonResearchResponse response = service.generate("AAPL", "MSFT");

        ArgumentCaptor<AiComparisonPrompt> promptCaptor = ArgumentCaptor.forClass(AiComparisonPrompt.class);
        verify(aiClient, times(2)).generate(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).userMessage())
                .contains("Validation failures: too many source IDs")
                .contains("previous output exceeded 15 unique source IDs");
        assertThat(response.sources()).hasSize(15);
        assertThat(briefRepository.count()).isOne();
        assertThat(redisTemplate.keys("stocklens:brief:*")).hasSize(1);
    }

    @Test
    void stillInvalidRepairIsNeitherPersistedNorCached() {
        BuiltComparisonContext context = persistComparisonInputsWithManySources();
        AiComparisonResult overLimit = resultWithSourceCount(context, 16);
        when(aiClient.generate(any(AiComparisonPrompt.class))).thenReturn(overLimit, overLimit);

        assertThatThrownBy(() -> service.generate("AAPL", "MSFT"))
                .isInstanceOf(InvalidAiResponseException.class);

        verify(aiClient, times(2)).generate(any(AiComparisonPrompt.class));
        assertThat(briefRepository.count()).isZero();
        assertThat(redisTemplate.keys("stocklens:brief:*")).isEmpty();
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

    private BuiltComparisonContext persistComparisonInputsWithManySources() {
        Instant now = Instant.now();
        Company apple = companyRepository.saveAndFlush(company("AAPL", "Apple Inc.", now));
        Company microsoft = companyRepository.saveAndFlush(company("MSFT", "Microsoft Corporation", now));
        metricRepository.saveAndFlush(completeMetric(apple, now));
        metricRepository.saveAndFlush(completeMetric(microsoft, now));
        marketRepository.saveAndFlush(market(apple, "210", now));
        marketRepository.saveAndFlush(market(microsoft, "500", now));
        return contextBuilder.build(sourceLoader.load("AAPL", "MSFT"));
    }

    private FinancialMetricSnapshot completeMetric(Company company, Instant now) {
        BigDecimal value = BigDecimal.ONE;
        return new FinancialMetricSnapshot(company, value, value, value, value, value, value, value,
                value, value, value, value, value, value, "USD", LocalDate.of(2026, 6, 30),
                now, "FMP", null);
    }

    private AiComparisonResult resultWithSourceCount(BuiltComparisonContext context, int count) {
        List<String> ids = context.sources().stream().map(source -> source.id()).limit(count).toList();
        AiAdvantages resultAdvantages = new AiAdvantages(
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
                resultAdvantages, risks, List.of());
    }

    private AiAdvantageResult advantage(String winner, List<String> sourceIds) {
        return new AiAdvantageResult(winner, "Grounded by supplied StockLens data.", sourceIds);
    }

    private AiAdvantages advantages() {
        AiAdvantageResult advantage = new AiAdvantageResult(
                "AAPL", "Grounded by the company profile.", List.of("C1"));
        return new AiAdvantages(advantage, advantage, advantage, advantage);
    }
}
