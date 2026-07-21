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
import com.stocklens.common.time.FreshnessPolicy;
import com.stocklens.financial.repository.HistoricalPriceRepository;
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
    private final HistoricalPriceRepository repository;
    private final FreshnessPolicy freshness;

    public HistoricalPriceQueryService(
            TickerNormalizer tickerNormalizer,
            FinancialDataClient financialDataClient,
            CompanyService companyService,
            HistoricalPriceService historicalPriceService,
            HistoricalReturnCalculator returnCalculator,
            Clock clock, JsonRedisCache cache, StockLensCacheKeys cacheKeys, StockLensCacheProperties cacheProperties,
            HistoricalPriceRepository repository, FreshnessPolicy freshness) {
        this.tickerNormalizer = tickerNormalizer;
        this.financialDataClient = financialDataClient;
        this.companyService = companyService;
        this.historicalPriceService = historicalPriceService;
        this.returnCalculator = returnCalculator;
        this.clock = clock;
        this.cache = cache; this.cacheKeys = cacheKeys; this.cacheProperties = cacheProperties;
        this.repository = repository; this.freshness = freshness;
    }

    public HistoricalPriceResponse getHistory(String rawTicker, String rawPeriod) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        PricePeriod period = PricePeriod.parse(rawPeriod);
        var cached = cache.get(cacheKeys.history(ticker, period.code()), HistoricalPriceResponse.class);
        if (cached.isPresent()) return cached.get();
        PriceDateRange range = period.range(clock);
        Company existing = companyService.findByTicker(ticker).orElse(null);
        if (existing != null) {
            List<HistoricalPrice> persisted = repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(existing.getId());
            if (complete(persisted, range) && freshness.isFresh(persisted.stream().map(HistoricalPrice::getRetrievedAt).max(Instant::compareTo).orElse(null), cacheProperties.historyTtl())) {
                HistoricalPriceResponse response = response(ticker, period, range, persisted);
                cache.put(cacheKeys.history(ticker, period.code()), response, cacheProperties.historyTtl()); return response;
            }
        }
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
        HistoricalPriceResponse response = response(ticker, period, range, prices);
        cache.put(cacheKeys.history(ticker, period.code()), response, cacheProperties.historyTtl()); return response;
    }

    private HistoricalPriceResponse response(String ticker, PricePeriod period, PriceDateRange range, List<HistoricalPrice> prices) {
        BigDecimal calculatedReturn = returnCalculator.roundForApi(returnCalculator.calculate(prices));
        Instant retrievedAt = prices.stream()
                .map(HistoricalPrice::getRetrievedAt)
                .max(Instant::compareTo)
                .orElseThrow();
        LocalDate startDate = range.from() == null ? prices.getFirst().getTradingDate() : range.from();
        return new HistoricalPriceResponse(
                ticker,
                period.code(),
                startDate,
                range.to(),
                prices.getFirst().getCurrency(),
                calculatedReturn,
                prices.getFirst().getProviderName(),
                retrievedAt,
                prices.stream().map(this::toPoint).toList());
    }

    private boolean complete(List<HistoricalPrice> prices, PriceDateRange range) {
        var valid = prices.stream().filter(p -> p.getTradingDate() != null && p.getClosePrice() != null && p.getClosePrice().signum() > 0)
                .collect(java.util.stream.Collectors.toMap(HistoricalPrice::getTradingDate, p -> p, (a, b) -> a, java.util.TreeMap::new));
        if (valid.size() < 2) return false;
        if (range.from() == null) return valid.firstKey().isBefore(valid.lastKey());
        return !valid.firstKey().isAfter(range.from()) && !valid.lastKey().isBefore(range.to());
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
