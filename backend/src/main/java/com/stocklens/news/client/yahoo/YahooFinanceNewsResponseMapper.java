package com.stocklens.news.client.yahoo;

import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.client.model.NewsFetchResult;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.Content;
import com.stocklens.news.client.yahoo.dto.YahooFinanceNewsResponse.StreamItem;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

@Component
public class YahooFinanceNewsResponseMapper {

    static final String PROVIDER_NAME = "YAHOO_FINANCE";

    private static final Logger log =
            LoggerFactory.getLogger(YahooFinanceNewsResponseMapper.class);

    public NewsFetchResult toNews(
            YahooFinanceNewsResponse response, String requestedTicker, Instant retrievedAt) {
        List<StreamItem> stream = requireStream(response);
        List<NewsArticleData> articles = new ArrayList<>();
        int skipped = 0;
        for (StreamItem item : stream) {
            try {
                articles.add(toArticle(item, retrievedAt));
            } catch (InvalidYahooNewsRecordException exception) {
                skipped++;
                log.warn(
                        "Skipping invalid Yahoo Finance news record reason={}",
                        exception.reason);
            }
        }
        if (!stream.isEmpty() && articles.isEmpty()) {
            throw new NewsProviderException(
                    "Yahoo Finance news response did not contain a valid article.");
        }
        articles.sort(Comparator
                .comparing(NewsArticleData::publishedAt)
                .reversed()
                .thenComparing(NewsArticleData::headline));
        return new NewsFetchResult(articles, skipped, PROVIDER_NAME, retrievedAt);
    }

    private List<StreamItem> requireStream(YahooFinanceNewsResponse response) {
        if (response == null
                || response.data() == null
                || response.data().tickerStream() == null
                || response.data().tickerStream().stream() == null) {
            throw new NewsProviderException("Yahoo Finance news response contract is invalid.");
        }
        return response.data().tickerStream().stream();
    }

    private NewsArticleData toArticle(StreamItem item, Instant retrievedAt) {
        if (item == null) {
            throw invalid("missing_record");
        }
        if (isTruthy(item.ad())) {
            throw invalid("advertisement");
        }
        Content content = item.content();
        if (content == null) {
            throw invalid("missing_content");
        }
        if (!"STORY".equalsIgnoreCase(optionalText(content.contentType()))) {
            throw invalid("unsupported_content_type");
        }

        String externalId = optionalText(firstNonblank(content.id(), item.id()));
        if (externalId != null && externalId.length() > 255) {
            throw invalid("external_id");
        }
        String headline = requiredPlainText(content.title(), 1000, "headline");
        String sourceName = content.provider() == null
                ? null
                : optionalPlainText(content.provider().displayName(), 255);
        String articleUrl = requiredUrl(firstNonblank(
                url(content.canonicalUrl()), url(content.clickThroughUrl())));
        String description = optionalPlainText(
                firstNonblank(content.summary(), content.description()), Integer.MAX_VALUE);
        Instant publishedAt = publishedAt(content.pubDate());

        return new NewsArticleData(
                externalId,
                headline,
                sourceName,
                articleUrl,
                description,
                publishedAt,
                Set.of(),
                retrievedAt,
                PROVIDER_NAME);
    }

    private boolean isTruthy(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isArray() || value.isObject()) {
            return value.size() != 0;
        }
        if (value.isString()) {
            return !value.stringValue().isBlank();
        }
        if (value.isNumber()) {
            return value.decimalValue().signum() != 0;
        }
        return true;
    }

    private Instant publishedAt(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw invalid("publication_timestamp");
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeException exception) {
            throw invalid("publication_timestamp");
        }
    }

    private String requiredUrl(String value) {
        String normalized = optionalText(value);
        if (normalized == null || normalized.length() > 2048) {
            throw invalid("url");
        }
        try {
            URI uri = URI.create(normalized);
            if (uri.isOpaque()
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw invalid("url");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            if (exception instanceof InvalidYahooNewsRecordException invalid) {
                throw invalid;
            }
            throw invalid("url");
        }
    }

    private String requiredPlainText(String value, int maxLength, String field) {
        String normalized = optionalPlainText(value, maxLength);
        if (normalized == null) {
            throw invalid(field);
        }
        return normalized;
    }

    private String optionalPlainText(String value, int maxLength) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return null;
        }
        String result = HtmlUtils.htmlUnescape(normalized)
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (result.isEmpty()) {
            return null;
        }
        if (result.length() > maxLength) {
            throw invalid("text_length");
        }
        return result;
    }

    private String url(YahooFinanceNewsResponse.ArticleUrl value) {
        return value == null ? null : value.url();
    }

    private String firstNonblank(String first, String second) {
        return optionalText(first) == null ? second : first;
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private InvalidYahooNewsRecordException invalid(String reason) {
        return new InvalidYahooNewsRecordException(reason);
    }

    private static final class InvalidYahooNewsRecordException extends IllegalArgumentException {
        private final String reason;

        private InvalidYahooNewsRecordException(String reason) {
            this.reason = reason;
        }
    }
}
