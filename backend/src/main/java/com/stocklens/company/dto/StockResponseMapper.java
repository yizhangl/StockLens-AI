package com.stocklens.company.dto;

import com.stocklens.company.domain.Company;
import com.stocklens.company.service.StockQueryService.StockResult;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.dto.MarketSnapshotResponse;
import org.springframework.stereotype.Component;

@Component
public class StockResponseMapper {

    public StockResponse toResponse(StockResult result) {
        Company company = result.company();
        MarketSnapshot snapshot = result.latestMarketSnapshot();
        return new StockResponse(
                new CompanyResponse(
                        company.getTicker(),
                        company.getName(),
                        company.getExchange(),
                        company.getSector(),
                        company.getIndustry(),
                        company.getCountry(),
                        company.getWebsiteUrl(),
                        company.getDescription(),
                        company.getLogoUrl(),
                        company.getUpdatedAt()),
                new MarketSnapshotResponse(
                        snapshot.getPrice(),
                        snapshot.getPriceChange(),
                        snapshot.getPriceChangePercent(),
                        snapshot.getMarketCap(),
                        snapshot.getCurrency(),
                        snapshot.getQuoteTimestamp(),
                        snapshot.getRetrievedAt(),
                        snapshot.getProviderName()));
    }
}
