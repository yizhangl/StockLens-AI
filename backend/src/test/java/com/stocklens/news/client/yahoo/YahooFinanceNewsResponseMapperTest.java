package com.stocklens.news.client.yahoo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.ArticleUrl;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.Content;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.Data;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.Provider;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.StreamItem;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.TickerStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class YahooFinanceNewsResponseMapperTest {

    private static final Instant RETRIEVED_AT = Instant.parse("2026-07-19T08:00:00Z");

    private final YahooFinanceNewsResponseMapper mapper =
            new YahooFinanceNewsResponseMapper();

    @Test
    void mapsOnlyVerifiedFieldsUsesCanonicalUrlAndSortsNewestFirst() {
        var result = mapper.toNews(response(List.of(
                story("outer-old", "content-old", "Older", "2026-07-17T09:15:00Z"),
                story("outer-new", "content-new", "Newer", "2026-07-18T18:30:00Z"))),
                "AAPL", RETRIEVED_AT);

        assertThat(result.providerName()).isEqualTo("YAHOO_FINANCE");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.skippedArticleCount()).isZero();
        assertThat(result.articles()).extracting(article -> article.headline())
                .containsExactly("Newer", "Older");
        assertThat(result.articles().getFirst()).satisfies(article -> {
            assertThat(article.externalId()).isEqualTo("content-new");
            assertThat(article.sourceName()).isEqualTo("Example Wire");
            assertThat(article.articleUrl()).isEqualTo("https://publisher.example/Newer");
            assertThat(article.description()).isEqualTo("Summary for Newer");
            assertThat(article.publishedAt())
                    .isEqualTo(Instant.parse("2026-07-18T18:30:00Z"));
            assertThat(article.relatedSymbols()).isEmpty();
            assertThat(article.providerName()).isEqualTo("YAHOO_FINANCE");
            assertThat(article.retrievedAt()).isEqualTo(RETRIEVED_AT);
        });
    }

    @Test
    void normalizesPlainTextAndUsesVerifiedOptionalFallbacks() {
        Content content = new Content(
                null,
                "STORY",
                "Apple &amp; partners <strong>announce</strong> news",
                "Fallback &lt;em&gt;description&lt;/em&gt;",
                " ",
                "2026-07-18T18:30:00Z",
                new Provider("Example &amp; Wire"),
                null,
                new ArticleUrl("https://finance.yahoo.com/news/fallback.html"));

        var article = mapper.toNews(response(List.of(
                        new StreamItem("outer-id", null, content))),
                "AAPL", RETRIEVED_AT).articles().getFirst();

        assertThat(article.externalId()).isEqualTo("outer-id");
        assertThat(article.headline()).isEqualTo("Apple & partners announce news");
        assertThat(article.sourceName()).isEqualTo("Example & Wire");
        assertThat(article.description()).isEqualTo("Fallback description");
        assertThat(article.articleUrl())
                .isEqualTo("https://finance.yahoo.com/news/fallback.html");
    }

    @Test
    void filtersAdvertisementsNonStoriesAndMalformedRowsButKeepsValidStories()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StreamItem advertisement = new StreamItem(
                "ad", objectMapper.readTree("{\"slot\":\"ticker-stream\"}"),
                story("ad", "ad", "Ad", "2026-07-19T00:00:00Z").content());
        StreamItem video = new StreamItem(
                "video", null, new Content(
                        "video", "VIDEO", "Video", null, null,
                        "2026-07-19T00:00:00Z", null,
                        new ArticleUrl("https://example.com/video"), null));
        StreamItem invalidTimestamp = story(
                "bad", "bad", "Bad", "not-a-timestamp");

        var result = mapper.toNews(response(List.of(
                        story("valid", "valid", "Valid", "2026-07-18T18:30:00Z"),
                        advertisement,
                        video,
                        invalidTimestamp)),
                "AAPL", RETRIEVED_AT);

        assertThat(result.articles()).singleElement()
                .satisfies(article -> assertThat(article.headline()).isEqualTo("Valid"));
        assertThat(result.skippedArticleCount()).isEqualTo(3);
    }

    @Test
    void acceptsValidEmptyStreamAndRejectsMissingOrNullStream() {
        assertThat(mapper.toNews(response(List.of()), "AAPL", RETRIEVED_AT).articles())
                .isEmpty();

        assertContractFailure(null);
        assertContractFailure(new YahooFinanceNewsResponse(null));
        assertContractFailure(new YahooFinanceNewsResponse(new Data(null)));
        assertContractFailure(new YahooFinanceNewsResponse(new Data(new TickerStream(null))));
    }

    @Test
    void rejectsAllInvalidRecordsIncludingMissingRequiredFieldsAndUnsafeUrls() {
        List<StreamItem> invalid = List.of(
                story("headline", "headline", " ", "2026-07-18T18:30:00Z"),
                withUrl(story("url", "url", "Bad URL", "2026-07-18T18:30:00Z"),
                        "javascript:alert(1)"),
                story("date", "date", "Bad date", "invalid"),
                new StreamItem("missing-content", null, null));

        assertThatThrownBy(() -> mapper.toNews(response(invalid), "AAPL", RETRIEVED_AT))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("valid article");
    }

    private void assertContractFailure(YahooFinanceNewsResponse response) {
        assertThatThrownBy(() -> mapper.toNews(response, "AAPL", RETRIEVED_AT))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("contract");
    }

    private YahooFinanceNewsResponse response(List<StreamItem> stream) {
        return new YahooFinanceNewsResponse(new Data(new TickerStream(stream)));
    }

    private StreamItem story(
            String outerId, String contentId, String title, String publishedAt) {
        return new StreamItem(
                outerId,
                null,
                new Content(
                        contentId,
                        "STORY",
                        title,
                        null,
                        "Summary for " + title,
                        publishedAt,
                        new Provider("Example Wire"),
                        new ArticleUrl("https://publisher.example/" + title),
                        new ArticleUrl("https://finance.yahoo.com/news/" + outerId + ".html")));
    }

    private StreamItem withUrl(StreamItem item, String url) {
        Content content = item.content();
        return new StreamItem(item.id(), item.ad(), new Content(
                content.id(), content.contentType(), content.title(), content.description(),
                content.summary(), content.pubDate(), content.provider(),
                new ArticleUrl(url), content.clickThroughUrl()));
    }
}
