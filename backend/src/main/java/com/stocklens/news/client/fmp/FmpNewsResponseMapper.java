package com.stocklens.news.client.fmp;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.news.client.fmp.dto.FmpNewsResponse;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.client.model.NewsFetchResult;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class FmpNewsResponseMapper {

    static final String PROVIDER_NAME = "FMP";

    private static final Logger log = LoggerFactory.getLogger(FmpNewsResponseMapper.class);
    private static final DateTimeFormatter PUBLISHED_DATE_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    public NewsFetchResult toNews(
            List<FmpNewsResponse> responses, String requestedTicker, Instant retrievedAt) {
        List<FmpNewsResponse> safeResponses = responses == null ? List.of() : responses;
        List<NewsArticleData> articles = new ArrayList<>();
        int skipped = 0;
        for (FmpNewsResponse response : safeResponses) {
            try {
                articles.add(toArticle(response, requestedTicker, retrievedAt));
            } catch (InvalidNewsRecordException exception) {
                skipped++;
                log.warn("Skipping invalid FMP news record reason={}", exception.reason);
            }
        }
        if (!safeResponses.isEmpty() && articles.isEmpty()) {
            throw new DataUnavailableException(
                    "News provider response did not contain a valid article.");
        }
        articles.sort(Comparator
                .comparing(NewsArticleData::publishedAt)
                .reversed()
                .thenComparing(NewsArticleData::headline));
        return new NewsFetchResult(articles, skipped, PROVIDER_NAME, retrievedAt);
    }

    private NewsArticleData toArticle(
            FmpNewsResponse response, String requestedTicker, Instant retrievedAt) {
        if (response == null) {
            throw invalid("missing_record");
        }
        String symbol = requiredText(response.symbol(), 64, "symbol").toUpperCase(Locale.ROOT);
        if (!symbol.equals(requestedTicker)) {
            throw invalid("unexpected_symbol");
        }
        String headline = requiredPlainText(response.title(), 1000, "headline");
        String articleUrl = requiredText(response.url(), 2048, "url");
        Instant publishedAt = publishedAt(response.publishedDate());
        String sourceName = optionalPlainText(response.publisher(), 255);
        if (sourceName == null) {
            sourceName = optionalPlainText(response.site(), 255);
        }

        return new NewsArticleData(
                null,
                headline,
                sourceName,
                articleUrl,
                optionalPlainText(response.text(), Integer.MAX_VALUE),
                publishedAt,
                Set.of(symbol),
                retrievedAt,
                PROVIDER_NAME);
    }

    private Instant publishedAt(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw invalid("publication_timestamp");
        }
        try {
            return LocalDateTime.parse(normalized, PUBLISHED_DATE_FORMAT).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException exception) {
            throw invalid("publication_timestamp");
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
        String unescaped = HtmlUtils.htmlUnescape(normalized);
        String result = unescaped
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

    private String requiredText(String value, int maxLength, String field) {
        String normalized = optionalText(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw invalid(field);
        }
        return normalized;
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private InvalidNewsRecordException invalid(String reason) {
        return new InvalidNewsRecordException(reason);
    }

    private static final class InvalidNewsRecordException extends RuntimeException {
        private final String reason;

        private InvalidNewsRecordException(String reason) {
            this.reason = reason;
        }
    }
}
