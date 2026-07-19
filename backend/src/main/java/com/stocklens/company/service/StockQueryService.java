package com.stocklens.company.service;

import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.service.MarketSnapshotService;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService {

    private final TickerNormalizer tickerNormalizer;
    private final FinancialDataClient financialDataClient;
    private final CompanyService companyService;
    private final MarketSnapshotService marketSnapshotService;

    public StockQueryService(
            TickerNormalizer tickerNormalizer,
            FinancialDataClient financialDataClient,
            CompanyService companyService,
            MarketSnapshotService marketSnapshotService) {
        this.tickerNormalizer = tickerNormalizer;
        this.financialDataClient = financialDataClient;
        this.companyService = companyService;
        this.marketSnapshotService = marketSnapshotService;
    }

    public StockResult getStock(String rawTicker) {
        String ticker = tickerNormalizer.normalize(rawTicker);
        CompanyProfileData profile = financialDataClient.getCompanyProfile(ticker);
        MarketSnapshotData snapshotData = financialDataClient
                .getMarketSnapshot(ticker)
                .withCurrency(profile.currency());
        Company company = companyService.upsert(profile);
        MarketSnapshot snapshot = marketSnapshotService.create(company, snapshotData);
        return new StockResult(company, snapshot);
    }

    public record StockResult(Company company, MarketSnapshot latestMarketSnapshot) {}
}
