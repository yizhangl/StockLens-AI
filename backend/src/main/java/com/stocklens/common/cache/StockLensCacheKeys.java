package com.stocklens.common.cache;

import org.springframework.stereotype.Component;

@Component
public class StockLensCacheKeys {
    private static final String PREFIX = "stocklens:";
    public String company(String ticker) { return PREFIX + "company:" + ticker; }
    public String market(String ticker) { return PREFIX + "market:" + ticker; }
    public String metrics(String ticker) { return PREFIX + "metrics:" + ticker; }
    public String history(String ticker, String period) { return PREFIX + "history:" + ticker + ':' + period; }
    public String news(String ticker, int limit) { return PREFIX + "news:" + ticker + ':' + limit; }
    public String comparison(String left, String right, String period, String mode) { return PREFIX + "comparison:" + pair(left, right) + ':' + period + ':' + mode; }
    public String brief(String left, String right, String inputHash) { return PREFIX + "brief:" + pair(left, right) + ':' + inputHash; }
    public String comparisonPrefix() { return PREFIX + "comparison:"; }
    public String briefPrefix() { return PREFIX + "brief:"; }
    private String pair(String left, String right) { return left.compareTo(right) <= 0 ? left + ':' + right : right + ':' + left; }
}
