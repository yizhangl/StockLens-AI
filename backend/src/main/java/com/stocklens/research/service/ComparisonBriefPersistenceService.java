package com.stocklens.research.service;

import com.stocklens.research.ai.AiComparisonResult;
import com.stocklens.research.context.BuiltComparisonContext;
import com.stocklens.research.context.GroundedSource;
import com.stocklens.research.domain.ComparisonBrief;
import com.stocklens.research.domain.ComparisonBriefSource;
import com.stocklens.research.repository.ComparisonBriefRepository;
import com.stocklens.research.repository.ComparisonBriefSourceRepository;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComparisonBriefPersistenceService {

    private final ComparisonBriefRepository briefRepository;
    private final ComparisonBriefSourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public ComparisonBriefPersistenceService(ComparisonBriefRepository briefRepository,
            ComparisonBriefSourceRepository sourceRepository, ObjectMapper objectMapper) {
        this.briefRepository = briefRepository;
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ComparisonBrief persist(BuiltComparisonContext context, AiComparisonResult result,
            String modelName, Instant generatedAt, String inputHash) {
        try {
            boolean leftFirst = context.leftCompany().getTicker().compareTo(context.rightCompany().getTicker()) <= 0;
            ComparisonBrief brief = briefRepository.saveAndFlush(new ComparisonBrief(
                    leftFirst ? context.leftCompany() : context.rightCompany(),
                    leftFirst ? context.rightCompany() : context.leftCompany(),
                    result.overallSummary(),
                    objectMapper.writeValueAsString(result.advantages()),
                    objectMapper.writeValueAsString(result.keyRisks()),
                    modelName,
                    "stock-comparison-v1",
                    generatedAt,
                    context.dataCutoffAt(),
                    inputHash));
            List<ComparisonBriefSource> links = result.sourceIds().stream()
                    .map(context.sourcesById()::get)
                    .map(source -> link(brief, source))
                    .toList();
            sourceRepository.saveAllAndFlush(links);
            return brief;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist validated AI comparison brief", exception);
        }
    }

    private ComparisonBriefSource link(ComparisonBrief brief, GroundedSource source) {
        return new ComparisonBriefSource(brief, source.id(), source.type().name(), source.newsArticle(),
                source.financialSnapshot(), source.marketSnapshot());
    }
}
