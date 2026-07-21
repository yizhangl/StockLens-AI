package com.stocklens.financial.service;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.company.service.CompanyService;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.financial.dto.HistoricalPricePointResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.period.PriceDateRange;
import com.stocklens.financial.period.PricePeriod;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HistoricalPriceQueryService {

    private final TickerNormalizer tickerNormalizer;
    private final FinancialDataClient financialDataClient;
    private final CompanyService companyService;
    private final HistoricalPriceService historicalPriceService;
    private final HistoricalReturnCalculator returnCalculator;
    private final Clock clock;
    private final JsonRedisCache cache;
    private final StockLensCacheKeys cacheKeys;
    private final StockLensCacheProperties cacheProperties;

    public HistoricalPriceQueryService(
            TickerNormalizer tickerNormalizer,
            FinancialDataClient financialDataClient,
            CompanyService companyService,
            HistoricalPriceService historicalPriceService,
            HistoricalReturnCalculator returnCalculator,
            Clock clock, JsonRedisCache cache, StockLensCacheKeys cacheKeys, StockLensCacheProperties cacheProperties) {
        this.tickerNormalizer = tickerNormalizer;
        this.financialDataClient = financialDataClient;
        this.companyService = companyService;
        this.historicalPriceService = historicalPriceService;
        this.returnCalculator = returnCalculator;
        this.clock = clock;
        this.cache = cache; this.cacheKeys = cacheKeys; this.cacheProperties = cacheProperties;
    }

    public HistoricalPriceResponse getHistory(String rawTicker, String rawPeriod) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        PricePeriod period = PricePeriod.parse(rawPeriod);
        var cached = cache.get(cacheKeys.history(ticker, period.code()), HistoricalPriceResponse.class);
        if (cached.isPresent()) return cached.get();
        PriceDateRange range = period.range(clock);
        CompanyProfileData profile = financialDataClient.getCompanyProfile(ticker);
        List<HistoricalPriceData> providerPrices = financialDataClient
                .getHistoricalPrices(ticker, range.from(), range.to())
                .stream()
                .map(point -> point.withCurrency(profile.currency()))
                .toList();
        if (providerPrices.isEmpty()) {
            throw new DataUnavailableException("Historical prices are unavailable for " + ticker + ".");
        }
        Company company = companyService.upsert(profile);
        String providerName = providerPrices.getFirst().providerName();
        List<HistoricalPrice> prices = historicalPriceService.upsert(
                company, providerName, range.from(), range.to(), providerPrices);
        if (prices.isEmpty()) {
            throw new DataUnavailableException("Historical prices are unavailable for " + ticker + ".");
        }
        BigDecimal calculatedReturn = returnCalculator.roundForApi(returnCalculator.calculate(prices));
        Instant retrievedAt = prices.stream()
                .map(HistoricalPrice::getRetrievedAt)
                .max(Instant::compareTo)
                .orElseThrow();
        LocalDate startDate = range.from() == null ? prices.getFirst().getTradingDate() : range.from();
        HistoricalPriceResponse response = new HistoricalPriceResponse(
                ticker,
                period.code(),
                startDate,
                range.to(),
                prices.getFirst().getCurrency(),
                calculatedReturn,
                providerName,
                retrievedAt,
                prices.stream().map(this::toPoint).toList());
        cache.put(cacheKeys.history(ticker, period.code()), response, cacheProperties.historyTtl());
        return response;
    }

    private HistoricalPricePointResponse toPoint(HistoricalPrice price) {
        return new HistoricalPricePointResponse(
                price.getTradingDate(),
                price.getOpenPrice(),
                price.getHighPrice(),
                price.getLowPrice(),
                price.getClosePrice(),
                price.getAdjustedClose(),
                price.getVolume());
    }
}
