package com.stocklens.research.service;

import com.stocklens.research.ai.AiAdvantageResult;
import com.stocklens.research.ai.AiComparisonProperties;
import com.stocklens.research.ai.AiComparisonResult;
import com.stocklens.research.ai.AiComparisonValidator;
import com.stocklens.research.ai.AiComparisonValidator.ValidationResult;
import com.stocklens.research.ai.AiPromptTemplate;
import com.stocklens.research.ai.ComparisonAiClient;
import com.stocklens.research.ai.InvalidAiResponseException;
import com.stocklens.research.context.BuiltComparisonContext;
import com.stocklens.research.context.ComparisonContextBuilder;
import com.stocklens.research.context.ComparisonSourceDataLoader;
import com.stocklens.research.context.GroundedSource;
import com.stocklens.research.domain.ComparisonBrief;
import com.stocklens.research.dto.AdvantageResponse;
import com.stocklens.research.dto.AdvantagesResponse;
import com.stocklens.research.dto.ComparisonResearchResponse;
import com.stocklens.research.dto.GroundedSourceResponse;
import com.stocklens.research.dto.RiskResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ComparisonResearchService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonResearchService.class);
    private final ComparisonSourceDataLoader sourceLoader;
    private final ComparisonContextBuilder contextBuilder;
    private final AiPromptTemplate promptTemplate;
    private final ComparisonAiClient aiClient;
    private final AiComparisonValidator validator;
    private final ComparisonInputHashService inputHashService;
    private final ComparisonBriefPersistenceService persistenceService;
    private final AiComparisonProperties properties;
    private final Clock clock;

    public ComparisonResearchService(ComparisonSourceDataLoader sourceLoader,
            ComparisonContextBuilder contextBuilder, AiPromptTemplate promptTemplate,
            ComparisonAiClient aiClient, AiComparisonValidator validator,
            ComparisonInputHashService inputHashService, ComparisonBriefPersistenceService persistenceService,
            AiComparisonProperties properties, Clock clock) {
        this.sourceLoader = sourceLoader; this.contextBuilder = contextBuilder;
        this.promptTemplate = promptTemplate; this.aiClient = aiClient; this.validator = validator;
        this.inputHashService = inputHashService; this.persistenceService = persistenceService;
        this.properties = properties; this.clock = clock;
    }

    public ComparisonResearchResponse generate(String rawLeftTicker, String rawRightTicker) {
        BuiltComparisonContext context = contextBuilder.build(sourceLoader.load(rawLeftTicker, rawRightTicker));
        String inputHash = inputHashService.hash(context);
        ValidationResult validation = validator.validate(aiClient.generate(promptTemplate.build(context)), context);
        boolean repaired = false;
        if (!validation.isValid()) {
            repaired = true;
            validation = validator.validate(aiClient.generate(
                    promptTemplate.build(context).withRepair(validation.sanitizedFailures())), context);
        }
        if (!validation.isValid()) {
            log.warn("AI comparison validation failed left={} right={} repaired={} categories={}",
                    context.leftCompany().getTicker(), context.rightCompany().getTicker(), repaired,
                    validation.sanitizedFailures());
            throw new InvalidAiResponseException();
        }
        Instant generatedAt = Instant.now(clock);
        ComparisonBrief brief = persistenceService.persist(context, validation.result(), properties.model(), generatedAt, inputHash);
        return response(brief, context, validation.result());
    }

    private ComparisonResearchResponse response(ComparisonBrief brief, BuiltComparisonContext context,
            AiComparisonResult result) {
        return new ComparisonResearchResponse(brief.getId(), context.leftCompany().getTicker(),
                context.rightCompany().getTicker(), result.overallSummary(), advantages(result),
                result.keyRisks().stream().map(risk -> new RiskResponse(risk.ticker(), risk.text(), risk.sourceIds())).toList(),
                result.sourceIds().stream().map(context.sourcesById()::get).map(this::source).toList(),
                brief.getModelName(), brief.getPromptVersion(), brief.getGeneratedAt(), brief.getDataCutoffAt(), false);
    }

    private AdvantagesResponse advantages(AiComparisonResult result) {
        return new AdvantagesResponse(advantage(result.advantages().valuation()), advantage(result.advantages().profitability()),
                advantage(result.advantages().growth()), advantage(result.advantages().financialHealth()));
    }
    private AdvantageResponse advantage(AiAdvantageResult result) {
        return new AdvantageResponse(result.winner(), result.explanation(), result.sourceIds());
    }
    private GroundedSourceResponse source(GroundedSource source) {
        return new GroundedSourceResponse(source.id(), source.type(), source.ticker(), source.label(),
                source.sourceName(), source.url(), source.asOf());
    }
}
