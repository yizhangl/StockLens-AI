package com.stocklens.research.repository;

import com.stocklens.research.domain.ComparisonBrief;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonBriefRepository extends JpaRepository<ComparisonBrief, Long> {
    Optional<ComparisonBrief> findFirstByLeftCompany_IdAndRightCompany_IdAndInputHashAndPromptVersionAndModelNameOrderByGeneratedAtDescIdDesc(
            Long leftCompanyId, Long rightCompanyId, String inputHash, String promptVersion, String modelName);
}
