package com.stocklens.company.dto;

import com.stocklens.market.dto.MarketSnapshotResponse;

public record StockResponse(
        CompanyResponse company, MarketSnapshotResponse latestMarketSnapshot) {}
