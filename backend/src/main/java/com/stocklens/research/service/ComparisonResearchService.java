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
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
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
    private final JsonRedisCache cache;
    private final StockLensCacheKeys cacheKeys;
    private final StockLensCacheProperties cacheProperties;

    public ComparisonResearchService(ComparisonSourceDataLoader sourceLoader,
            ComparisonContextBuilder contextBuilder, AiPromptTemplate promptTemplate,
            ComparisonAiClient aiClient, AiComparisonValidator validator,
            ComparisonInputHashService inputHashService, ComparisonBriefPersistenceService persistenceService,
            AiComparisonProperties properties, Clock clock, JsonRedisCache cache, StockLensCacheKeys cacheKeys,
            StockLensCacheProperties cacheProperties) {
        this.sourceLoader = sourceLoader; this.contextBuilder = contextBuilder;
        this.promptTemplate = promptTemplate; this.aiClient = aiClient; this.validator = validator;
        this.inputHashService = inputHashService; this.persistenceService = persistenceService;
        this.properties = properties; this.clock = clock;
        this.cache = cache; this.cacheKeys = cacheKeys; this.cacheProperties = cacheProperties;
    }

    public ComparisonResearchResponse generate(String rawLeftTicker, String rawRightTicker) {
        return generate(rawLeftTicker, rawRightTicker, false);
    }

    public ComparisonResearchResponse generate(String rawLeftTicker, String rawRightTicker, boolean forceRefresh) {
        BuiltComparisonContext context = contextBuilder.build(sourceLoader.load(rawLeftTicker, rawRightTicker));
        String inputHash = inputHashService.hash(context);
        String cacheKey = cacheKeys.brief(context.leftCompany().getTicker(), context.rightCompany().getTicker(), inputHash);
        if (!forceRefresh) {
            var cached = cache.get(cacheKey, ComparisonResearchResponse.class);
            if (cached.isPresent()) return orient(cached.get(), context.leftCompany().getTicker(), context.rightCompany().getTicker(), true);
        }
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
        ComparisonResearchResponse response = response(brief, context, validation.result());
        cache.put(cacheKey, response, cacheProperties.briefTtl());
        return response;
    }

    private ComparisonResearchResponse response(ComparisonBrief brief, BuiltComparisonContext context,
            AiComparisonResult result) {
        return new ComparisonResearchResponse(brief.getId(), context.leftCompany().getTicker(),
                context.rightCompany().getTicker(), result.overallSummary(), advantages(result),
                result.keyRisks().stream().map(risk -> new RiskResponse(risk.ticker(), risk.text(), risk.sourceIds())).toList(),
                result.sourceIds().stream().map(context.sourcesById()::get).map(this::source).toList(),
                brief.getModelName(), brief.getPromptVersion(), brief.getGeneratedAt(), brief.getDataCutoffAt(), false);
    }

    private ComparisonResearchResponse orient(ComparisonResearchResponse response, String left, String right, boolean cached) {
        return new ComparisonResearchResponse(response.id(), left, right, response.overallSummary(), response.advantages(),
                response.keyRisks(), response.sources(), response.modelName(), response.promptVersion(), response.generatedAt(),
                response.dataCutoffAt(), cached);
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
