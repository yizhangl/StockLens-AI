package com.stocklens.market.repository;

import com.stocklens.market.domain.MarketSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, Long> {

    Optional<MarketSnapshot> findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc(
            Long companyId);
}
