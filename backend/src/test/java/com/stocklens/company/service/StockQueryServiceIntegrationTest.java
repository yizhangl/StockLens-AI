package com.stocklens.company.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.client.FinancialDataClient;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;
import com.stocklens.market.repository.MarketSnapshotRepository;
import com.stocklens.support.IntegrationTestContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import({IntegrationTestContainers.class, StockQueryServiceIntegrationTest.FakeProviderConfiguration.class})
@SpringBootTest
class StockQueryServiceIntegrationTest {

    @Autowired
    private StockQueryService service;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    @Autowired
    private CountingFinancialDataClient fakeClient;

    @AfterEach
    void cleanUp() {
        snapshotRepository.deleteAll();
        companyRepository.deleteAll();
        fakeClient.reset();
    }

    @Test
    void repeatedRequestsUpdateCompanyAndAppendSnapshots() {
        service.getStock(" aapl ");
        service.getStock("AAPL");

        assertThat(companyRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.count()).isEqualTo(2);
        assertThat(companyRepository.findByTicker("AAPL")).get().satisfies(company -> {
            assertThat(company.getName()).isEqualTo("Apple Inc. 2");
            assertThat(company.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T20:00:01Z"));
            assertThat(company.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-18T20:00:02Z"));
        });
        assertThat(fakeClient.profileCalls()).isEqualTo(2);
        assertThat(fakeClient.quoteCalls()).isEqualTo(2);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {

        @Bean
        @Primary
        CountingFinancialDataClient countingFinancialDataClient() {
            return new CountingFinancialDataClient();
        }
    }

    static final class CountingFinancialDataClient implements FinancialDataClient {

        private final AtomicInteger profileCalls = new AtomicInteger();
        private final AtomicInteger quoteCalls = new AtomicInteger();

        @Override
        public CompanyProfileData getCompanyProfile(String ticker) {
            int call = profileCalls.incrementAndGet();
            return new CompanyProfileData(
                    ticker,
                    "Apple Inc. " + call,
                    "NASDAQ",
                    "Technology",
                    "Consumer Electronics",
                    "US",
                    "https://www.apple.com",
                    "Description",
                    null,
                    ticker,
                    "USD",
                    Instant.parse("2026-07-18T20:00:0" + call + "Z"));
        }

        @Override
        public MarketSnapshotData getMarketSnapshot(String ticker) {
            int call = quoteCalls.incrementAndGet();
            return new MarketSnapshotData(
                    ticker,
                    new BigDecimal("268." + call),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("3967020108000.00"),
                    null,
                    Instant.parse("2026-07-18T19:00:0" + call + "Z"),
                    Instant.parse("2026-07-18T20:00:0" + call + "Z"),
                    "FMP");
        }

        int profileCalls() {
            return profileCalls.get();
        }

        int quoteCalls() {
            return quoteCalls.get();
        }

        void reset() {
            profileCalls.set(0);
            quoteCalls.set(0);
        }
    }
}
