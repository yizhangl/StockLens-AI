package com.stocklens.news.service;

import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.domain.NewsArticle;
import com.stocklens.news.repository.NewsArticleRepository;
import com.stocklens.news.repository.NewsRetrievalRepository;
import com.stocklens.news.domain.NewsRetrieval;
import com.stocklens.news.service.CanonicalArticleUrlService.CanonicalArticleUrl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsArticlePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(NewsArticlePersistenceService.class);

    private final NewsArticleRepository articleRepository;
    private final CompanyRepository companyRepository;
    private final CanonicalArticleUrlService canonicalUrlService;
    private final NewsRetrievalRepository retrievalRepository;

    public NewsArticlePersistenceService(
            NewsArticleRepository articleRepository,
            CompanyRepository companyRepository,
            CanonicalArticleUrlService canonicalUrlService, NewsRetrievalRepository retrievalRepository) {
        this.articleRepository = articleRepository;
        this.companyRepository = companyRepository;
        this.canonicalUrlService = canonicalUrlService;
        this.retrievalRepository = retrievalRepository;
    }

    @Transactional
    public PersistenceResult persistAndLoadRecent(
            Company company, NewsFetchResult fetchResult, int limit) {
        Company managedCompany = companyRepository.findById(company.getId())
                .orElseThrow(() -> new IllegalStateException("Resolved company no longer exists."));
        CandidateResult candidateResult = candidates(fetchResult);

        if (fetchResult.articles().isEmpty()) {
            retrievalRepository.saveAndFlush(new NewsRetrieval(managedCompany, fetchResult.retrievedAt(), 0, fetchResult.providerName()));
            return new PersistenceResult(List.of(), candidateResult.skippedArticleCount());
        }
        if (candidateResult.candidates().isEmpty()) {
            throw new NewsProviderException(
                    "News provider response did not contain a valid article.");
        }

        for (Candidate candidate : candidateResult.candidates()) {
            NewsArticleData data = candidate.data();
            articleRepository.insertIfAbsent(
                    normalizeExternalId(data.externalId()),
                    data.headline(),
                    data.sourceName(),
                    candidate.canonicalUrl().url(),
                    data.description(),
                    data.publishedAt(),
                    data.retrievedAt(),
                    candidate.canonicalUrl().hash(),
                    data.providerName());

            NewsArticle article = findAuthoritativeArticle(candidate);
            article.updateContent(
                    data.headline(),
                    data.sourceName(),
                    data.description(),
                    data.publishedAt(),
                    data.retrievedAt());
            articleRepository.associateWithCompany(article.getId(), managedCompany.getId());
        }
        articleRepository.flush();
        retrievalRepository.saveAndFlush(new NewsRetrieval(managedCompany, fetchResult.retrievedAt(), candidateResult.candidates().size(), fetchResult.providerName()));

        List<Long> recentIds = articleRepository.findRecentIdsByCompanyId(
                managedCompany.getId(), PageRequest.of(0, limit));
        if (recentIds.isEmpty()) {
            return new PersistenceResult(List.of(), candidateResult.skippedArticleCount());
        }
        Map<Long, NewsArticle> byId = new HashMap<>();
        articleRepository.findAllByIdsWithCompanies(recentIds)
                .forEach(article -> byId.put(article.getId(), article));
        List<NewsArticle> ordered = recentIds.stream().map(byId::get).toList();
        return new PersistenceResult(ordered, candidateResult.skippedArticleCount());
    }

    private CandidateResult candidates(NewsFetchResult fetchResult) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> externalKeys = new HashSet<>();
        Set<String> urlHashes = new HashSet<>();
        int skipped = fetchResult.skippedArticleCount();

        for (NewsArticleData data : fetchResult.articles()) {
            try {
                validateForPersistence(data);
                CanonicalArticleUrl canonicalUrl = canonicalUrlService.canonicalize(data.articleUrl());
                String externalId = normalizeExternalId(data.externalId());
                String externalKey = externalId == null
                        ? null
                        : data.providerName() + "\u0000" + externalId;
                if ((externalKey != null && !externalKeys.add(externalKey))
                        || !urlHashes.add(canonicalUrl.hash())) {
                    continue;
                }
                candidates.add(new Candidate(data, canonicalUrl));
            } catch (IllegalArgumentException exception) {
                skipped++;
                log.warn(
                        "Skipping invalid normalized news article exceptionType={}",
                        exception.getClass().getSimpleName());
            }
        }
        return new CandidateResult(List.copyOf(candidates), skipped);
    }

    private void validateForPersistence(NewsArticleData data) {
        if (data == null
                || data.headline() == null
                || data.headline().isBlank()
                || data.headline().length() > 1000
                || (data.sourceName() != null
                        && (data.sourceName().isBlank() || data.sourceName().length() > 255))
                || data.publishedAt() == null
                || data.retrievedAt() == null
                || data.providerName() == null
                || data.providerName().isBlank()
                || data.providerName().length() > 64
                || (normalizeExternalId(data.externalId()) != null
                        && normalizeExternalId(data.externalId()).length() > 255)) {
            throw new IllegalArgumentException("News article does not fit the normalized schema.");
        }
    }

    private String normalizeExternalId(String externalId) {
        return externalId == null || externalId.isBlank() ? null : externalId.trim();
    }

    private NewsArticle findAuthoritativeArticle(Candidate candidate) {
        String externalId = normalizeExternalId(candidate.data().externalId());
        if (externalId != null) {
            var byExternalId = articleRepository.findByProviderNameAndExternalId(
                    candidate.data().providerName(), externalId);
            if (byExternalId.isPresent()) {
                return byExternalId.get();
            }
        }
        return articleRepository.findByUrlHash(candidate.canonicalUrl().hash())
                .orElseThrow(() -> new IllegalStateException("Conflict-safe news insert was not visible."));
    }

    public record PersistenceResult(
            List<NewsArticle> articles, int skippedArticleCount) {

        public PersistenceResult {
            articles = List.copyOf(articles);
        }
    }

    private record Candidate(NewsArticleData data, CanonicalArticleUrl canonicalUrl) {}

    private record CandidateResult(List<Candidate> candidates, int skippedArticleCount) {}
}
