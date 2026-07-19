package com.stocklens.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stocklens.company.domain.Company;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketSnapshotServiceTest {

    @Mock
    private MarketSnapshotRepository repository;

    @Test
    void persistsExactNormalizedSnapshotWithoutRawProviderData() {
        when(repository.saveAndFlush(any(MarketSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MarketSnapshotService service = new MarketSnapshotService(repository);
        Company company = new Company(
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                null,
                null,
                null,
                null,
                null,
                null,
                "AAPL",
                Instant.parse("2026-07-18T20:00:00Z"),
                Instant.parse("2026-07-18T20:00:00Z"));
        MarketSnapshotData data = new MarketSnapshotData(
                "AAPL",
                new BigDecimal("268.47000000"),
                new BigDecimal("-1.30000000"),
                new BigDecimal("-0.481890"),
                new BigDecimal("3967020108000.00"),
                "USD",
                Instant.parse("2026-07-18T19:00:00Z"),
                Instant.parse("2026-07-18T20:00:00Z"),
                "FMP");

        MarketSnapshot result = service.create(company, data);

        assertThat(result.getPrice()).isEqualByComparingTo("268.47000000");
        assertThat(result.getMarketCap()).isEqualByComparingTo("3967020108000.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getProviderName()).isEqualTo("FMP");
        assertThat(result.getRawDataJson()).isNull();
    }
}
