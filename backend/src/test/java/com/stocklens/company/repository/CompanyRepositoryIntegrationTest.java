package com.stocklens.company.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.company.domain.Company;
import com.stocklens.support.IntegrationTestContainers;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Import(IntegrationTestContainers.class)
@SpringBootTest
class CompanyRepositoryIntegrationTest {

    @Autowired
    private CompanyRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void persistsAndFindsCompanyByUniqueTicker() {
        Company saved = repository.saveAndFlush(company("AAPL", "Apple Inc."));

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findByTicker("AAPL")).get().extracting(Company::getName).isEqualTo("Apple Inc.");

        assertThatThrownBy(() -> repository.saveAndFlush(company("AAPL", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsTickerOutsideApprovedFormat() {
        assertThatThrownBy(() -> repository.saveAndFlush(company("1BAD", "Invalid")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Company company(String ticker, String name) {
        Instant now = Instant.parse("2026-07-18T20:00:00Z");
        return new Company(
                ticker,
                name,
                "NASDAQ",
                null,
                null,
                null,
                null,
                null,
                null,
                ticker,
                now,
                now);
    }
}
