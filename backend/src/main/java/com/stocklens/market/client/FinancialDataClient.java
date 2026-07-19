package com.stocklens.market.client;

import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.market.client.model.MarketSnapshotData;
import java.time.LocalDate;
import java.util.List;

public interface FinancialDataClient {

    CompanyProfileData getCompanyProfile(String ticker);

    MarketSnapshotData getMarketSnapshot(String ticker);

    FinancialMetricsData getFinancialMetrics(String ticker);

    List<HistoricalPriceData> getHistoricalPrices(String ticker, LocalDate from, LocalDate to);
}
