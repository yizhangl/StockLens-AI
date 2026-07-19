package com.stocklens.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.client.model.CompanyProfileData;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository repository;

    @Test
    void insertsACompanyFromNormalizedProfileData() {
        when(repository.findByTicker("AAPL")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyService service = new CompanyService(repository);

        Company result = service.upsert(profile("Apple Inc.", Instant.parse("2026-07-18T20:00:00Z")));

        assertThat(result.getTicker()).isEqualTo("AAPL");
        assertThat(result.getName()).isEqualTo("Apple Inc.");
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T20:00:00Z"));
        verify(repository).saveAndFlush(any(Company.class));
    }

    @Test
    void updatesProviderFieldsWithoutReplacingCreationTimestamp() {
        Instant createdAt = Instant.parse("2026-07-17T20:00:00Z");
        Company existing = new Company(
                "AAPL", "Old Name", null, null, null, null, null, null, null, "AAPL", createdAt, createdAt);
        when(repository.findByTicker("AAPL")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyService service = new CompanyService(repository);

        Company result = service.upsert(profile("Apple Inc.", Instant.parse("2026-07-18T20:00:00Z")));

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(result.getName()).isEqualTo("Apple Inc.");
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-18T20:00:00Z"));
    }

    private CompanyProfileData profile(String name, Instant retrievedAt) {
        return new CompanyProfileData(
                "AAPL",
                name,
                "NASDAQ",
                "Technology",
                "Consumer Electronics",
                "US",
                "https://www.apple.com",
                "Description",
                null,
                "AAPL",
                "USD",
                retrievedAt);
    }
}
