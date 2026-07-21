package com.stocklens.news.domain;

import com.stocklens.company.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "news_retrieval")
public class NewsRetrieval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    protected NewsRetrieval() {}

    public NewsRetrieval(Company company, Instant retrievedAt, int resultCount, String providerName) {
        this.company = company;
        this.retrievedAt = retrievedAt;
        this.resultCount = resultCount;
        this.providerName = providerName;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public int getResultCount() { return resultCount; }
    public String getProviderName() { return providerName; }
}
