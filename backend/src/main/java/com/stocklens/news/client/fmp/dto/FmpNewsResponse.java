package com.stocklens.news.client.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpNewsResponse(
        String symbol,
        String publishedDate,
        String publisher,
        String title,
        String image,
        String site,
        String text,
        String url) {}
