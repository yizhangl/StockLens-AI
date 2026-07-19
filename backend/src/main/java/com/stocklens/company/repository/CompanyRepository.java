package com.stocklens.company.repository;

import com.stocklens.company.domain.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByTicker(String ticker);
}
