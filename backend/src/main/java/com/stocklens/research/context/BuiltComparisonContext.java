package com.stocklens.research.context;

import com.stocklens.company.domain.Company;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BuiltComparisonContext(
        Company leftCompany,
        Company rightCompany,
        List<GroundedSource> sources,
        Map<String, GroundedSource> sourcesById,
        Instant dataCutoffAt,
        String canonicalInput) {}
