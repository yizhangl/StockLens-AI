package com.stocklens.financial.repository;

import com.stocklens.financial.domain.FinancialMetricSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialMetricSnapshotRepository
        extends JpaRepository<FinancialMetricSnapshot, Long> {

    Optional<FinancialMetricSnapshot> findFirstByCompany_IdOrderByRetrievedAtDescIdDesc(
            Long companyId);
}
