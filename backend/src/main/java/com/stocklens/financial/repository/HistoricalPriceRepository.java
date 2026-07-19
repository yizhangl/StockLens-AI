package com.stocklens.financial.repository;

import com.stocklens.financial.domain.HistoricalPrice;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricalPriceRepository extends JpaRepository<HistoricalPrice, Long> {

    List<HistoricalPrice> findByCompany_IdAndProviderNameAndTradingDateBetweenOrderByTradingDateAsc(
            Long companyId, String providerName, LocalDate from, LocalDate to);

    List<HistoricalPrice> findByCompany_IdAndProviderNameOrderByTradingDateAsc(
            Long companyId, String providerName);
}
