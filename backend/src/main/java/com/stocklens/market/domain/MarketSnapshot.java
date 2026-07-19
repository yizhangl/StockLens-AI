package com.stocklens.market.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "market_snapshot")
public class MarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal price;

    @Column(name = "price_change", precision = 30, scale = 8)
    private BigDecimal priceChange;

    @Column(name = "price_change_percent", precision = 19, scale = 6)
    private BigDecimal priceChangePercent;

    @Column(name = "market_cap", precision = 30, scale = 2)
    private BigDecimal marketCap;

    @Column(length = 3)
    private String currency;

    @Column(name = "quote_timestamp", nullable = false)
    private Instant quoteTimestamp;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data_json", columnDefinition = "jsonb")
    private String rawDataJson;

    protected MarketSnapshot() {}

    public MarketSnapshot(
            Company company,
            BigDecimal price,
            BigDecimal priceChange,
            BigDecimal priceChangePercent,
            BigDecimal marketCap,
            String currency,
            Instant quoteTimestamp,
            Instant retrievedAt,
            String providerName,
            String rawDataJson) {
        this.company = company;
        this.price = price;
        this.priceChange = priceChange;
        this.priceChangePercent = priceChangePercent;
        this.marketCap = marketCap;
        this.currency = currency;
        this.quoteTimestamp = quoteTimestamp;
        this.retrievedAt = retrievedAt;
        this.providerName = providerName;
        this.rawDataJson = rawDataJson;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getPriceChange() {
        return priceChange;
    }

    public BigDecimal getPriceChangePercent() {
        return priceChangePercent;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getRawDataJson() {
        return rawDataJson;
    }
}
