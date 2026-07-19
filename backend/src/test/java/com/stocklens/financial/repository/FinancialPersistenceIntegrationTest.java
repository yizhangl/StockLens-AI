package com.stocklens.financial.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.financial.service.HistoricalPriceService;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.support.IntegrationTestContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Import(IntegrationTestContainers.class)
@SpringBootTest
class FinancialPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-18T20:00:00Z");

    @Autowired private FinancialMetricSnapshotRepository metricRepository;
    @Autowired private HistoricalPriceRepository priceRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private HistoricalPriceService historicalPriceService;

    @AfterEach
    void cleanUp() {
        priceRepository.deleteAll();
        metricRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void persistsNullableMetricSnapshotAndFindsLatest() {
        Company company = companyRepository.saveAndFlush(company());
        metricRepository.saveAndFlush(metric(company, "30.1", NOW.minusSeconds(1)));
        FinancialMetricSnapshot latest = metricRepository.saveAndFlush(metric(company, "31.2", NOW));

        assertThat(metricRepository.findFirstByCompany_IdOrderByRetrievedAtDescIdDesc(company.getId()))
                .get()
                .extracting(FinancialMetricSnapshot::getId)
                .isEqualTo(latest.getId());
        assertThat(latest.getPeTtm()).isEqualByComparingTo("31.2");
        assertThat(latest.getForwardPe()).isNull();
    }

    @Test
    void enforcesUniquePriceAndReturnsBoundedAscendingSeries() {
        Company company = companyRepository.saveAndFlush(company());
        priceRepository.saveAndFlush(price(company, "2026-07-18", "211"));
        priceRepository.saveAndFlush(price(company, "2026-07-17", "204"));

        assertThat(priceRepository
                        .findByCompany_IdAndProviderNameAndTradingDateBetweenOrderByTradingDateAsc(
                                company.getId(), "FMP", LocalDate.parse("2026-07-17"),
                                LocalDate.parse("2026-07-18")))
                .extracting(HistoricalPrice::getTradingDate)
                .containsExactly(LocalDate.parse("2026-07-17"), LocalDate.parse("2026-07-18"));
        assertThatThrownBy(() -> priceRepository.saveAndFlush(price(company, "2026-07-18", "212")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historicalUpsertIsIdempotentAndUpdatesExistingDate() {
        Company company = companyRepository.saveAndFlush(company());
        LocalDate date = LocalDate.parse("2026-07-18");
        historicalPriceService.upsert(company, "FMP", date, date, List.of(data(date, "211")));
        historicalPriceService.upsert(company, "FMP", date, date, List.of(data(date, "212")));

        List<HistoricalPrice> prices = priceRepository.findAll();
        assertThat(prices).hasSize(1);
        assertThat(prices.getFirst().getClosePrice()).isEqualByComparingTo("212");
    }

    private Company company() {
        return new Company(
                "AAPL", "Apple Inc.", "NASDAQ", null, null, null, null, null, null, "AAPL", NOW, NOW);
    }

    private FinancialMetricSnapshot metric(Company company, String pe, Instant retrievedAt) {
        return new FinancialMetricSnapshot(
                company, new BigDecimal(pe), null, null, null, null, null, null, null,
                null, null, null, null, null, "USD", LocalDate.parse("2025-09-27"),
                retrievedAt, "FMP", null);
    }

    private HistoricalPrice price(Company company, String date, String close) {
        return new HistoricalPrice(
                company, LocalDate.parse(date), null, null, null, new BigDecimal(close), null,
                1000L, "USD", "FMP", NOW);
    }

    private HistoricalPriceData data(LocalDate date, String close) {
        return new HistoricalPriceData(
                "AAPL", date, null, null, null, new BigDecimal(close), null,
                1000L, "USD", "FMP", NOW);
    }
}
