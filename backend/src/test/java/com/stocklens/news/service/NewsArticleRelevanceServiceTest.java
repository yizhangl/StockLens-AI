package com.stocklens.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.domain.Company;
import com.stocklens.news.client.model.NewsArticleData;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewsArticleRelevanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    private final NewsArticleRelevanceService service = new NewsArticleRelevanceService();
    private final Company apple = new Company(
            "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null,
            "AAPL", NOW, NOW);

    @Test
    void rejectsUnrelatedTeslaArticleTaggedWithAapl() {
        var assessment = service.assess(apple, article(
                "Elon Musk's Tesla remains a founder-led tech giant",
                "Tesla reports vehicle-delivery results.",
                Set.of("AAPL")));

        assertThat(assessment.score()).isEqualTo(1);
        assertThat(assessment.relatedTickerMatch()).isTrue();
        assertThat(assessment.isRelevant()).isFalse();
    }

    @Test
    void acceptsAppleHeadlineWithRelatedSymbol() {
        var assessment = service.assess(apple, article(
                "Apple launches a new service", null, Set.of("AAPL")));

        assertThat(assessment.score()).isEqualTo(3);
        assertThat(assessment.headlineAliasMatch()).isTrue();
        assertThat(assessment.isRelevant()).isTrue();
    }

    @Test
    void acceptsAppleHeadlineWithoutRelatedSymbols() {
        var assessment = service.assess(apple, article(
                "APPLE shares reach a record", null, Set.of()));

        assertThat(assessment.score()).isEqualTo(2);
        assertThat(assessment.relatedTickerMatch()).isFalse();
        assertThat(assessment.isRelevant()).isTrue();
    }

    @Test
    void acceptsDescriptionOnlyMentionWithRelatedSymbol() {
        var assessment = service.assess(apple, article(
                "Technology shares move higher",
                "Investors focused on results from Apple Inc.",
                Set.of("aapl")));

        assertThat(assessment.score()).isEqualTo(2);
        assertThat(assessment.descriptionAliasMatch()).isTrue();
        assertThat(assessment.relatedTickerMatch()).isTrue();
        assertThat(assessment.isRelevant()).isTrue();
    }

    @Test
    void rejectsUnrelatedArticleWithNoEvidence() {
        assertThat(service.assess(apple, article(
                        "Oil prices move higher",
                        "Energy companies led the market.",
                        Set.of("XOM")))
                .isRelevant()).isFalse();
    }

    @Test
    void rejectsNullProviderRecordWithoutFailingTheWholeFilter() {
        assertThat(service.filterRelevant(apple, java.util.Arrays.asList(
                        null,
                        article("Apple reports results", null, Set.of()))))
                .hasSize(1);
    }

    @Test
    void derivesSafeShortNameAndUsesTokenAwareMatching() {
        assertThat(service.assess(apple, article(
                        "Apple unveils updated hardware", null, Set.of()))
                .headlineAliasMatch()).isTrue();
        assertThat(service.assess(apple, article(
                        "Pineapple exports increase", null, Set.of("AAPL")))
                .isRelevant()).isFalse();
    }

    private NewsArticleData article(
            String headline, String description, Set<String> relatedSymbols) {
        return new NewsArticleData(
                null,
                headline,
                "Example Wire",
                "https://example.com/article",
                description,
                NOW,
                relatedSymbols,
                NOW,
                "YAHOO_FINANCE");
    }
}
