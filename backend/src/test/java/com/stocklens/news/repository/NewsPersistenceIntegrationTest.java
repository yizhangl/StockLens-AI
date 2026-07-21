package com.stocklens.news.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.domain.NewsArticle;
import com.stocklens.news.service.NewsArticlePersistenceService;
import com.stocklens.support.IntegrationTestContainers;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(IntegrationTestContainers.class)
@SpringBootTest
class NewsPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-18T20:00:00Z");

    @Autowired private NewsArticleRepository articleRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private NewsArticlePersistenceService persistenceService;
    @Autowired private NewsRetrievalRepository retrievalRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        retrievalRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM news_article_company");
        articleRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void persistsSuccessfulEmptyRetrievalAndSelectsNewestMarkerDeterministically() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));

        persistenceService.persistAndLoadRecent(
                apple, new NewsFetchResult(List.of(), 0, "YAHOO_FINANCE", NOW.minusSeconds(60)), 10);
        persistenceService.persistAndLoadRecent(
                apple, new NewsFetchResult(List.of(), 0, "YAHOO_FINANCE", NOW), 10);

        assertThat(retrievalRepository.count()).isEqualTo(2);
        assertThat(retrievalRepository.findFirstByCompany_IdOrderByRetrievedAtDescIdDesc(apple.getId()))
                .get().satisfies(marker -> {
                    assertThat(marker.getRetrievedAt()).isEqualTo(NOW);
                    assertThat(marker.getResultCount()).isZero();
                });
    }

    @Test
    void repeatedPersistenceUsesOneCanonicalArticleAndOneRelationship() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));

        persistenceService.persistAndLoadRecent(
                apple, fetch(article(
                        "HTTPS://Example.COM/story?edition=us#first", "First headline", NOW)), 10);
        var repeated = persistenceService.persistAndLoadRecent(
                apple, fetch(article(
                        "https://example.com/story?edition=us#second", "Updated headline", NOW)), 10);

        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(relationshipCount()).isEqualTo(1);
        assertThat(repeated.articles()).singleElement().satisfies(article -> {
            assertThat(article.getArticleUrl()).isEqualTo("https://example.com/story?edition=us");
            assertThat(article.getHeadline()).isEqualTo("Updated headline");
            assertThat(article.getCompanies()).extracting(Company::getTicker).containsExactly("AAPL");
        });
    }

    @Test
    void duplicateRowsInsideOneProviderResponseAreCollapsed() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));

        persistenceService.persistAndLoadRecent(apple, new NewsFetchResult(List.of(
                article("HTTPS://Example.COM/duplicate#first", "First copy", NOW),
                article("https://example.com/duplicate#second", "Second copy", NOW)),
                0, "YAHOO_FINANCE", NOW), 10);

        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(relationshipCount()).isEqualTo(1);
    }

    @Test
    void allMalformedUrlsProduceControlledNewsProviderError() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));

        assertThatThrownBy(() -> persistenceService.persistAndLoadRecent(
                        apple,
                        fetch(article("javascript:alert(1)", "Unsafe", NOW)),
                        10))
                .isInstanceOf(com.stocklens.common.exception.NewsProviderException.class)
                .hasMessageContaining("valid article");
        assertThat(articleRepository.count()).isZero();
        assertThat(relationshipCount()).isZero();
    }

    @Test
    void oneArticleCanBeAssociatedWithTwoCompanies() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));
        Company microsoft = companyRepository.saveAndFlush(company("MSFT"));
        NewsArticleData appleArticle = article(
                "https://example.com/shared-story", "Shared story", NOW);
        NewsArticleData microsoftArticle = new NewsArticleData(
                null,
                appleArticle.headline(),
                appleArticle.sourceName(),
                appleArticle.articleUrl(),
                appleArticle.description(),
                appleArticle.publishedAt(),
                Set.of("MSFT"),
                appleArticle.retrievedAt(),
                appleArticle.providerName());

        persistenceService.persistAndLoadRecent(apple, fetch(appleArticle), 10);
        var result = persistenceService.persistAndLoadRecent(
                microsoft, fetch(microsoftArticle), 10);

        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(relationshipCount()).isEqualTo(2);
        assertThat(result.articles().getFirst().getCompanies())
                .extracting(Company::getTicker)
                .containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    void recentQueryOrdersByPublicationAndEnforcesLimit() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));
        persistenceService.persistAndLoadRecent(apple, new NewsFetchResult(List.of(
                article("https://example.com/old", "Old", NOW.minusSeconds(300)),
                article("https://example.com/new", "New", NOW),
                article("https://example.com/middle", "Middle", NOW.minusSeconds(60))),
                0, "YAHOO_FINANCE", NOW), 3);

        var result = persistenceService.persistAndLoadRecent(
                apple, fetch(article("https://example.com/new", "New", NOW)), 2);

        assertThat(result.articles()).extracting(NewsArticle::getHeadline)
                .containsExactly("New", "Middle");
    }

    @Test
    void databaseEnforcesUrlHashProviderIdAndJoinUniqueness() {
        Company apple = companyRepository.saveAndFlush(company("AAPL"));
        NewsArticle first = articleEntity("provider-1", "a".repeat(64));
        NewsArticle saved = articleRepository.saveAndFlush(first);

        assertThatThrownBy(() -> articleRepository.saveAndFlush(
                        articleEntity("provider-2", "a".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> articleRepository.saveAndFlush(
                        articleEntity("provider-1", "b".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                "INSERT INTO news_article_company (news_article_id, company_id) VALUES (?, ?)",
                saved.getId(), apple.getId());
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO news_article_company (news_article_id, company_id) VALUES (?, ?)",
                        saved.getId(), apple.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesForeignKeysAndHashFormat() {
        assertThatThrownBy(() -> articleRepository.saveAndFlush(
                        articleEntity(null, "not-a-sha256")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO news_article_company (news_article_id, company_id) VALUES (?, ?)",
                        Long.MAX_VALUE, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long relationshipCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM news_article_company", Long.class);
    }

    private Company company(String ticker) {
        return new Company(
                ticker, ticker + " Inc.", "NASDAQ", null, null, null, null, null, null,
                ticker, NOW, NOW);
    }

    private NewsFetchResult fetch(NewsArticleData article) {
        return new NewsFetchResult(List.of(article), 0, "YAHOO_FINANCE", NOW);
    }

    private NewsArticleData article(String url, String headline, Instant publishedAt) {
        return new NewsArticleData(
                null,
                headline,
                "Example Wire",
                url,
                "Description",
                publishedAt,
                Set.of("AAPL"),
                NOW,
                "YAHOO_FINANCE");
    }

    private NewsArticle articleEntity(String externalId, String hash) {
        return new NewsArticle(
                externalId,
                "Headline",
                "Example Wire",
                "https://example.com/" + hash,
                null,
                NOW,
                NOW,
                hash,
                "YAHOO_FINANCE");
    }
}
