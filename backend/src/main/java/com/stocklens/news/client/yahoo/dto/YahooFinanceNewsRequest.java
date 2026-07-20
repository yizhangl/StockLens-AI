package com.stocklens.news.client.yahoo.dto;

import java.util.List;

public record YahooFinanceNewsRequest(ServiceConfig serviceConfig) {

    public static YahooFinanceNewsRequest forTicker(String ticker, int candidateCount) {
        return new YahooFinanceNewsRequest(
                new ServiceConfig(candidateCount, List.of(ticker)));
    }

    public record ServiceConfig(int snippetCount, List<String> s) {

        public ServiceConfig {
            s = List.copyOf(s);
        }
    }
}
