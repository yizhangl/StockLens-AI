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
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class HistoricalPriceQueryService {

    static final int BOUNDARY_TOLERANCE_DAYS = 7;

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
            List<HistoricalPrice> persisted = usableSeries(
                    repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(existing.getId()), range);
            Instant seriesRetrievedAt = persisted.stream()
                    .map(HistoricalPrice::getRetrievedAt)
                    .min(Instant::compareTo)
                    .orElse(null);
            if (isComplete(persisted, range)
                    && freshness.isFresh(seriesRetrievedAt, cacheProperties.historyTtl())) {
                HistoricalPriceResponse response = response(ticker, period, range, persisted);
                cache.put(cacheKeys.history(ticker, period.code()), response, cacheProperties.historyTtl()); return response;
            }
        }
        CompanyProfileData profile = existing == null ? financialDataClient.getCompanyProfile(ticker) : null;
        String currency = profile == null
                ? repository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(existing.getId()).stream()
                        .map(HistoricalPrice::getCurrency).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null)
                : profile.currency();
        List<HistoricalPriceData> providerPrices = financialDataClient
                .getHistoricalPrices(ticker, range.from(), range.to())
                .stream()
                .map(point -> point.withCurrency(currency))
                .toList();
        if (providerPrices.isEmpty()) {
            throw new DataUnavailableException("Historical prices are unavailable for " + ticker + ".");
        }
        Company company = existing == null ? companyService.upsert(profile) : existing;
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

    List<HistoricalPrice> usableSeries(List<HistoricalPrice> prices, PriceDateRange range) {
        TreeMap<LocalDate, HistoricalPrice> byDate = new TreeMap<>();
        prices.stream()
                .filter(price -> price != null
                        && price.getTradingDate() != null
                        && price.getClosePrice() != null
                        && price.getClosePrice().signum() > 0)
                .filter(price -> range.from() == null
                        || !price.getTradingDate().isBefore(range.from().minusDays(BOUNDARY_TOLERANCE_DAYS)))
                .filter(price -> !price.getTradingDate().isAfter(range.to().plusDays(BOUNDARY_TOLERANCE_DAYS)))
                .forEach(price -> byDate.putIfAbsent(price.getTradingDate(), price));
        return List.copyOf(byDate.values());
    }

    boolean isComplete(List<HistoricalPrice> prices, PriceDateRange range) {
        if (prices.size() < 2) return false;
        LocalDate first = prices.getFirst().getTradingDate();
        LocalDate last = prices.getLast().getTradingDate();
        if (!first.isBefore(last)) return false;
        if (range.from() == null) return true;
        return !first.isAfter(range.from().plusDays(BOUNDARY_TOLERANCE_DAYS))
                && !last.isBefore(range.to().minusDays(BOUNDARY_TOLERANCE_DAYS));
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
