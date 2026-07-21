package com.stocklens.company.service;

import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.common.time.FreshnessPolicy;
import com.stocklens.common.cache.JsonRedisCache;
import com.stocklens.common.cache.StockLensCacheKeys;
import com.stocklens.common.cache.StockLensCacheProperties;
import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.service.MarketSnapshotService;
import com.stocklens.market.repository.MarketSnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService {

    private final TickerNormalizer tickerNormalizer;
    private final FinancialDataClient financialDataClient;
    private final CompanyService companyService;
    private final MarketSnapshotService marketSnapshotService;
    private final CompanyRepository companyRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final FreshnessPolicy freshness;
    private final JsonRedisCache cache;
    private final StockLensCacheKeys cacheKeys;
    private final StockLensCacheProperties cacheProperties;

    public StockQueryService(
            TickerNormalizer tickerNormalizer,
            FinancialDataClient financialDataClient,
            CompanyService companyService,
            MarketSnapshotService marketSnapshotService, CompanyRepository companyRepository,
            MarketSnapshotRepository marketSnapshotRepository, FreshnessPolicy freshness, JsonRedisCache cache,
            StockLensCacheKeys cacheKeys, StockLensCacheProperties cacheProperties) {
        this.tickerNormalizer = tickerNormalizer;
        this.financialDataClient = financialDataClient;
        this.companyService = companyService;
        this.marketSnapshotService = marketSnapshotService;
        this.companyRepository = companyRepository; this.marketSnapshotRepository = marketSnapshotRepository;
        this.freshness = freshness; this.cache = cache; this.cacheKeys = cacheKeys; this.cacheProperties = cacheProperties;
    }

    public StockResult getStock(String rawTicker) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        CompanyResolution company = resolveCompany(ticker);
        return new StockResult(company.company(), loadMarketSnapshot(company));
    }

    public CompanyResolution resolveCompany(String rawTicker) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        cache.get(cacheKeys.company(ticker), CompanyCacheValue.class);
        Company persisted = companyRepository.findByTicker(ticker).orElse(null);
        if (persisted != null && freshness.isFresh(persisted.getUpdatedAt(), cacheProperties.companyTtl())) {
            String currency = marketSnapshotRepository.findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc(persisted.getId())
                    .map(MarketSnapshot::getCurrency).orElse(null);
            cache.put(cacheKeys.company(ticker), CompanyCacheValue.from(persisted, currency), cacheProperties.companyTtl());
            return new CompanyResolution(persisted, currency);
        }
        CompanyProfileData profile = financialDataClient.getCompanyProfile(ticker);
        Company company = companyService.upsert(profile);
        cache.put(cacheKeys.company(ticker), CompanyCacheValue.from(company, profile.currency()), cacheProperties.companyTtl());
        return new CompanyResolution(company, profile.currency());
    }

    public MarketSnapshot refreshMarketSnapshot(CompanyResolution resolution) {
        String ticker = resolution.company().getTicker();
        MarketSnapshotData snapshotData = financialDataClient
                .getMarketSnapshot(ticker)
                .withCurrency(resolution.currency());
        return marketSnapshotService.create(resolution.company(), snapshotData);
    }

    public MarketSnapshot loadMarketSnapshot(CompanyResolution resolution) {
        String ticker = resolution.company().getTicker();
        cache.get(cacheKeys.market(ticker), com.stocklens.market.dto.MarketSnapshotResponse.class);
        MarketSnapshot persisted = marketSnapshotRepository.findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc(resolution.company().getId()).orElse(null);
        if (persisted != null && freshness.isFresh(persisted.getRetrievedAt(), cacheProperties.marketTtl())) {
            cache.put(cacheKeys.market(ticker), marketResponse(persisted), cacheProperties.marketTtl());
            return persisted;
        }
        MarketSnapshot refreshed = refreshMarketSnapshot(resolution);
        cache.put(cacheKeys.market(ticker), marketResponse(refreshed), cacheProperties.marketTtl());
        return refreshed;
    }

    private com.stocklens.market.dto.MarketSnapshotResponse marketResponse(MarketSnapshot value) {
        return new com.stocklens.market.dto.MarketSnapshotResponse(value.getPrice(), value.getPriceChange(), value.getPriceChangePercent(), value.getMarketCap(), value.getCurrency(), value.getQuoteTimestamp(), value.getRetrievedAt(), value.getProviderName());
    }

    public record StockResult(Company company, MarketSnapshot latestMarketSnapshot) {}

    public record CompanyResolution(Company company, String currency) {}
    private record CompanyCacheValue(String ticker, String name, String currency, java.time.Instant updatedAt) {
        static CompanyCacheValue from(Company company, String currency) { return new CompanyCacheValue(company.getTicker(), company.getName(), currency, company.getUpdatedAt()); }
    }
}
