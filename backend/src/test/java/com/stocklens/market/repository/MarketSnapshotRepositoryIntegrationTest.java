package com.stocklens.market.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.support.IntegrationTestContainers;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(IntegrationTestContainers.class)
@SpringBootTest
class MarketSnapshotRepositoryIntegrationTest {

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        snapshotRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void persistsExactDecimalsAndNullableOptionalValues() {
        Company company = companyRepository.saveAndFlush(company());
        MarketSnapshot saved = snapshotRepository.saveAndFlush(snapshot(
                company,
                "268.47000000",
                null,
                Instant.parse("2026-07-18T19:00:00Z"),
                Instant.parse("2026-07-18T20:00:00Z")));

        assertThat(saved.getPrice()).isEqualByComparingTo("268.47000000");
        assertThat(saved.getCurrency()).isNull();
        assertThat(saved.getMarketCap()).isNull();
        assertThat(saved.getRawDataJson()).isNull();
    }

    @Test
    void deterministicallyFindsLatestSnapshot() {
        Company company = companyRepository.saveAndFlush(company());
        Instant quote = Instant.parse("2026-07-18T19:00:00Z");
        snapshotRepository.saveAndFlush(snapshot(
                company, "100", "USD", quote.minusSeconds(60), quote.plusSeconds(100)));
        snapshotRepository.saveAndFlush(snapshot(
                company, "101", "USD", quote, quote.plusSeconds(100)));
        snapshotRepository.saveAndFlush(snapshot(
                company, "102", "USD", quote, quote.plusSeconds(200)));
        MarketSnapshot expected = snapshotRepository.saveAndFlush(snapshot(
                company, "103", "USD", quote, quote.plusSeconds(200)));

        assertThat(snapshotRepository
                        .findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc(company.getId()))
                .get()
                .extracting(MarketSnapshot::getId)
                .isEqualTo(expected.getId());
    }

    @Test
    void databaseEnforcesPriceMarketCapAndCurrencyChecks() {
        Company company = companyRepository.saveAndFlush(company());
        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(snapshot(
                        company,
                        "0",
                        "USD",
                        Instant.parse("2026-07-18T19:00:00Z"),
                        Instant.parse("2026-07-18T20:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(new MarketSnapshot(
                        company,
                        BigDecimal.ONE,
                        null,
                        null,
                        new BigDecimal("-1"),
                        "USD",
                        Instant.parse("2026-07-18T19:00:00Z"),
                        Instant.parse("2026-07-18T20:00:00Z"),
                        "FMP",
                        null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(snapshot(
                        company,
                        "1",
                        "US",
                        Instant.parse("2026-07-18T19:00:00Z"),
                        Instant.parse("2026-07-18T20:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesCompanyForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO market_snapshot (
                            company_id, price, quote_timestamp, retrieved_at, provider_name
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        Long.MAX_VALUE,
                        BigDecimal.ONE,
                        Timestamp.from(Instant.parse("2026-07-18T19:00:00Z")),
                        Timestamp.from(Instant.parse("2026-07-18T20:00:00Z")),
                        "FMP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Company company() {
        Instant now = Instant.parse("2026-07-18T20:00:00Z");
        return new Company(
                "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null, "AAPL", now, now);
    }

    private MarketSnapshot snapshot(
            Company company, String price, String currency, Instant quoteTimestamp, Instant retrievedAt) {
        return new MarketSnapshot(
                company,
                new BigDecimal(price),
                null,
                null,
                null,
                currency,
                quoteTimestamp,
                retrievedAt,
                "FMP",
                null);
    }
}
