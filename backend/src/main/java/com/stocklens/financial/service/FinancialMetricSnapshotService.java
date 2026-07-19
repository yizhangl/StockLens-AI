package com.stocklens.financial.service;

import com.stocklens.company.domain.Company;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.repository.FinancialMetricSnapshotRepository;
import com.stocklens.market.client.model.FinancialMetricsData;
import org.springframework.stereotype.Service;

@Service
public class FinancialMetricSnapshotService {

    private final FinancialMetricSnapshotRepository repository;

    public FinancialMetricSnapshotService(FinancialMetricSnapshotRepository repository) {
        this.repository = repository;
    }

    public FinancialMetricSnapshot create(Company company, FinancialMetricsData data) {
        return repository.saveAndFlush(new FinancialMetricSnapshot(
                company,
                data.peTtm(),
                data.forwardPe(),
                data.pegRatio(),
                data.priceToSales(),
                data.revenueTtm(),
                data.grossMargin(),
                data.netMargin(),
                data.returnOnEquity(),
                data.revenueGrowth(),
                data.earningsGrowth(),
                data.debtToEquity(),
                data.currentRatio(),
                data.beta(),
                data.currency(),
                data.reportedAt(),
                data.retrievedAt(),
                data.providerName(),
                null));
    }
}
