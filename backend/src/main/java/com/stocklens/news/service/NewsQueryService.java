package com.stocklens.news.service;

import com.stocklens.common.exception.InvalidNewsLimitException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.company.service.CompanyService;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.news.client.NewsDataClient;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.domain.NewsArticle;
import com.stocklens.news.dto.NewsArticleResponse;
import com.stocklens.news.dto.NewsResponse;
import com.stocklens.news.dto.NewsWarningResponse;
import com.stocklens.news.service.NewsArticlePersistenceService.PersistenceResult;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NewsQueryService {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 20;
    private static final int MIN_CANDIDATE_LIMIT = 10;
    private static final int MAX_CANDIDATE_LIMIT = 40;

    private final TickerNormalizer tickerNormalizer;
    private final CompanyRepository companyRepository;
    private final FinancialDataClient financialDataClient;
    private final CompanyService companyService;
    private final NewsDataClient newsDataClient;
    private final NewsArticlePersistenceService persistenceService;
    private final NewsArticleRelevanceService relevanceService;

    public NewsQueryService(
            TickerNormalizer tickerNormalizer,
            CompanyRepository companyRepository,
            FinancialDataClient financialDataClient,
            CompanyService companyService,
            NewsDataClient newsDataClient,
            NewsArticlePersistenceService persistenceService,
            NewsArticleRelevanceService relevanceService) {
        this.tickerNormalizer = tickerNormalizer;
        this.companyRepository = companyRepository;
        this.financialDataClient = financialDataClient;
        this.companyService = companyService;
        this.newsDataClient = newsDataClient;
        this.persistenceService = persistenceService;
        this.relevanceService = relevanceService;
    }

    public NewsResponse getRecentNews(String rawTicker, int limit) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        validateLimit(limit);
        Company company = companyRepository.findByTicker(ticker)
                .orElseGet(() -> companyService.upsert(
                        financialDataClient.getCompanyProfile(ticker)));
        NewsFetchResult fetchResult = newsDataClient.getRecentNews(ticker, limit);
        NewsFetchResult relevantFetchResult = new NewsFetchResult(
                relevanceService.filterRelevant(company, fetchResult.articles()),
                fetchResult.skippedArticleCount(),
                fetchResult.providerName(),
                fetchResult.retrievedAt());
        PersistenceResult persisted = persistenceService.persistAndLoadRecent(
                company, relevantFetchResult, candidateLimit(limit));

        List<NewsArticle> relevantArticles = persisted.articles().stream()
                .filter(article -> relevanceService.assess(company, article).isRelevant())
                .sorted(Comparator
                        .comparing(NewsArticle::getPublishedAt)
                        .reversed()
                        .thenComparing(NewsArticle::getHeadline))
                .limit(limit)
                .toList();

        List<NewsWarningResponse> warnings = persisted.skippedArticleCount() == 0
                ? List.of()
                : List.of(new NewsWarningResponse(
                        "INVALID_PROVIDER_RECORDS_SKIPPED",
                        "Some news records were unavailable because the provider data was invalid.",
                        persisted.skippedArticleCount()));
        return new NewsResponse(
                ticker,
                limit,
                fetchResult.providerName(),
                fetchResult.retrievedAt(),
                relevantArticles.stream().map(this::toResponse).toList(),
                warnings);
    }

    private int candidateLimit(int publicLimit) {
        return Math.min(
                MAX_CANDIDATE_LIMIT,
                Math.max(MIN_CANDIDATE_LIMIT, publicLimit * 2));
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidNewsLimitException();
        }
    }

    private NewsArticleResponse toResponse(NewsArticle article) {
        List<String> relatedSymbols = article.getCompanies().stream()
                .map(Company::getTicker)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new NewsArticleResponse(
                article.getId(),
                article.getHeadline(),
                article.getSourceName(),
                article.getArticleUrl(),
                article.getPublishedAt(),
                article.getDescription(),
                relatedSymbols);
    }
}
