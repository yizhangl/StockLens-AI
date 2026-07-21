package com.stocklens.research.repository;

import com.stocklens.research.domain.ComparisonBriefSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonBriefSourceRepository
        extends JpaRepository<ComparisonBriefSource, ComparisonBriefSource.Key> {}
