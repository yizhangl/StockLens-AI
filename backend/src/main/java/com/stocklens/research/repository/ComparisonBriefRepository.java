package com.stocklens.research.repository;

import com.stocklens.research.domain.ComparisonBrief;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComparisonBriefRepository extends JpaRepository<ComparisonBrief, Long> {

    @Query(value = """
            SELECT cb.id
            FROM comparison_brief cb
            WHERE cb.left_company_id = :leftCompanyId
              AND cb.right_company_id = :rightCompanyId
              AND cb.input_hash = :inputHash
              AND cb.prompt_version = :promptVersion
              AND cb.model_name = :modelName
            ORDER BY cb.generated_at DESC, cb.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findNewestMatchingId(
            @Param("leftCompanyId") Long leftCompanyId,
            @Param("rightCompanyId") Long rightCompanyId,
            @Param("inputHash") String inputHash,
            @Param("promptVersion") String promptVersion,
            @Param("modelName") String modelName);
}
