package com.stocklens.market.service;

import com.stocklens.company.domain.Company;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.repository.MarketSnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class MarketSnapshotService {

    private final MarketSnapshotRepository repository;

    public MarketSnapshotService(MarketSnapshotRepository repository) {
        this.repository = repository;
    }

    public MarketSnapshot create(Company company, MarketSnapshotData data) {
        return repository.saveAndFlush(new MarketSnapshot(
                company,
                data.price(),
                data.priceChange(),
                data.priceChangePercent(),
                data.marketCap(),
                data.currency(),
                data.quoteTimestamp(),
                data.retrievedAt(),
                data.providerName(),
                null));
    }
}
