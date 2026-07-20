package com.stocklens.news.client.yahoo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooFinanceNewsResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(TickerStream tickerStream) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TickerStream(List<StreamItem> stream) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamItem(String id, JsonNode ad, Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String id,
            String contentType,
            String title,
            String description,
            String summary,
            String pubDate,
            Provider provider,
            ArticleUrl canonicalUrl,
            ArticleUrl clickThroughUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provider(String displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArticleUrl(String url) {}
}
