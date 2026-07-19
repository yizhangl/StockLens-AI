package com.stocklens.market.client;

import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;

public interface FinancialDataClient {

    CompanyProfileData getCompanyProfile(String ticker);

    MarketSnapshotData getMarketSnapshot(String ticker);
}
