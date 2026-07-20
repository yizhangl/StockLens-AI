package com.stocklens.news.client;

import com.stocklens.news.client.model.NewsFetchResult;

public interface NewsDataClient {

    NewsFetchResult getRecentNews(String ticker, int limit);
}
