package com.stocklens.financial.service;

import com.stocklens.company.domain.Company;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.financial.repository.HistoricalPriceRepository;
import com.stocklens.market.client.model.HistoricalPriceData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoricalPriceService {

    private final HistoricalPriceRepository repository;

    public HistoricalPriceService(HistoricalPriceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<HistoricalPrice> upsert(
            Company company,
            String providerName,
            LocalDate from,
            LocalDate to,
            List<HistoricalPriceData> data) {
        List<HistoricalPrice> existing = from == null
                ? repository.findByCompany_IdAndProviderNameOrderByTradingDateAsc(
                        company.getId(), providerName)
                : repository.findByCompany_IdAndProviderNameAndTradingDateBetweenOrderByTradingDateAsc(
                        company.getId(), providerName, from, to);
        Map<LocalDate, HistoricalPrice> byDate = new HashMap<>();
        existing.forEach(price -> byDate.put(price.getTradingDate(), price));

        List<HistoricalPrice> changed = new ArrayList<>();
        for (HistoricalPriceData point : data) {
            HistoricalPrice price = byDate.get(point.tradingDate());
            if (price == null) {
                price = new HistoricalPrice(
                        company,
                        point.tradingDate(),
                        point.openPrice(),
                        point.highPrice(),
                        point.lowPrice(),
                        point.closePrice(),
                        point.adjustedClose(),
                        point.volume(),
                        point.currency(),
                        point.providerName(),
                        point.retrievedAt());
                byDate.put(point.tradingDate(), price);
            } else {
                price.update(
                        point.openPrice(),
                        point.highPrice(),
                        point.lowPrice(),
                        point.closePrice(),
                        point.adjustedClose(),
                        point.volume(),
                        point.currency(),
                        point.retrievedAt());
            }
            changed.add(price);
        }
        repository.saveAllAndFlush(changed);
        return from == null
                ? repository.findByCompany_IdAndProviderNameOrderByTradingDateAsc(
                        company.getId(), providerName)
                : repository.findByCompany_IdAndProviderNameAndTradingDateBetweenOrderByTradingDateAsc(
                        company.getId(), providerName, from, to);
    }
}
