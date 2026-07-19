package com.stocklens.news.client.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.news.client.fmp.dto.FmpNewsResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FmpNewsResponseMapperTest {

    private static final Instant RETRIEVED_AT = Instant.parse("2026-07-18T20:00:00Z");

    private final FmpNewsResponseMapper mapper = new FmpNewsResponseMapper();

    @Test
    void mapsVerifiedFieldsSortsNewestFirstAndKeepsAbsentExternalIdNull() {
        var result = mapper.toNews(List.of(
                response("2026-07-17 09:15:00", "Older", null, "Example Wire"),
                response("2026-07-18 18:30:00", "Newer", "Description", "Publisher")),
                "AAPL", RETRIEVED_AT);

        assertThat(result.providerName()).isEqualTo("FMP");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.skippedArticleCount()).isZero();
        assertThat(result.articles()).extracting(article -> article.headline())
                .containsExactly("Newer", "Older");
        assertThat(result.articles().getFirst()).satisfies(article -> {
            assertThat(article.externalId()).isNull();
            assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-07-18T18:30:00Z"));
            assertThat(article.relatedSymbols()).containsExactly("AAPL");
            assertThat(article.providerName()).isEqualTo("FMP");
        });
        assertThat(result.articles().getLast().description()).isNull();
    }

    @Test
    void normalizesEncodedAndActualHtmlToPlainTextAndUsesSiteFallback() {
        FmpNewsResponse response = new FmpNewsResponse(
                "AAPL",
                "2026-07-18 18:30:00",
                " ",
                "Apple &amp; partners <strong>announce</strong> news",
                null,
                "example.com",
                "&lt;em&gt;Encoded&lt;/em&gt; and <b>actual</b> markup",
                "https://example.com/story");

        var article = mapper.toNews(List.of(response), "AAPL", RETRIEVED_AT)
                .articles().getFirst();

        assertThat(article.headline()).isEqualTo("Apple & partners announce news");
        assertThat(article.description()).isEqualTo("Encoded and actual markup");
        assertThat(article.sourceName()).isEqualTo("example.com");
    }

    @Test
    void skipsMalformedRowsWhilePreservingValidRows() {
        var result = mapper.toNews(List.of(
                response("2026-07-18 18:30:00", "Valid", "Description", "Publisher"),
                new FmpNewsResponse(
                        "AAPL", "2026-07-18 18:30:00", "Publisher", " ", null,
                        "example.com", "Description", "https://example.com/missing-headline"),
                new FmpNewsResponse(
                        "AAPL", "invalid", "Publisher", "Invalid date", null,
                        "example.com", null, "https://example.com/invalid-date"),
                new FmpNewsResponse(
                        "MSFT", "2026-07-18 18:30:00", "Publisher", "Wrong symbol", null,
                        "example.com", null, "https://example.com/wrong-symbol")),
                "AAPL", RETRIEVED_AT);

        assertThat(result.articles()).hasSize(1);
        assertThat(result.skippedArticleCount()).isEqualTo(3);
    }

    @Test
    void rejectsAllInvalidNonEmptyRowsButAcceptsAnActualEmptyList() {
        assertThatThrownBy(() -> mapper.toNews(List.of(
                        new FmpNewsResponse(
                                "AAPL", null, null, "Headline", null, null, null,
                                "https://example.com/story"),
                        new FmpNewsResponse(
                                "AAPL", "2026-07-18 18:30:00", null, "Headline", null,
                                null, null, null)),
                        "AAPL", RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("valid article");

        var empty = mapper.toNews(List.of(), "AAPL", RETRIEVED_AT);
        assertThat(empty.articles()).isEmpty();
        assertThat(empty.skippedArticleCount()).isZero();
    }

    private FmpNewsResponse response(
            String publishedDate, String title, String text, String publisher) {
        return new FmpNewsResponse(
                "AAPL",
                publishedDate,
                publisher,
                title,
                null,
                "example.com",
                text,
                "https://example.com/" + title.toLowerCase());
    }
}
