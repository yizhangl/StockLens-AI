package com.stocklens.research.repository;

import com.stocklens.research.domain.ComparisonBriefSource;
import org.springframework.data.jpa.repository.JpaRepository;
import com.stocklens.research.domain.ComparisonBrief;
import java.util.List;

public interface ComparisonBriefSourceRepository
        extends JpaRepository<ComparisonBriefSource, ComparisonBriefSource.Key> {
    List<ComparisonBriefSource> findByComparisonBrief(ComparisonBrief comparisonBrief);
}
