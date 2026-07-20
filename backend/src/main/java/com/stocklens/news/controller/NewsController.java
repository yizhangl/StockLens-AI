package com.stocklens.news.controller;

import com.stocklens.news.dto.NewsResponse;
import com.stocklens.news.service.NewsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
public class NewsController {

    private final NewsQueryService newsQueryService;

    public NewsController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @GetMapping("/{ticker}/news")
    NewsResponse getNews(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "10") int limit) {
        return newsQueryService.getRecentNews(ticker, limit);
    }
}
